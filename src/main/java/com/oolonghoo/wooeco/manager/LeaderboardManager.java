package com.oolonghoo.wooeco.manager;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.database.dao.PlayerDAO;
import com.oolonghoo.wooeco.model.PlayerAccount;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 排行榜管理器 (线程安全)
 * 支持黑名单过滤
 * 
 * @author oolongho
 */
public class LeaderboardManager {
    
    private final WooEco plugin;
    private final PlayerDAO playerDAO;
    
    private volatile List<PlayerAccount> balanceTopCache;
    private volatile List<PlayerAccount> incomeTopCache;
    private final int cacheSize;
    private final Object cacheLock = new Object();
    
    private volatile Map<UUID, Integer> balanceRankCache;
    private volatile Map<UUID, Integer> incomeRankCache;
    
    private final Set<String> blacklistNames;
    private final Set<UUID> blacklistUUIDs;
    private boolean blacklistEnabled;
    
    public LeaderboardManager(WooEco plugin) {
        this.plugin = plugin;
        this.playerDAO = plugin.getDatabaseManager().getPlayerDAO();
        this.cacheSize = plugin.getConfig().getInt("leaderboard.per-page", 10) * 10;
        this.balanceTopCache = Collections.emptyList();
        this.incomeTopCache = Collections.emptyList();
        this.balanceRankCache = Collections.emptyMap();
        this.incomeRankCache = Collections.emptyMap();
        this.blacklistNames = new HashSet<>();
        this.blacklistUUIDs = new HashSet<>();
        loadBlacklist();
    }
    
    private void loadBlacklist() {
        blacklistEnabled = plugin.getConfig().getBoolean("leaderboard.blacklist.enabled", false);
        blacklistNames.clear();
        blacklistUUIDs.clear();
        
        if (blacklistEnabled) {
            List<String> names = plugin.getConfig().getStringList("leaderboard.blacklist.players");
            for (String name : names) {
                blacklistNames.add(name.toLowerCase());
            }
            
            List<String> uuids = plugin.getConfig().getStringList("leaderboard.blacklist.uuids");
            for (String uuidStr : uuids) {
                try {
                    blacklistUUIDs.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("无效的UUID格式: " + uuidStr);
                }
            }
            
            plugin.getLogger().info("排行榜黑名单已加载: " + blacklistNames.size() + " 个玩家名, " + blacklistUUIDs.size() + " 个UUID");
        }
    }
    
    public void reloadBlacklist() {
        loadBlacklist();
        refreshCache();
    }
    
    private boolean isBlacklisted(PlayerAccount account) {
        if (!blacklistEnabled) {
            return false;
        }
        
        if (blacklistNames.contains(account.getPlayerName().toLowerCase())) {
            return true;
        }
        
        return blacklistUUIDs.contains(account.getUuid());
    }
    
    public void refreshCache() {
        try {
            List<PlayerAccount> rawBalanceTop = playerDAO.getTopBalances(cacheSize * 2);
            List<PlayerAccount> rawIncomeTop = playerDAO.getTopIncomes(cacheSize * 2);
            
            List<PlayerAccount> filteredBalanceTop = new ArrayList<>();
            List<PlayerAccount> filteredIncomeTop = new ArrayList<>();
            
            for (PlayerAccount account : rawBalanceTop) {
                if (!isBlacklisted(account)) {
                    filteredBalanceTop.add(account);
                    if (filteredBalanceTop.size() >= cacheSize) {
                        break;
                    }
                }
            }
            
            for (PlayerAccount account : rawIncomeTop) {
                if (!isBlacklisted(account)) {
                    filteredIncomeTop.add(account);
                    if (filteredIncomeTop.size() >= cacheSize) {
                        break;
                    }
                }
            }
            
            Map<UUID, Integer> newBalanceRankCache = new ConcurrentHashMap<>();
            Map<UUID, Integer> newIncomeRankCache = new ConcurrentHashMap<>();
            
            for (int i = 0; i < filteredBalanceTop.size(); i++) {
                newBalanceRankCache.put(filteredBalanceTop.get(i).getUuid(), i + 1);
            }
            
            for (int i = 0; i < filteredIncomeTop.size(); i++) {
                newIncomeRankCache.put(filteredIncomeTop.get(i).getUuid(), i + 1);
            }
            
            synchronized (cacheLock) {
                this.balanceTopCache = Collections.unmodifiableList(filteredBalanceTop);
                this.incomeTopCache = Collections.unmodifiableList(filteredIncomeTop);
                this.balanceRankCache = Collections.unmodifiableMap(newBalanceRankCache);
                this.incomeRankCache = Collections.unmodifiableMap(newIncomeRankCache);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("刷新排行榜缓存失败: " + e.getMessage());
        }
    }
    
    public List<PlayerAccount> getBalanceTop(int page, int perPage) {
        List<PlayerAccount> cache;
        synchronized (cacheLock) {
            cache = balanceTopCache;
        }
        
        if (cache.isEmpty()) {
            refreshCache();
            synchronized (cacheLock) {
                cache = balanceTopCache;
            }
        }
        
        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, cache.size());
        
        if (start >= cache.size()) {
            return Collections.emptyList();
        }
        
        return new ArrayList<>(cache.subList(start, end));
    }
    
    public List<PlayerAccount> getIncomeTop(int page, int perPage) {
        List<PlayerAccount> cache;
        synchronized (cacheLock) {
            cache = incomeTopCache;
        }
        
        if (cache.isEmpty()) {
            refreshCache();
            synchronized (cacheLock) {
                cache = incomeTopCache;
            }
        }
        
        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, cache.size());
        
        if (start >= cache.size()) {
            return Collections.emptyList();
        }
        
        return new ArrayList<>(cache.subList(start, end));
    }
    
    public int getTotalBalancePages(int perPage) {
        List<PlayerAccount> cache;
        synchronized (cacheLock) {
            cache = balanceTopCache;
        }
        return (int) Math.ceil((double) cache.size() / perPage);
    }
    
    public int getTotalIncomePages(int perPage) {
        List<PlayerAccount> cache;
        synchronized (cacheLock) {
            cache = incomeTopCache;
        }
        return (int) Math.ceil((double) cache.size() / perPage);
    }
    
    public int getPerPage() {
        return plugin.getConfig().getInt("leaderboard.per-page", 10);
    }
    
    public boolean isBlacklistEnabled() {
        return blacklistEnabled;
    }
    
    public int getBlacklistCount() {
        return blacklistNames.size() + blacklistUUIDs.size();
    }
    
    public int getBalanceRank(UUID uuid) {
        Map<UUID, Integer> cache = balanceRankCache;
        if (cache.isEmpty()) {
            refreshCache();
            cache = balanceRankCache;
        }
        return cache.getOrDefault(uuid, -1);
    }
    
    public int getIncomeRank(UUID uuid) {
        Map<UUID, Integer> cache = incomeRankCache;
        if (cache.isEmpty()) {
            refreshCache();
            cache = incomeRankCache;
        }
        return cache.getOrDefault(uuid, -1);
    }
}
