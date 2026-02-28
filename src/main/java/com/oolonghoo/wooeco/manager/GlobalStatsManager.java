package com.oolonghoo.wooeco.manager;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.database.dao.PlayerDAO;
import com.oolonghoo.wooeco.util.ThreadUtils;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 全局统计管理器
 * 缓存全服总余额和账户数量
 * 
 * @author oolongho
 */
public class GlobalStatsManager {
    
    private final WooEco plugin;
    private final PlayerDAO playerDAO;
    
    private final AtomicReference<BigDecimal> totalBalance;
    private final AtomicReference<BigDecimal> totalIncome;
    private final AtomicInteger accountCount;
    private final AtomicInteger onlineCount;
    
    private volatile long lastRefreshTime = 0;
    private final long refreshInterval;
    
    public GlobalStatsManager(WooEco plugin) {
        this.plugin = plugin;
        this.playerDAO = plugin.getDatabaseManager().getPlayerDAO();
        this.totalBalance = new AtomicReference<>(BigDecimal.ZERO);
        this.totalIncome = new AtomicReference<>(BigDecimal.ZERO);
        this.accountCount = new AtomicInteger(0);
        this.onlineCount = new AtomicInteger(0);
        this.refreshInterval = plugin.getConfig().getLong("leaderboard.cache-refresh", 60) * 1000;
    }
    
    public void refresh() {
        ThreadUtils.runSmart(() -> {
            try {
                BigDecimal newTotalBalance = playerDAO.getTotalBalance();
                BigDecimal newTotalIncome = playerDAO.getTotalDailyIncome();
                int newAccountCount = playerDAO.countAccounts();
                
                totalBalance.set(newTotalBalance != null ? newTotalBalance : BigDecimal.ZERO);
                totalIncome.set(newTotalIncome != null ? newTotalIncome : BigDecimal.ZERO);
                accountCount.set(newAccountCount);
                onlineCount.set(plugin.getServer().getOnlinePlayers().size());
                lastRefreshTime = System.currentTimeMillis();
                
                if (plugin.getDebugManager() != null && plugin.getDebugManager().isEnabled()) {
                    plugin.getDebugManager().log("CACHE", "INFO", 
                        "统计刷新完成 - 总余额: " + totalBalance.get() + ", 账户数: " + accountCount.get());
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("刷新全局统计失败: " + e.getMessage());
            }
        });
    }
    
    public void refreshIfNeeded() {
        if (System.currentTimeMillis() - lastRefreshTime > refreshInterval) {
            refresh();
        }
    }
    
    public BigDecimal getTotalBalance() {
        refreshIfNeeded();
        return totalBalance.get();
    }
    
    public double getTotalBalanceDouble() {
        return getTotalBalance().doubleValue();
    }
    
    public BigDecimal getTotalIncome() {
        refreshIfNeeded();
        return totalIncome.get();
    }
    
    public double getTotalIncomeDouble() {
        return getTotalIncome().doubleValue();
    }
    
    public int getAccountCount() {
        refreshIfNeeded();
        return accountCount.get();
    }
    
    public int getOnlineCount() {
        onlineCount.set(plugin.getServer().getOnlinePlayers().size());
        return onlineCount.get();
    }
    
    public void addToTotalBalance(BigDecimal amount) {
        totalBalance.updateAndGet(current -> current.add(amount));
    }
    
    public void subtractFromTotalBalance(BigDecimal amount) {
        totalBalance.updateAndGet(current -> current.subtract(amount));
    }
    
    public void incrementAccountCount() {
        accountCount.incrementAndGet();
    }
    
    public void decrementAccountCount() {
        accountCount.decrementAndGet();
    }
    
    public long getLastRefreshTime() {
        return lastRefreshTime;
    }
    
    public StatsSnapshot getSnapshot() {
        return new StatsSnapshot(
            totalBalance.get(),
            totalIncome.get(),
            accountCount.get(),
            onlineCount.get(),
            lastRefreshTime
        );
    }
    
    public static class StatsSnapshot {
        public final BigDecimal totalBalance;
        public final BigDecimal totalIncome;
        public final int accountCount;
        public final int onlineCount;
        public final long snapshotTime;
        
        public StatsSnapshot(BigDecimal totalBalance, BigDecimal totalIncome, 
                            int accountCount, int onlineCount, long snapshotTime) {
            this.totalBalance = totalBalance;
            this.totalIncome = totalIncome;
            this.accountCount = accountCount;
            this.onlineCount = onlineCount;
            this.snapshotTime = snapshotTime;
        }
    }
}
