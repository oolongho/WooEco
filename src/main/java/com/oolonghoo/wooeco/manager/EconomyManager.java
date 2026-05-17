package com.oolonghoo.wooeco.manager;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.api.events.BalanceChangeEvent;
import com.oolonghoo.wooeco.api.events.BalanceChangeReason;
import com.oolonghoo.wooeco.model.PlayerAccount;
import com.oolonghoo.wooeco.util.AsyncUtils;

/**
 * 经济管理器
 * 处理所有余额相关操作
 * 
 */
public class EconomyManager {
    
    private final WooEco plugin;
    private final PlayerDataManager playerDataManager;
    private final LogManager logManager;
    
    public EconomyManager(WooEco plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
        this.logManager = plugin.getLogManager();
    }
    
    public double getBalance(UUID uuid) {
        PlayerAccount account = playerDataManager.getAccount(uuid);
        return account != null ? account.getBalanceDouble() : 0;
    }
    
    public BigDecimal getBalanceDecimal(UUID uuid) {
        PlayerAccount account = playerDataManager.getAccount(uuid);
        return account != null ? account.getBalance() : BigDecimal.ZERO;
    }
    
    public boolean has(UUID uuid, double amount) {
        return has(uuid, BigDecimal.valueOf(amount));
    }
    
    public boolean has(UUID uuid, BigDecimal amount) {
        return getBalanceDecimal(uuid).compareTo(amount) >= 0;
    }
    
    public boolean hasAccount(UUID uuid) {
        return playerDataManager.getAccount(uuid) != null;
    }
    
    public EconomyResult deposit(UUID uuid, double amount) {
        return deposit(uuid, BigDecimal.valueOf(amount), BalanceChangeReason.ADMIN, null, null);
    }
    
    public EconomyResult deposit(UUID uuid, BigDecimal amount) {
        return deposit(uuid, amount, BalanceChangeReason.ADMIN, null, null);
    }
    
    public EconomyResult deposit(UUID uuid, BigDecimal amount, BalanceChangeReason reason, 
                                  String operator, String operatorName) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new EconomyResult(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "金额必须大于0");
        }
        
        PlayerAccount account = playerDataManager.getAccount(uuid);
        if (account == null) {
            plugin.getDebugManager().economyError("DEPOSIT", uuid, "账户不存在");
            return new EconomyResult(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "账户不存在");
        }
        
        BigDecimal maxBalance = plugin.getCurrencyConfig().getMaxBalanceBigDecimal();
        BigDecimal oldBalance;
        BigDecimal newBalance;
        
        synchronized (account) {
            oldBalance = account.getBalance();
            newBalance = oldBalance.add(amount);
            if (newBalance.compareTo(maxBalance) > 0) {
                return new EconomyResult(false, BigDecimal.ZERO, oldBalance, BigDecimal.ZERO, "余额已达上限");
            }
            account.setBalance(newBalance);
        }
        
        BalanceChangeEvent event = new BalanceChangeEvent(uuid, oldBalance, newBalance, amount, reason);
        AsyncUtils.callEventOnMain(event);
        plugin.getDebugManager().event("BalanceChangeEvent", "UUID: " + uuid + " | Amount: " + amount);
        
        if (event.isCancelled()) {
            synchronized (account) {
                account.setBalance(oldBalance);
            }
            return new EconomyResult(false, BigDecimal.ZERO, oldBalance, BigDecimal.ZERO, "操作被取消");
        }
        
        BigDecimal eventBalance = plugin.getCurrencyConfig().formatInput(event.getNewBalanceDecimal());
        eventBalance = eventBalance.max(BigDecimal.ZERO).min(maxBalance);
        synchronized (account) {
            account.setBalance(eventBalance);
        }
        newBalance = eventBalance;
        
        if (reason == BalanceChangeReason.PAYMENT_RECEIVED) {
            BigDecimal actualChange = newBalance.subtract(oldBalance);
            if (actualChange.compareTo(BigDecimal.ZERO) > 0) {
                account.addDailyIncome(actualChange);
            }
        }
        playerDataManager.saveAccount(account);
        
        plugin.getDebugManager().economy("DEPOSIT", uuid, account.getPlayerName(), amount, oldBalance, newBalance);
        
        logManager.logBalanceChange(uuid, account.getPlayerName(), "DEPOSIT", 
                                    amount, oldBalance, 
                                    newBalance, operator, operatorName, reason != null ? reason.name() : null);
        
        publishSync(uuid, account.getPlayerName(), newBalance);
        
        BigDecimal actualChange = newBalance.subtract(oldBalance);
        return new EconomyResult(true, actualChange, newBalance, BigDecimal.ZERO, null);
    }
    
    public EconomyResult withdraw(UUID uuid, double amount) {
        return withdraw(uuid, BigDecimal.valueOf(amount), BalanceChangeReason.ADMIN, null, null);
    }
    
    public EconomyResult withdraw(UUID uuid, BigDecimal amount) {
        return withdraw(uuid, amount, BalanceChangeReason.ADMIN, null, null);
    }
    
    public EconomyResult withdraw(UUID uuid, BigDecimal amount, BalanceChangeReason reason,
                                   String operator, String operatorName) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new EconomyResult(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "金额必须大于0");
        }
        
        PlayerAccount account = playerDataManager.getAccount(uuid);
        if (account == null) {
            return new EconomyResult(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "账户不存在");
        }
        
        BigDecimal maxBalance = plugin.getCurrencyConfig().getMaxBalanceBigDecimal();
        BigDecimal oldBalance;
        BigDecimal newBalance;
        
        synchronized (account) {
            oldBalance = account.getBalance();
            if (oldBalance.compareTo(amount) < 0) {
                return new EconomyResult(false, BigDecimal.ZERO, oldBalance, BigDecimal.ZERO, "余额不足");
            }
            newBalance = oldBalance.subtract(amount);
            account.setBalance(newBalance);
        }
        
        BalanceChangeEvent event = new BalanceChangeEvent(uuid, oldBalance, newBalance, amount.negate(), reason);
        AsyncUtils.callEventOnMain(event);
        
        if (event.isCancelled()) {
            synchronized (account) {
                account.setBalance(oldBalance);
            }
            return new EconomyResult(false, BigDecimal.ZERO, oldBalance, BigDecimal.ZERO, "操作被取消");
        }
        
        BigDecimal eventBalance = plugin.getCurrencyConfig().formatInput(event.getNewBalanceDecimal());
        eventBalance = eventBalance.max(BigDecimal.ZERO).min(maxBalance);
        synchronized (account) {
            account.setBalance(eventBalance);
        }
        newBalance = eventBalance;
        
        playerDataManager.saveAccount(account);
        
        logManager.logBalanceChange(uuid, account.getPlayerName(), "WITHDRAW", 
                                    amount, oldBalance, 
                                    newBalance, operator, operatorName, reason != null ? reason.name() : null);
        
        publishSync(uuid, account.getPlayerName(), newBalance);
        
        BigDecimal actualChange = oldBalance.subtract(newBalance);
        return new EconomyResult(true, actualChange, newBalance, BigDecimal.ZERO, null);
    }
    
    public EconomyResult set(UUID uuid, double amount) {
        return set(uuid, BigDecimal.valueOf(amount), BalanceChangeReason.ADMIN, null, null);
    }
    
    public EconomyResult set(UUID uuid, BigDecimal amount) {
        return set(uuid, amount, BalanceChangeReason.ADMIN, null, null);
    }
    
    public EconomyResult set(UUID uuid, BigDecimal amount, BalanceChangeReason reason,
                              String operator, String operatorName) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return new EconomyResult(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "金额不能为负数");
        }
        
        PlayerAccount account = playerDataManager.getAccount(uuid);
        if (account == null) {
            return new EconomyResult(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "账户不存在");
        }
        
        BigDecimal maxBalance = plugin.getCurrencyConfig().getMaxBalanceBigDecimal();
        BigDecimal oldBalance;
        BigDecimal newBalance;
        
        synchronized (account) {
            oldBalance = account.getBalance();
            if (amount.compareTo(maxBalance) > 0) {
                return new EconomyResult(false, BigDecimal.ZERO, oldBalance, BigDecimal.ZERO, "余额超出上限");
            }
            newBalance = amount;
            account.setBalance(newBalance);
        }
        
        BalanceChangeEvent event = new BalanceChangeEvent(uuid, oldBalance, newBalance, amount.subtract(oldBalance), reason);
        AsyncUtils.callEventOnMain(event);
        
        if (event.isCancelled()) {
            synchronized (account) {
                account.setBalance(oldBalance);
            }
            return new EconomyResult(false, BigDecimal.ZERO, oldBalance, BigDecimal.ZERO, "操作被取消");
        }
        
        BigDecimal eventBalance = plugin.getCurrencyConfig().formatInput(event.getNewBalanceDecimal());
        eventBalance = eventBalance.max(BigDecimal.ZERO).min(maxBalance);
        synchronized (account) {
            account.setBalance(eventBalance);
        }
        newBalance = eventBalance;
        
        playerDataManager.saveAccount(account);
        
        logManager.logBalanceChange(uuid, account.getPlayerName(), "SET", 
                                    amount.subtract(oldBalance).abs(), 
                                    oldBalance, newBalance, 
                                    operator, operatorName, reason != null ? reason.name() : null);
        
        publishSync(uuid, account.getPlayerName(), newBalance);
        
        BigDecimal actualChange = newBalance.subtract(oldBalance);
        return new EconomyResult(true, actualChange, newBalance, BigDecimal.ZERO, null);
    }
    
    private void publishSync(UUID uuid, String playerName, BigDecimal newBalance) {
        if (plugin.getRedisSyncManager() != null) {
            plugin.getRedisSyncManager().publishBalanceUpdate(uuid, playerName, newBalance);
        }
    }
    
    public double getDailyIncome(UUID uuid) {
        PlayerAccount account = playerDataManager.getAccount(uuid);
        return account != null ? account.getDailyIncomeDouble() : 0;
    }
    
    public BigDecimal getDailyIncomeDecimal(UUID uuid) {
        PlayerAccount account = playerDataManager.getAccount(uuid);
        return account != null ? account.getDailyIncome() : BigDecimal.ZERO;
    }
    
    public BigDecimal getWeeklyIncomeDecimal(UUID uuid) {
        try {
            long fromTimestamp = getStartOfWeekTimestamp();
            return plugin.getDatabaseManager().getLogDAO().getIncomeInPeriod(uuid, fromTimestamp);
        } catch (SQLException e) {
            plugin.getLogger().warning("查询周收入失败: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    public BigDecimal getMonthlyIncomeDecimal(UUID uuid) {
        try {
            long fromTimestamp = getStartOfMonthTimestamp();
            return plugin.getDatabaseManager().getLogDAO().getIncomeInPeriod(uuid, fromTimestamp);
        } catch (SQLException e) {
            plugin.getLogger().warning("查询月收入失败: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    private long getStartOfDayTimestamp() {
        return LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
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
    
    private static class BatchContext {
        final List<UUID> allowedUuids = new ArrayList<>();
        final Map<UUID, BigDecimal> oldBalances = new HashMap<>();
        final Map<UUID, String> nameMap = new HashMap<>();
        int totalAccounts;
    }
    
    private BatchContext collectAllowedAccounts(Collection<PlayerAccount> accounts, BigDecimal amount,
                                                BalanceChangeReason reason, boolean isWithdraw, boolean isSet) {
        BatchContext ctx = new BatchContext();
        ctx.totalAccounts = accounts.size();
        
        for (PlayerAccount account : accounts) {
            UUID uuid = account.getUuid();
            BigDecimal oldBalance = account.getBalance();
            
            if (isWithdraw && oldBalance.compareTo(amount) < 0) {
                continue;
            }
            
            BigDecimal newBalance = isSet ? amount : (isWithdraw ? oldBalance.subtract(amount) : oldBalance.add(amount));
            BigDecimal changeAmount = isSet ? amount.subtract(oldBalance) : (isWithdraw ? amount.negate() : amount);
            
            BalanceChangeEvent event = new BalanceChangeEvent(uuid, oldBalance, newBalance, changeAmount, reason);
            AsyncUtils.callEventOnMain(event);
            
            if (!event.isCancelled()) {
                ctx.allowedUuids.add(uuid);
                ctx.oldBalances.put(uuid, oldBalance);
                ctx.nameMap.put(uuid, account.getPlayerName());
            }
        }
        return ctx;
    }
    
    private void processBatchResult(BatchContext ctx, BigDecimal amount, String logType,
                                     boolean isWithdraw, boolean isSet, String operator, String operatorName) {
        playerDataManager.invalidateAllCache(false);
        for (UUID uuid : ctx.allowedUuids) {
            BigDecimal oldBalance = ctx.oldBalances.get(uuid);
            BigDecimal newBalance = isSet ? amount : (isWithdraw ? oldBalance.subtract(amount) : oldBalance.add(amount));
            newBalance = plugin.getCurrencyConfig().formatInput(newBalance);
            String playerName = ctx.nameMap.get(uuid);
            
            logManager.logBalanceChange(uuid, playerName, logType,
                isSet ? amount.subtract(oldBalance).abs() : amount,
                oldBalance, newBalance, operator, operatorName, null);
            
            if (plugin.getRedisSyncManager() != null) {
                plugin.getRedisSyncManager().publishBalanceUpdate(uuid, playerName, newBalance);
            }
        }
    }
    
    public BatchResult depositAll(BigDecimal amount, boolean onlineOnly, String operator, String operatorName) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new BatchResult(0, 0, amount);
        }
        
        Collection<PlayerAccount> accounts = onlineOnly ? playerDataManager.getOnlineAccounts() : playerDataManager.getAllAccounts();
        if (accounts.isEmpty()) {
            return new BatchResult(0, 0, amount);
        }
        
        BatchContext ctx = collectAllowedAccounts(accounts, amount, BalanceChangeReason.ADMIN, false, false);
        if (ctx.allowedUuids.isEmpty()) {
            return new BatchResult(0, ctx.totalAccounts, amount);
        }
        
        try {
            playerDataManager.saveAll();
            int updated = plugin.getPlayerDataManager().getPlayerDAO().depositAllBatch(amount, true, ctx.allowedUuids);
            processBatchResult(ctx, amount, "DEPOSIT_ALL", false, false, operator, operatorName);
            return new BatchResult(updated, ctx.totalAccounts - updated, amount);
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("批量存款失败：%s", e.getMessage()));
            return new BatchResult(0, ctx.totalAccounts, amount);
        }
    }
    
    public BatchResult withdrawAll(BigDecimal amount, boolean onlineOnly, String operator, String operatorName) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new BatchResult(0, 0, amount);
        }
        
        Collection<PlayerAccount> accounts = onlineOnly ? playerDataManager.getOnlineAccounts() : playerDataManager.getAllAccounts();
        if (accounts.isEmpty()) {
            return new BatchResult(0, 0, amount);
        }
        
        BatchContext ctx = collectAllowedAccounts(accounts, amount, BalanceChangeReason.ADMIN, true, false);
        if (ctx.allowedUuids.isEmpty()) {
            return new BatchResult(0, ctx.totalAccounts, amount);
        }
        
        try {
            playerDataManager.saveAll();
            int updated = plugin.getPlayerDataManager().getPlayerDAO().withdrawAllBatch(amount, true, ctx.allowedUuids);
            processBatchResult(ctx, amount, "WITHDRAW_ALL", true, false, operator, operatorName);
            return new BatchResult(updated, ctx.totalAccounts - updated, amount);
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("批量扣款失败：%s", e.getMessage()));
            return new BatchResult(0, ctx.totalAccounts, amount);
        }
    }
    
    public BatchResult setAll(BigDecimal amount, boolean onlineOnly, String operator, String operatorName) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return new BatchResult(0, 0, amount);
        }
        
        Collection<PlayerAccount> accounts = onlineOnly ? playerDataManager.getOnlineAccounts() : playerDataManager.getAllAccounts();
        if (accounts.isEmpty()) {
            return new BatchResult(0, 0, amount);
        }
        
        BatchContext ctx = collectAllowedAccounts(accounts, amount, BalanceChangeReason.ADMIN, false, true);
        if (ctx.allowedUuids.isEmpty()) {
            return new BatchResult(0, ctx.totalAccounts, amount);
        }
        
        try {
            playerDataManager.saveAll();
            int updated = plugin.getPlayerDataManager().getPlayerDAO().setAllBatch(amount, true, ctx.allowedUuids);
            processBatchResult(ctx, amount, "SET_ALL", false, true, operator, operatorName);
            return new BatchResult(updated, ctx.totalAccounts - updated, amount);
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("批量设置余额失败：%s", e.getMessage()));
            return new BatchResult(0, ctx.totalAccounts, amount);
        }
    }
    
    public void depositAllAsync(BigDecimal amount, boolean onlineOnly, String operator, String operatorName,
                                Consumer<BatchResult> callback) {
        Collection<PlayerAccount> accounts = onlineOnly ? playerDataManager.getOnlineAccounts() : playerDataManager.getAllAccounts();
        if (accounts == null || accounts.isEmpty()) {
            callback.accept(new BatchResult(0, 0, amount));
            return;
        }
        
        BatchContext ctx = collectAllowedAccounts(accounts, amount, BalanceChangeReason.ADMIN, false, false);
        if (ctx.allowedUuids.isEmpty()) {
            callback.accept(new BatchResult(0, ctx.totalAccounts, amount));
            return;
        }
        
        playerDataManager.saveAll();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int updated = plugin.getPlayerDataManager().getPlayerDAO().depositAllBatch(amount, true, ctx.allowedUuids);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    processBatchResult(ctx, amount, "DEPOSIT_ALL", false, false, operator, operatorName);
                    callback.accept(new BatchResult(updated, ctx.totalAccounts - updated, amount));
                });
            } catch (SQLException e) {
                plugin.getLogger().severe(String.format("批量存款失败：%s", e.getMessage()));
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(new BatchResult(0, ctx.totalAccounts, amount)));
            }
        });
    }
    
    public void withdrawAllAsync(BigDecimal amount, boolean onlineOnly, String operator, String operatorName,
                                 Consumer<BatchResult> callback) {
        Collection<PlayerAccount> accounts = onlineOnly ? playerDataManager.getOnlineAccounts() : playerDataManager.getAllAccounts();
        if (accounts == null || accounts.isEmpty()) {
            callback.accept(new BatchResult(0, 0, amount));
            return;
        }
        
        BatchContext ctx = collectAllowedAccounts(accounts, amount, BalanceChangeReason.ADMIN, true, false);
        if (ctx.allowedUuids.isEmpty()) {
            callback.accept(new BatchResult(0, ctx.totalAccounts, amount));
            return;
        }
        
        playerDataManager.saveAll();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int updated = plugin.getPlayerDataManager().getPlayerDAO().withdrawAllBatch(amount, true, ctx.allowedUuids);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    processBatchResult(ctx, amount, "WITHDRAW_ALL", true, false, operator, operatorName);
                    callback.accept(new BatchResult(updated, ctx.totalAccounts - updated, amount));
                });
            } catch (SQLException e) {
                plugin.getLogger().severe(String.format("批量扣款失败：%s", e.getMessage()));
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(new BatchResult(0, ctx.totalAccounts, amount)));
            }
        });
    }
    
    public void setAllAsync(BigDecimal amount, boolean onlineOnly, String operator, String operatorName,
                            Consumer<BatchResult> callback) {
        Collection<PlayerAccount> accounts = onlineOnly ? playerDataManager.getOnlineAccounts() : playerDataManager.getAllAccounts();
        if (accounts == null || accounts.isEmpty()) {
            callback.accept(new BatchResult(0, 0, amount));
            return;
        }
        
        BatchContext ctx = collectAllowedAccounts(accounts, amount, BalanceChangeReason.ADMIN, false, true);
        if (ctx.allowedUuids.isEmpty()) {
            callback.accept(new BatchResult(0, ctx.totalAccounts, amount));
            return;
        }
        
        playerDataManager.saveAll();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int updated = plugin.getPlayerDataManager().getPlayerDAO().setAllBatch(amount, true, ctx.allowedUuids);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    processBatchResult(ctx, amount, "SET_ALL", false, true, operator, operatorName);
                    callback.accept(new BatchResult(updated, ctx.totalAccounts - updated, amount));
                });
            } catch (SQLException e) {
                plugin.getLogger().severe(String.format("批量设置余额失败：%s", e.getMessage()));
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(new BatchResult(0, ctx.totalAccounts, amount)));
            }
        });
    }
    
    public static class BatchResult {
        private final int successCount;
        private final int failedCount;
        private final BigDecimal amount;
        
        public BatchResult(int successCount, int failedCount, BigDecimal amount) {
            this.successCount = successCount;
            this.failedCount = failedCount;
            this.amount = amount;
        }
        
        public int getSuccessCount() {
            return successCount;
        }
        
        public int getFailedCount() {
            return failedCount;
        }
        
        public int getTotalCount() {
            return successCount + failedCount;
        }
        
        public BigDecimal getAmount() {
            return amount;
        }
        
        public double getAmountDouble() {
            return amount.doubleValue();
        }
    }
    
    public static class EconomyResult {
        private final boolean success;
        private final BigDecimal amount;
        private final BigDecimal balance;
        private final BigDecimal tax;
        private final String errorMessage;
        
        public EconomyResult(boolean success, BigDecimal amount, BigDecimal balance, BigDecimal tax, String errorMessage) {
            this.success = success;
            this.amount = amount;
            this.balance = balance;
            this.tax = tax;
            this.errorMessage = errorMessage;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public double getAmount() {
            return amount.doubleValue();
        }
        
        public BigDecimal getAmountDecimal() {
            return amount;
        }
        
        public double getBalance() {
            return balance.doubleValue();
        }
        
        public BigDecimal getBalanceDecimal() {
            return balance;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public double getTax() {
            return tax.doubleValue();
        }
        
        public BigDecimal getTaxDecimal() {
            return tax;
        }
    }
}
