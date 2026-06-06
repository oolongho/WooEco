package com.oolonghoo.wooeco.manager;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.config.UUIDMode;
import com.oolonghoo.wooeco.database.dao.PlayerDAO;
import com.oolonghoo.wooeco.model.PlayerAccount;
import com.oolonghoo.wooeco.util.AsyncUtils;
import com.oolonghoo.wooeco.util.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家数据管理器
 * 管理在线玩家的内存缓存
 * 支持缓存禁用模式（直接读写数据库）
 * 
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
        if (disableCache) {
            return getAccountDirectFromDB(uuid);
        }
        
        PlayerAccount account = onlineCache.get(uuid);
        if (account != null) {
            plugin.getDebugManager().cacheHit(uuid);
            return account;
        }
        
        // 缓存未命中：loadPlayer 已在 PlayerJoinEvent 中预先放入占位账户，
        // 此处未命中说明玩家不在线，返回 null
        plugin.getDebugManager().cacheMiss(uuid);
        return null;
    }
    
    public PlayerAccount getOrCreateAccount(UUID uuid, String playerName) {
        PlayerAccount account = getAccount(uuid);
        if (account == null) {
            account = createNewAccount(uuid, playerName);
        }
        return account;
    }
    
    private PlayerAccount getAccountDirectFromDB(UUID uuid) {
        PlayerAccount account = AsyncUtils.supplyAsyncWithTimeout(() -> {
            try {
                return playerDAO.getAccount(uuid);
            } catch (SQLException e) {
                plugin.getLogger().severe(String.format("获取玩家账户失败：%s", e.getMessage()));
                return null;
            }
        }, null);
        
        if (account == null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
                String name = offlinePlayer.getName();
                if (name == null) {
                    name = uuid.toString().substring(0, 8);
                }
                account = createNewAccount(uuid, name);
            } else if (plugin.getDatabaseConfig().getUuidMode() == UUIDMode.SEMIONLINE) {
                UUID offlineUuid = plugin.getDatabaseManager().getUUIDMappingDAO().getOfflineUUID(uuid);
                if (offlineUuid != null) {
                    OfflinePlayer mappedPlayer = Bukkit.getOfflinePlayer(offlineUuid);
                    if (mappedPlayer.hasPlayedBefore() || mappedPlayer.isOnline()) {
                        String name = mappedPlayer.getName();
                        if (name == null) {
                            name = uuid.toString().substring(0, 8);
                        }
                        account = createNewAccount(uuid, name);
                    }
                }
            }
            if (account == null) {
                return null;
            }
        }
        
        return account;
    }
    
    public PlayerAccount getAccount(String playerName) {
        if (disableCache) {
            return getAccountByNameDirectFromDB(playerName);
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
        
        // 尝试通过在线玩家查找
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        if (onlinePlayer != null) {
            return getAccount(onlinePlayer.getUniqueId());
        }
        
        // 缓存未命中且玩家不在线，返回 null
        plugin.getDebugManager().cacheMiss(null);
        return null;
    }
    
    private PlayerAccount getAccountByNameDirectFromDB(String playerName) {
        PlayerAccount account = AsyncUtils.supplyAsyncWithTimeout(() -> {
            try {
                return playerDAO.getAccountByName(playerName);
            } catch (SQLException e) {
                plugin.getLogger().severe(String.format("获取玩家账户失败：%s", e.getMessage()));
                return null;
            }
        }, null);
        
        if (account != null) {
            return account;
        }
        
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
            UUID uuid = plugin.getUuidHandler().getUUID(playerName);
            return getAccount(uuid);
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
        account.setBalance(plugin.getCurrencyConfig().formatInput(plugin.getCurrencyConfig().getStartingBalance()));
        account.setLastIncomeReset(getTodayStart());
        
        try {
            playerDAO.saveOrUpdateAccount(account);
            plugin.getLogger().info(String.format("为新玩家创建账户：%s", playerName));
            return account;
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("创建玩家账户失败：%s", e.getMessage()));
            return null;
        }
    }
    
    public void loadPlayer(UUID uuid) {
        if (disableCache) {
            return;
        }

        // 先放入占位账户（余额为0），避免后续操作因缓存未命中而阻塞主线程
        Player player = Bukkit.getPlayer(uuid);
        String name = player != null ? player.getName() : uuid.toString().substring(0, 8);
        PlayerAccount placeholder = new PlayerAccount(uuid, name);
        onlineCache.put(uuid, placeholder);
        updateNameIndex(name, uuid);

        // 异步加载真实数据并替换占位账户
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                PlayerAccount account = playerDAO.getAccount(uuid);
                if (account == null) {
                    account = createNewAccount(uuid, name);
                    if (account == null) {
                        plugin.getLogger().severe(String.format("无法为玩家 %s 创建账户", name));
                        return;
                    }
                }
                checkAndResetDailyIncome(account);
                onlineCache.put(uuid, account);
                updateNameIndex(account.getPlayerName(), uuid);
            } catch (SQLException e) {
                plugin.getLogger().severe(String.format("加载玩家数据失败：%s", e.getMessage()));
            }
        });
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
        try {
            playerDAO.saveOrUpdateAccount(account);
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("保存玩家数据失败：%s", e.getMessage()));
        }
    }

    public void saveAccountSync(PlayerAccount account) {
        saveAccount(account);
    }
    
    public void saveAll() {
        if (disableCache) {
            return;
        }

        List<PlayerAccount> dirtyAccounts = new ArrayList<>();
        for (PlayerAccount account : onlineCache.values()) {
            if (account.isDirty()) {
                dirtyAccounts.add(account);
            }
        }

        if (dirtyAccounts.isEmpty()) return;

        try {
            playerDAO.saveAllBatch(dirtyAccounts);
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("批量保存玩家数据失败：%s", e.getMessage()));
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
    
    public void resetAllDailyIncome() {
        long todayStart = getTodayStart();
        
        for (PlayerAccount account : onlineCache.values()) {
            account.setDailyIncome(0);
            account.setLastIncomeReset(todayStart);
        }
        
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                playerDAO.resetAllDailyIncome();
            } catch (SQLException e) {
                plugin.getLogger().severe(String.format("重置所有玩家每日收入失败：%s", e.getMessage()));
            }
        });
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
        try {
            return playerDAO.getAllAccounts();
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("获取所有账户失败：%s", e.getMessage()));
            return onlineCache.values();
        }
    }
    
    public int getAccountCount() {
        try {
            return playerDAO.countAccounts();
        } catch (SQLException e) {
            return onlineCache.size();
        }
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
    
    public PlayerDAO getPlayerDAO() {
        return playerDAO;
    }
    
    public void invalidateAllCache() {
        invalidateAllCache(true);
    }
    
    public void invalidateAllCache(boolean saveDirty) {
        if (disableCache) {
            return;
        }

        if (saveDirty) {
            saveAll();
        }

        // 异步刷新所有在线玩家数据，而非清空缓存，避免缓存雪崩
        SchedulerUtils.runAsync(plugin, () -> {
            for (UUID uuid : new ArrayList<>(onlineCache.keySet())) {
                try {
                    PlayerAccount freshAccount = playerDAO.getAccount(uuid);
                    if (freshAccount != null) {
                        onlineCache.put(uuid, freshAccount);
                        updateNameIndex(freshAccount.getPlayerName(), uuid);
                    }
                } catch (SQLException e) {
                    // 忽略单个玩家刷新失败
                }
            }
        });
    }
}
