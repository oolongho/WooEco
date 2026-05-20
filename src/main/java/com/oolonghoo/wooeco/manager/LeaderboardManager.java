package com.oolonghoo.wooeco.manager;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.database.dao.PlayerDAO;
import com.oolonghoo.wooeco.model.IncomePeriod;
import com.oolonghoo.wooeco.model.PlayerAccount;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
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
 */
public class LeaderboardManager {
    
    private final WooEco plugin;
    private final PlayerDAO playerDAO;
    
    private volatile List<PlayerAccount> balanceTopCache;
    private volatile List<PlayerAccount> incomeTopCache;
    private volatile List<PlayerAccount> weeklyIncomeTopCache;
    private volatile List<PlayerAccount> monthlyIncomeTopCache;
    private final int cacheSize;
    private final Object cacheLock = new Object();
    
    private volatile Map<UUID, Integer> balanceRankCache;
    private volatile Map<UUID, Integer> incomeRankCache;
    private volatile Map<UUID, Integer> weeklyIncomeRankCache;
    private volatile Map<UUID, Integer> monthlyIncomeRankCache;
    
    private final Set<String> blacklistNames;
    private final Set<UUID> blacklistUUIDs;
    private boolean blacklistEnabled;
    
    public LeaderboardManager(WooEco plugin) {
        this.plugin = plugin;
        this.playerDAO = plugin.getDatabaseManager().getPlayerDAO();
        this.cacheSize = plugin.getConfig().getInt("leaderboard.per-page", 10) * 10;
        this.balanceTopCache = Collections.emptyList();
        this.incomeTopCache = Collections.emptyList();
        this.weeklyIncomeTopCache = Collections.emptyList();
        this.monthlyIncomeTopCache = Collections.emptyList();
        this.balanceRankCache = Collections.emptyMap();
        this.incomeRankCache = Collections.emptyMap();
        this.weeklyIncomeRankCache = Collections.emptyMap();
        this.monthlyIncomeRankCache = Collections.emptyMap();
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
            
            long weekStart = getStartOfWeekTimestamp();
            long monthStart = getStartOfMonthTimestamp();
            List<PlayerAccount> rawWeeklyIncomeTop = plugin.getDatabaseManager().getLogDAO().getTopIncomesByPeriod(weekStart, cacheSize * 2);
            List<PlayerAccount> rawMonthlyIncomeTop = plugin.getDatabaseManager().getLogDAO().getTopIncomesByPeriod(monthStart, cacheSize * 2);
            
            List<PlayerAccount> filteredBalanceTop = new ArrayList<>();
            List<PlayerAccount> filteredIncomeTop = new ArrayList<>();
            List<PlayerAccount> filteredWeeklyIncomeTop = new ArrayList<>();
            List<PlayerAccount> filteredMonthlyIncomeTop = new ArrayList<>();
            
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
            
            for (PlayerAccount account : rawWeeklyIncomeTop) {
                if (!isBlacklisted(account)) {
                    filteredWeeklyIncomeTop.add(account);
                    if (filteredWeeklyIncomeTop.size() >= cacheSize) {
                        break;
                    }
                }
            }
            
            for (PlayerAccount account : rawMonthlyIncomeTop) {
                if (!isBlacklisted(account)) {
                    filteredMonthlyIncomeTop.add(account);
                    if (filteredMonthlyIncomeTop.size() >= cacheSize) {
                        break;
                    }
                }
            }
            
            Map<UUID, Integer> newBalanceRankCache = new ConcurrentHashMap<>();
            Map<UUID, Integer> newIncomeRankCache = new ConcurrentHashMap<>();
            Map<UUID, Integer> newWeeklyIncomeRankCache = new ConcurrentHashMap<>();
            Map<UUID, Integer> newMonthlyIncomeRankCache = new ConcurrentHashMap<>();
            
            for (int i = 0; i < filteredBalanceTop.size(); i++) {
                newBalanceRankCache.put(filteredBalanceTop.get(i).getUuid(), i + 1);
            }
            
            for (int i = 0; i < filteredIncomeTop.size(); i++) {
                newIncomeRankCache.put(filteredIncomeTop.get(i).getUuid(), i + 1);
            }
            
            for (int i = 0; i < filteredWeeklyIncomeTop.size(); i++) {
                newWeeklyIncomeRankCache.put(filteredWeeklyIncomeTop.get(i).getUuid(), i + 1);
            }
            
            for (int i = 0; i < filteredMonthlyIncomeTop.size(); i++) {
                newMonthlyIncomeRankCache.put(filteredMonthlyIncomeTop.get(i).getUuid(), i + 1);
            }
            
            synchronized (cacheLock) {
                this.balanceTopCache = Collections.unmodifiableList(filteredBalanceTop);
                this.incomeTopCache = Collections.unmodifiableList(filteredIncomeTop);
                this.weeklyIncomeTopCache = Collections.unmodifiableList(filteredWeeklyIncomeTop);
                this.monthlyIncomeTopCache = Collections.unmodifiableList(filteredMonthlyIncomeTop);
                this.balanceRankCache = Collections.unmodifiableMap(newBalanceRankCache);
                this.incomeRankCache = Collections.unmodifiableMap(newIncomeRankCache);
                this.weeklyIncomeRankCache = Collections.unmodifiableMap(newWeeklyIncomeRankCache);
                this.monthlyIncomeRankCache = Collections.unmodifiableMap(newMonthlyIncomeRankCache);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("刷新排行榜缓存失败: " + e.getMessage());
        }
    }

    public void refreshCacheAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::refreshCache);
    }

    public List<PlayerAccount> getBalanceTop(int page, int perPage) {
        List<PlayerAccount> cache = balanceTopCache;
        
        if (cache.isEmpty()) {
            refreshCacheAsync();
            return Collections.emptyList();
        }

        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, cache.size());

        if (start >= cache.size()) {
            return Collections.emptyList();
        }

        return new ArrayList<>(cache.subList(start, end));
    }

    public List<PlayerAccount> getIncomeTop(int page, int perPage) {
        return getIncomeTopByPeriod(IncomePeriod.DAY, page, perPage);
    }
    
    public List<PlayerAccount> getIncomeTopByPeriod(IncomePeriod period, int page, int perPage) {
        List<PlayerAccount> cache = switch (period) {
            case WEEK -> weeklyIncomeTopCache;
            case MONTH -> monthlyIncomeTopCache;
            default -> incomeTopCache;
        };
        
        if (cache.isEmpty()) {
            refreshCacheAsync();
            return Collections.emptyList();
        }

        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, cache.size());

        if (start >= cache.size()) {
            return Collections.emptyList();
        }

        return new ArrayList<>(cache.subList(start, end));
    }

    public int getTotalBalancePages(int perPage) {
        return (int) Math.ceil((double) balanceTopCache.size() / perPage);
    }
    
    public int getTotalIncomePages(int perPage) {
        return getTotalIncomePagesByPeriod(IncomePeriod.DAY, perPage);
    }
    
    public int getTotalIncomePagesByPeriod(IncomePeriod period, int perPage) {
        List<PlayerAccount> cache = switch (period) {
            case WEEK -> weeklyIncomeTopCache;
            case MONTH -> monthlyIncomeTopCache;
            default -> incomeTopCache;
        };
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
            refreshCacheAsync();
            return -1;
        }
        return cache.getOrDefault(uuid, -1);
    }

    /**
     * 按玩家名查找余额排名（仅从内存缓存，零DB调用）
     */
    public int getBalanceRankByName(String playerName) {
        List<PlayerAccount> cache = balanceTopCache;
        if (cache.isEmpty()) {
            return -1;
        }
        for (PlayerAccount account : cache) {
            if (account.getPlayerName().equalsIgnoreCase(playerName)) {
                return balanceRankCache.getOrDefault(account.getUuid(), -1);
            }
        }
        return -1;
    }

    /**
     * 直接从缓存获取指定排名的余额排行玩家名（O(1)，零DB调用）
     */
    public String getTopBalancePlayer(int rank) {
        List<PlayerAccount> cache = balanceTopCache;
        if (rank < 1 || rank > cache.size()) return null;
        return cache.get(rank - 1).getPlayerName();
    }

    /**
     * 直接从缓存获取指定排名的余额（O(1)，零DB调用）
     */
    public double getTopBalanceAt(int rank) {
        List<PlayerAccount> cache = balanceTopCache;
        if (rank < 1 || rank > cache.size()) return -1;
        return cache.get(rank - 1).getBalanceDouble();
    }

    /**
     * 直接从缓存获取指定排名的收入排行玩家名（O(1)，零DB调用）
     */
    public String getTopIncomePlayerByPeriod(IncomePeriod period, int rank) {
        List<PlayerAccount> cache = getIncomeCache(period);
        if (rank < 1 || rank > cache.size()) return null;
        return cache.get(rank - 1).getPlayerName();
    }

    /**
     * 直接从缓存获取指定排名的收入（O(1)，零DB调用）
     * 注意：dailyIncome 字段在周/月排行上下文中存储的是对应周期的收入汇总值
     */
    public double getTopIncomeAt(IncomePeriod period, int rank) {
        List<PlayerAccount> cache = getIncomeCache(period);
        if (rank < 1 || rank > cache.size()) return -1;
        return cache.get(rank - 1).getDailyIncomeDouble();
    }

    private List<PlayerAccount> getIncomeCache(IncomePeriod period) {
        return switch (period) {
            case WEEK -> weeklyIncomeTopCache;
            case MONTH -> monthlyIncomeTopCache;
            default -> incomeTopCache;
        };
    }

    public int getIncomeRank(UUID uuid) {
        return getIncomeRankByPeriod(IncomePeriod.DAY, uuid);
    }
    
    public int getIncomeRankByPeriod(IncomePeriod period, UUID uuid) {
        Map<UUID, Integer> cache = switch (period) {
            case WEEK -> weeklyIncomeRankCache;
            case MONTH -> monthlyIncomeRankCache;
            default -> incomeRankCache;
        };
        if (cache.isEmpty()) {
            refreshCacheAsync();
            return -1;
        }
        return cache.getOrDefault(uuid, -1);
    }
    
    private long getStartOfWeekTimestamp() {
        return LocalDate.now(ZoneId.systemDefault())
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }
    
    private long getStartOfMonthTimestamp() {
        return LocalDate.now(ZoneId.systemDefault())
                .with(TemporalAdjusters.firstDayOfMonth())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }
}
