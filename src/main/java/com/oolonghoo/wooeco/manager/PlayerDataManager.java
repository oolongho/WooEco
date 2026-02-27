package com.oolonghoo.wooeco.manager;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.database.dao.PlayerDAO;
import com.oolonghoo.wooeco.model.PlayerAccount;
import com.oolonghoo.wooeco.util.AsyncUtils;
import com.oolonghoo.wooeco.util.ThreadUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家数据管理器
 * 管理在线玩家的内存缓存
 * 支持缓存禁用模式（直接读写数据库）
 * 
 * @author oolongho
 */
public class PlayerDataManager {
    
    private final WooEco plugin;
    private final Map<UUID, PlayerAccount> onlineCache;
    private final Map<String, UUID> nameIndex;
    private final PlayerDAO playerDAO;
    private final boolean usernameIgnoreCase;
    private final boolean disableCache;
    
    public PlayerDataManager(WooEco plugin) {
        this.plugin = plugin;
        this.onlineCache = new ConcurrentHashMap<>();
        this.nameIndex = new ConcurrentHashMap<>();
        this.playerDAO = plugin.getDatabaseManager().getPlayerDAO();
        this.usernameIgnoreCase = plugin.getConfig().getBoolean("settings.username-ignore-case", false);
        this.disableCache = plugin.getConfig().getBoolean("performance.disable-cache", false);
        
        if (disableCache) {
            plugin.getLogger().warning("缓存已禁用！所有操作将直接读写数据库，性能可能下降。");
        }
    }
    
    public PlayerAccount getAccount(UUID uuid) {
        long startTime = System.nanoTime();
        
        if (disableCache) {
            return getAccountDirectFromDB(uuid, startTime);
        }
        
        PlayerAccount account = onlineCache.get(uuid);
        if (account != null) {
            plugin.getDebugManager().cacheHit(uuid);
            return account;
        }
        
        plugin.getDebugManager().cacheMiss(uuid);
        
        account = AsyncUtils.supplyAsyncWithTimeout(() -> {
            try {
                return playerDAO.getAccount(uuid);
            } catch (SQLException e) {
                plugin.getLogger().severe("获取玩家账户失败: " + e.getMessage());
                return null;
            }
        }, null);
        
        if (account == null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            String name = offlinePlayer.getName();
            if (name == null) {
                name = uuid.toString().substring(0, 8);
            }
            account = createNewAccount(uuid, name);
        }
        
        onlineCache.put(uuid, account);
        updateNameIndex(account.getPlayerName(), uuid);
        
        long elapsed = System.nanoTime() - startTime;
        plugin.getDebugManager().playerLookup("UUID", uuid.toString(), uuid, elapsed);
        
        return account;
    }
    
    private PlayerAccount getAccountDirectFromDB(UUID uuid, long startTime) {
        PlayerAccount account = AsyncUtils.supplyAsyncWithTimeout(() -> {
            try {
                return playerDAO.getAccount(uuid);
            } catch (SQLException e) {
                plugin.getLogger().severe("获取玩家账户失败: " + e.getMessage());
                return null;
            }
        }, null);
        
        if (account == null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            String name = offlinePlayer.getName();
            if (name == null) {
                name = uuid.toString().substring(0, 8);
            }
            account = createNewAccount(uuid, name);
        }
        
        long elapsed = System.nanoTime() - startTime;
        plugin.getDebugManager().playerLookup("UUID", uuid.toString(), uuid, elapsed);
        
        return account;
    }
    
    public PlayerAccount getAccount(String playerName) {
        long startTime = System.nanoTime();
        
        if (disableCache) {
            return getAccountByNameDirectFromDB(playerName, startTime);
        }
        
        String lookupKey = usernameIgnoreCase ? playerName.toLowerCase() : playerName;
        
        UUID cachedUuid = nameIndex.get(lookupKey);
        if (cachedUuid != null) {
            PlayerAccount account = onlineCache.get(cachedUuid);
            if (account != null) {
                plugin.getDebugManager().cacheHit(account.getUuid());
                return account;
            }
        }
        
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        if (onlinePlayer != null) {
            return getAccount(onlinePlayer.getUniqueId());
        }
        
        plugin.getDebugManager().cacheMiss(null);
        
        PlayerAccount account = AsyncUtils.supplyAsyncWithTimeout(() -> {
            try {
                return playerDAO.getAccountByName(playerName);
            } catch (SQLException e) {
                plugin.getLogger().severe("获取玩家账户失败: " + e.getMessage());
                return null;
            }
        }, null);
        
        if (account != null) {
            onlineCache.put(account.getUuid(), account);
            updateNameIndex(account.getPlayerName(), account.getUuid());
            
            long elapsed = System.nanoTime() - startTime;
            plugin.getDebugManager().playerLookup("NAME", playerName, account.getUuid(), elapsed);
            
            return account;
        }
        
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
            return getAccount(offlinePlayer.getUniqueId());
        }
        
        return null;
    }
    
    private PlayerAccount getAccountByNameDirectFromDB(String playerName, long startTime) {
        PlayerAccount account = AsyncUtils.supplyAsyncWithTimeout(() -> {
            try {
                return playerDAO.getAccountByName(playerName);
            } catch (SQLException e) {
                plugin.getLogger().severe("获取玩家账户失败: " + e.getMessage());
                return null;
            }
        }, null);
        
        if (account != null) {
            long elapsed = System.nanoTime() - startTime;
            plugin.getDebugManager().playerLookup("NAME", playerName, account.getUuid(), elapsed);
            return account;
        }
        
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
            return getAccount(offlinePlayer.getUniqueId());
        }
        
        return null;
    }
    
    private void updateNameIndex(String playerName, UUID uuid) {
        if (disableCache || playerName == null || uuid == null) {
            return;
        }
        String key = usernameIgnoreCase ? playerName.toLowerCase() : playerName;
        nameIndex.put(key, uuid);
    }
    
    private void removeFromNameIndex(String playerName) {
        if (disableCache || playerName == null) {
            return;
        }
        String key = usernameIgnoreCase ? playerName.toLowerCase() : playerName;
        nameIndex.remove(key);
    }
    
    public PlayerAccount getOnlineAccount(UUID uuid) {
        if (disableCache) {
            return getAccount(uuid);
        }
        return onlineCache.get(uuid);
    }
    
    public PlayerAccount createNewAccount(UUID uuid, String playerName) {
        PlayerAccount account = new PlayerAccount(uuid, playerName);
        account.setBalance(plugin.getCurrencyConfig().getStartingBalance());
        account.setLastIncomeReset(getTodayStart());
        
        try {
            playerDAO.createAccount(account);
            plugin.getLogger().info("为新玩家创建账户: " + playerName);
        } catch (SQLException e) {
            plugin.getLogger().severe("创建玩家账户失败: " + e.getMessage());
        }
        
        return account;
    }
    
    public void loadPlayer(UUID uuid) {
        if (disableCache) {
            return;
        }
        
        PlayerAccount account = AsyncUtils.supplyAsyncWithTimeout(() -> {
            try {
                return playerDAO.getAccount(uuid);
            } catch (SQLException e) {
                plugin.getLogger().severe("加载玩家数据失败: " + e.getMessage());
                return null;
            }
        }, null);
        
        if (account == null) {
            Player player = Bukkit.getPlayer(uuid);
            String name = player != null ? player.getName() : uuid.toString().substring(0, 8);
            account = createNewAccount(uuid, name);
        }
        
        checkAndResetDailyIncome(account);
        onlineCache.put(uuid, account);
        updateNameIndex(account.getPlayerName(), uuid);
    }
    
    public void unloadPlayer(UUID uuid) {
        if (disableCache) {
            return;
        }
        
        PlayerAccount account = onlineCache.remove(uuid);
        if (account != null) {
            removeFromNameIndex(account.getPlayerName());
            if (account.isDirty()) {
                saveAccount(account);
            }
        }
    }
    
    public void saveAccount(PlayerAccount account) {
        ThreadUtils.runSmart(() -> {
            try {
                playerDAO.saveOrUpdateAccount(account);
            } catch (SQLException e) {
                plugin.getLogger().severe("保存玩家数据失败: " + e.getMessage());
            }
        });
    }
    
    public void saveAccountSync(PlayerAccount account) {
        try {
            playerDAO.saveOrUpdateAccount(account);
        } catch (SQLException e) {
            plugin.getLogger().severe("保存玩家数据失败: " + e.getMessage());
        }
    }
    
    public void saveAll() {
        if (disableCache) {
            return;
        }
        
        for (PlayerAccount account : onlineCache.values()) {
            if (account.isDirty()) {
                try {
                    playerDAO.saveOrUpdateAccount(account);
                } catch (SQLException e) {
                    plugin.getLogger().severe("保存玩家数据失败: " + e.getMessage());
                }
            }
        }
    }
    
    public void checkDailyReset() {
        if (disableCache) {
            return;
        }
        
        long todayStart = getTodayStart();
        for (PlayerAccount account : onlineCache.values()) {
            checkAndResetDailyIncome(account, todayStart);
        }
    }
    
    public void checkAndResetDailyIncome(PlayerAccount account) {
        checkAndResetDailyIncome(account, getTodayStart());
    }
    
    private void checkAndResetDailyIncome(PlayerAccount account, long todayStart) {
        if (account.getLastIncomeReset() < todayStart) {
            account.setDailyIncome(0);
            account.setLastIncomeReset(todayStart);
        }
    }
    
    private long getTodayStart() {
        return LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
    }
    
    public Collection<PlayerAccount> getOnlineAccounts() {
        if (disableCache) {
            return getAllAccounts();
        }
        return onlineCache.values();
    }
    
    public Collection<PlayerAccount> getAllAccounts() {
        return AsyncUtils.supplyAsyncWithTimeout(() -> {
            try {
                return playerDAO.getAllAccounts();
            } catch (SQLException e) {
                plugin.getLogger().severe("获取所有账户失败: " + e.getMessage());
                return onlineCache.values();
            }
        }, onlineCache.values());
    }
    
    public int getAccountCount() {
        return AsyncUtils.supplyAsyncWithTimeout(() -> {
            try {
                return playerDAO.countAccounts();
            } catch (SQLException e) {
                return onlineCache.size();
            }
        }, onlineCache.size());
    }
    
    public boolean isOnline(UUID uuid) {
        if (disableCache) {
            return Bukkit.getPlayer(uuid) != null;
        }
        return onlineCache.containsKey(uuid);
    }
    
    public void updatePlayerName(UUID uuid, String name) {
        if (disableCache) {
            return;
        }
        
        PlayerAccount account = onlineCache.get(uuid);
        if (account != null && !account.getPlayerName().equals(name)) {
            removeFromNameIndex(account.getPlayerName());
            account.setPlayerName(name);
            updateNameIndex(name, uuid);
        }
    }
    
    public void clearCache() {
        if (disableCache) {
            return;
        }
        
        saveAll();
        onlineCache.clear();
        nameIndex.clear();
    }
    
    public Map<String, UUID> getNameIndex() {
        if (disableCache) {
            return Map.of();
        }
        return Map.copyOf(nameIndex);
    }
    
    public boolean isCacheDisabled() {
        return disableCache;
    }
}
