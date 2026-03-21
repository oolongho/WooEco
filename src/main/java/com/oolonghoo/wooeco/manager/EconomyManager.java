package com.oolonghoo.wooeco.manager;

import java.math.BigDecimal;
import java.sql.SQLException;
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
import com.oolonghoo.wooeco.util.MoneyFormat;

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
            return new EconomyResult(false, BigDecimal.ZERO, BigDecimal.ZERO, "金额必须大于0");
        }
        
        PlayerAccount account = playerDataManager.getAccount(uuid);
        if (account == null) {
            plugin.getDebugManager().economyError("DEPOSIT", uuid, "账户不存在");
            return new EconomyResult(false, BigDecimal.ZERO, BigDecimal.ZERO, "账户不存在");
        }
        
        BigDecimal oldBalance = account.getBalance();
        BigDecimal maxBalance = MoneyFormat.getMaxBalance();
        
        if (oldBalance.add(amount).compareTo(maxBalance) > 0) {
            return new EconomyResult(false, oldBalance, oldBalance, "余额已达上限");
        }
        
        BalanceChangeEvent event = new BalanceChangeEvent(uuid, oldBalance.doubleValue(), 
                                                          oldBalance.add(amount).doubleValue(), 
                                                          amount.doubleValue(), reason);
        Bukkit.getPluginManager().callEvent(event);
        plugin.getDebugManager().event("BalanceChangeEvent", "UUID: " + uuid + " | Amount: " + amount);
        
        if (event.isCancelled()) {
            return new EconomyResult(false, oldBalance, oldBalance, "操作被取消");
        }
        
        BigDecimal newBalance = MoneyFormat.formatInput(BigDecimal.valueOf(event.getNewBalance()));
        account.setBalance(newBalance);
        account.addDailyIncome(amount);
        playerDataManager.saveAccount(account);
        
        plugin.getDebugManager().economy("DEPOSIT", uuid, account.getPlayerName(), amount, oldBalance, newBalance);
        
        logManager.logBalanceChange(uuid, account.getPlayerName(), "DEPOSIT", 
                                    amount.doubleValue(), oldBalance.doubleValue(), 
                                    newBalance.doubleValue(), operator, operatorName, null);
        
        publishSync(uuid, account.getPlayerName(), newBalance);
        
        return new EconomyResult(true, amount, newBalance, null);
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
            return new EconomyResult(false, BigDecimal.ZERO, BigDecimal.ZERO, "金额必须大于0");
        }
        
        PlayerAccount account = playerDataManager.getAccount(uuid);
        if (account == null) {
            return new EconomyResult(false, BigDecimal.ZERO, BigDecimal.ZERO, "账户不存在");
        }
        
        BigDecimal oldBalance = account.getBalance();
        
        if (oldBalance.compareTo(amount) < 0) {
            return new EconomyResult(false, BigDecimal.ZERO, oldBalance, "余额不足");
        }
        
        BalanceChangeEvent event = new BalanceChangeEvent(uuid, oldBalance.doubleValue(), 
                                                          oldBalance.subtract(amount).doubleValue(), 
                                                          -amount.doubleValue(), reason);
        Bukkit.getPluginManager().callEvent(event);
        
        if (event.isCancelled()) {
            return new EconomyResult(false, oldBalance, oldBalance, "操作被取消");
        }
        
        BigDecimal newBalance = MoneyFormat.formatInput(BigDecimal.valueOf(event.getNewBalance()));
        account.setBalance(newBalance);
        playerDataManager.saveAccount(account);
        
        logManager.logBalanceChange(uuid, account.getPlayerName(), "WITHDRAW", 
                                    amount.doubleValue(), oldBalance.doubleValue(), 
                                    newBalance.doubleValue(), operator, operatorName, null);
        
        publishSync(uuid, account.getPlayerName(), newBalance);
        
        return new EconomyResult(true, amount, newBalance, null);
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
            return new EconomyResult(false, BigDecimal.ZERO, BigDecimal.ZERO, "金额不能为负数");
        }
        
        PlayerAccount account = playerDataManager.getAccount(uuid);
        if (account == null) {
            return new EconomyResult(false, BigDecimal.ZERO, BigDecimal.ZERO, "账户不存在");
        }
        
        BigDecimal oldBalance = account.getBalance();
        BigDecimal maxBalance = MoneyFormat.getMaxBalance();
        
        if (amount.compareTo(maxBalance) > 0) {
            return new EconomyResult(false, oldBalance, oldBalance, "余额超出上限");
        }
        
        BalanceChangeEvent event = new BalanceChangeEvent(uuid, oldBalance.doubleValue(), 
                                                          amount.doubleValue(), 
                                                          amount.subtract(oldBalance).doubleValue(), reason);
        Bukkit.getPluginManager().callEvent(event);
        
        if (event.isCancelled()) {
            return new EconomyResult(false, oldBalance, oldBalance, "操作被取消");
        }
        
        BigDecimal newBalance = MoneyFormat.formatInput(BigDecimal.valueOf(event.getNewBalance()));
        account.setBalance(newBalance);
        playerDataManager.saveAccount(account);
        
        logManager.logBalanceChange(uuid, account.getPlayerName(), "SET", 
                                    amount.subtract(oldBalance).abs().doubleValue(), 
                                    oldBalance.doubleValue(), newBalance.doubleValue(), 
                                    operator, operatorName, null);
        
        publishSync(uuid, account.getPlayerName(), newBalance);
        
        return new EconomyResult(true, amount.subtract(oldBalance).abs(), newBalance, null);
    }
    
    private void publishSync(UUID uuid, String playerName, BigDecimal newBalance) {
        if (plugin.getRedisSyncManager() != null) {
            plugin.getRedisSyncManager().publishBalanceUpdate(uuid, playerName, newBalance.doubleValue());
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
    
    public BatchResult depositAll(BigDecimal amount, boolean onlineOnly, String operator, String operatorName) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new BatchResult(0, 0, amount);
        }
        
        java.util.Collection<PlayerAccount> accounts;
        java.util.List<UUID> onlineUuids = null;
        
        if (onlineOnly) {
            accounts = playerDataManager.getOnlineAccounts();
            onlineUuids = new java.util.ArrayList<>();
            for (PlayerAccount account : accounts) {
                onlineUuids.add(account.getUuid());
            }
        } else {
            accounts = playerDataManager.getAllAccounts();
        }
        
        int totalAccounts = accounts.size();
        if (totalAccounts == 0) {
            return new BatchResult(0, 0, amount);
        }
        
        java.util.Map<UUID, java.math.BigDecimal> oldBalances = new java.util.HashMap<>();
        java.util.Map<UUID, String> nameMap = new java.util.HashMap<>();
        for (PlayerAccount account : accounts) {
            oldBalances.put(account.getUuid(), account.getBalance());
            nameMap.put(account.getUuid(), account.getPlayerName());
        }
        
        try {
            int updated = plugin.getPlayerDataManager().getPlayerDAO()
                .depositAllBatch(amount.doubleValue(), onlineOnly, onlineUuids);
            
            playerDataManager.invalidateAllCache();
            
            for (java.util.Map.Entry<UUID, java.math.BigDecimal> entry : oldBalances.entrySet()) {
                UUID uuid = entry.getKey();
                java.math.BigDecimal oldBalance = entry.getValue();
                java.math.BigDecimal newBalance = oldBalance.add(amount);
                String playerName = nameMap.get(uuid);
                
                BalanceChangeEvent event = new BalanceChangeEvent(
                    uuid, 
                    oldBalance.doubleValue(),
                    newBalance.doubleValue(),
                    amount.doubleValue(), 
                    BalanceChangeReason.ADMIN
                );
                Bukkit.getPluginManager().callEvent(event);
                
                logManager.logBalanceChange(
                    uuid, 
                    playerName, 
                    "DEPOSIT_ALL", 
                    amount.doubleValue(), 
                    oldBalance.doubleValue(),
                    newBalance.doubleValue(),
                    operator, 
                    operatorName, 
                    null
                );
                
                if (plugin.getRedisSyncManager() != null) {
                    plugin.getRedisSyncManager().publishBalanceUpdate(
                        uuid, 
                        playerName, 
                        newBalance.doubleValue()
                    );
                }
            }
            
            return new BatchResult(updated, totalAccounts - updated, amount);
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("批量存款失败：%s", e.getMessage()));
            return new BatchResult(0, totalAccounts, amount);
        }
    }
    
    public BatchResult withdrawAll(BigDecimal amount, boolean onlineOnly, String operator, String operatorName) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new BatchResult(0, 0, amount);
        }
        
        java.util.Collection<PlayerAccount> accounts;
        java.util.List<UUID> onlineUuids = null;
        
        if (onlineOnly) {
            accounts = playerDataManager.getOnlineAccounts();
            onlineUuids = new java.util.ArrayList<>();
            for (PlayerAccount account : accounts) {
                onlineUuids.add(account.getUuid());
            }
        } else {
            accounts = playerDataManager.getAllAccounts();
        }
        
        int totalAccounts = accounts.size();
        if (totalAccounts == 0) {
            return new BatchResult(0, 0, amount);
        }
        
        java.util.Map<UUID, java.math.BigDecimal> oldBalances = new java.util.HashMap<>();
        java.util.Map<UUID, String> nameMap = new java.util.HashMap<>();
        for (PlayerAccount account : accounts) {
            oldBalances.put(account.getUuid(), account.getBalance());
            nameMap.put(account.getUuid(), account.getPlayerName());
        }
        
        try {
            int updated = plugin.getPlayerDataManager().getPlayerDAO()
                .withdrawAllBatch(amount.doubleValue(), onlineOnly, onlineUuids);
            
            playerDataManager.invalidateAllCache();
            
            for (java.util.Map.Entry<UUID, java.math.BigDecimal> entry : oldBalances.entrySet()) {
                UUID uuid = entry.getKey();
                java.math.BigDecimal oldBalance = entry.getValue();
                String playerName = nameMap.get(uuid);
                
                if (oldBalance.compareTo(amount) >= 0) {
                    java.math.BigDecimal newBalance = oldBalance.subtract(amount);
                    
                    BalanceChangeEvent event = new BalanceChangeEvent(
                        uuid, 
                        oldBalance.doubleValue(),
                        newBalance.doubleValue(),
                        -amount.doubleValue(), 
                        BalanceChangeReason.ADMIN
                    );
                    Bukkit.getPluginManager().callEvent(event);
                    
                    logManager.logBalanceChange(
                        uuid, 
                        playerName, 
                        "WITHDRAW_ALL", 
                        amount.doubleValue(), 
                        oldBalance.doubleValue(),
                        newBalance.doubleValue(),
                        operator, 
                        operatorName, 
                        null
                    );
                    
                    if (plugin.getRedisSyncManager() != null) {
                        plugin.getRedisSyncManager().publishBalanceUpdate(
                            uuid, 
                            playerName, 
                            newBalance.doubleValue()
                        );
                    }
                }
            }
            
            return new BatchResult(updated, totalAccounts - updated, amount);
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("批量扣款失败：%s", e.getMessage()));
            return new BatchResult(0, totalAccounts, amount);
        }
    }
    
    public BatchResult setAll(BigDecimal amount, boolean onlineOnly, String operator, String operatorName) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return new BatchResult(0, 0, amount);
        }
        
        java.util.Collection<PlayerAccount> accounts;
        java.util.List<UUID> onlineUuids = null;
        
        if (onlineOnly) {
            accounts = playerDataManager.getOnlineAccounts();
            onlineUuids = new java.util.ArrayList<>();
            for (PlayerAccount account : accounts) {
                onlineUuids.add(account.getUuid());
            }
        } else {
            accounts = playerDataManager.getAllAccounts();
        }
        
        int totalAccounts = accounts.size();
        if (totalAccounts == 0) {
            return new BatchResult(0, 0, amount);
        }
        
        java.util.Map<UUID, java.math.BigDecimal> oldBalances = new java.util.HashMap<>();
        java.util.Map<UUID, String> nameMap = new java.util.HashMap<>();
        for (PlayerAccount account : accounts) {
            oldBalances.put(account.getUuid(), account.getBalance());
            nameMap.put(account.getUuid(), account.getPlayerName());
        }
        
        try {
            int updated = plugin.getPlayerDataManager().getPlayerDAO()
                .setAllBatch(amount.doubleValue(), onlineOnly, onlineUuids);
            
            playerDataManager.invalidateAllCache();
            
            for (java.util.Map.Entry<UUID, java.math.BigDecimal> entry : oldBalances.entrySet()) {
                UUID uuid = entry.getKey();
                java.math.BigDecimal oldBalance = entry.getValue();
                String playerName = nameMap.get(uuid);
                
                BalanceChangeEvent event = new BalanceChangeEvent(
                    uuid, 
                    oldBalance.doubleValue(),
                    amount.doubleValue(),
                    amount.subtract(oldBalance).doubleValue(), 
                    BalanceChangeReason.ADMIN
                );
                Bukkit.getPluginManager().callEvent(event);
                
                logManager.logBalanceChange(
                    uuid, 
                    playerName, 
                    "SET_ALL", 
                    amount.subtract(oldBalance).abs().doubleValue(), 
                    oldBalance.doubleValue(),
                    amount.doubleValue(),
                    operator, 
                    operatorName, 
                    null
                );
                
                if (plugin.getRedisSyncManager() != null) {
                    plugin.getRedisSyncManager().publishBalanceUpdate(
                        uuid, 
                        playerName, 
                        amount.doubleValue()
                    );
                }
            }
            
            return new BatchResult(updated, totalAccounts - updated, amount);
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("批量设置余额失败：%s", e.getMessage()));
            return new BatchResult(0, totalAccounts, amount);
        }
    }
    
    /**
     * 异步批量操作，避免大量玩家时阻塞主线程
     * DB 操作在异步线程执行，事件和回调在主线程执行
     */
    public void depositAllAsync(BigDecimal amount, boolean onlineOnly, String operator, String operatorName,
                                Consumer<BatchResult> callback) {
        runBatchAsync(() -> {
            Collection<PlayerAccount> accounts = onlineOnly ? playerDataManager.getOnlineAccounts() : playerDataManager.getAllAccounts();
            List<UUID> onlineUuids = onlineOnly ? new ArrayList<>() : null;
            if (onlineOnly && accounts != null) {
                for (PlayerAccount a : accounts) {
                    onlineUuids.add(a.getUuid());
                }
            }
            Map<UUID, BigDecimal> oldBalances = new HashMap<>();
            Map<UUID, String> nameMap = new HashMap<>();
            for (PlayerAccount a : accounts) {
                oldBalances.put(a.getUuid(), a.getBalance());
                nameMap.put(a.getUuid(), a.getPlayerName());
            }
            int totalAccounts = accounts.size();
            if (totalAccounts == 0) {
                return new BatchContext(new BatchResult(0, 0, amount), oldBalances, nameMap, amount, "DEPOSIT_ALL", operator, operatorName);
            }
            try {
                int updated = plugin.getPlayerDataManager().getPlayerDAO()
                    .depositAllBatch(amount.doubleValue(), onlineOnly, onlineUuids);
                return new BatchContext(new BatchResult(updated, totalAccounts - updated, amount), oldBalances, nameMap, amount, "DEPOSIT_ALL", operator, operatorName);
            } catch (SQLException e) {
                plugin.getLogger().severe("批量存款失败：" + e.getMessage());
                return new BatchContext(new BatchResult(0, totalAccounts, amount), oldBalances, nameMap, amount, "DEPOSIT_ALL", operator, operatorName);
            }
        }, (ctx) -> {
            for (Map.Entry<UUID, BigDecimal> e : ctx.oldBalances.entrySet()) {
                BigDecimal newBalance = e.getValue().add(ctx.amount);
                BalanceChangeEvent event = new BalanceChangeEvent(e.getKey(), e.getValue().doubleValue(), newBalance.doubleValue(), ctx.amount.doubleValue(), BalanceChangeReason.ADMIN);
                Bukkit.getPluginManager().callEvent(event);
                logManager.logBalanceChange(e.getKey(), ctx.nameMap.get(e.getKey()), ctx.action, ctx.amount.doubleValue(), e.getValue().doubleValue(), newBalance.doubleValue(), ctx.operator, ctx.operatorName, null);
                if (plugin.getRedisSyncManager() != null) {
                    plugin.getRedisSyncManager().publishBalanceUpdate(e.getKey(), ctx.nameMap.get(e.getKey()), newBalance.doubleValue());
                }
            }
            playerDataManager.invalidateAllCache();
        }, callback);
    }
    
    public void withdrawAllAsync(BigDecimal amount, boolean onlineOnly, String operator, String operatorName,
                                 Consumer<BatchResult> callback) {
        runBatchAsync(() -> {
            Collection<PlayerAccount> accounts = onlineOnly ? playerDataManager.getOnlineAccounts() : playerDataManager.getAllAccounts();
            List<UUID> onlineUuids = onlineOnly ? new ArrayList<>() : null;
            if (onlineOnly && accounts != null) {
                for (PlayerAccount a : accounts) {
                    onlineUuids.add(a.getUuid());
                }
            }
            Map<UUID, BigDecimal> oldBalances = new HashMap<>();
            Map<UUID, String> nameMap = new HashMap<>();
            for (PlayerAccount a : accounts) {
                oldBalances.put(a.getUuid(), a.getBalance());
                nameMap.put(a.getUuid(), a.getPlayerName());
            }
            int totalAccounts = accounts.size();
            if (totalAccounts == 0) {
                return new BatchContext(new BatchResult(0, 0, amount), oldBalances, nameMap, amount, "WITHDRAW_ALL", operator, operatorName);
            }
            try {
                int updated = plugin.getPlayerDataManager().getPlayerDAO()
                    .withdrawAllBatch(amount.doubleValue(), onlineOnly, onlineUuids);
                return new BatchContext(new BatchResult(updated, totalAccounts - updated, amount), oldBalances, nameMap, amount, "WITHDRAW_ALL", operator, operatorName);
            } catch (SQLException e) {
                plugin.getLogger().severe("批量扣款失败：" + e.getMessage());
                return new BatchContext(new BatchResult(0, totalAccounts, amount), oldBalances, nameMap, amount, "WITHDRAW_ALL", operator, operatorName);
            }
        }, (ctx) -> {
            for (Map.Entry<UUID, BigDecimal> e : ctx.oldBalances.entrySet()) {
                if (e.getValue().compareTo(ctx.amount) >= 0) {
                    BigDecimal newBalance = e.getValue().subtract(ctx.amount);
                    BalanceChangeEvent event = new BalanceChangeEvent(e.getKey(), e.getValue().doubleValue(), newBalance.doubleValue(), -ctx.amount.doubleValue(), BalanceChangeReason.ADMIN);
                    Bukkit.getPluginManager().callEvent(event);
                    logManager.logBalanceChange(e.getKey(), ctx.nameMap.get(e.getKey()), ctx.action, ctx.amount.doubleValue(), e.getValue().doubleValue(), newBalance.doubleValue(), ctx.operator, ctx.operatorName, null);
                    if (plugin.getRedisSyncManager() != null) {
                        plugin.getRedisSyncManager().publishBalanceUpdate(e.getKey(), ctx.nameMap.get(e.getKey()), newBalance.doubleValue());
                    }
                }
            }
            playerDataManager.invalidateAllCache();
        }, callback);
    }
    
    public void setAllAsync(BigDecimal amount, boolean onlineOnly, String operator, String operatorName,
                            Consumer<BatchResult> callback) {
        runBatchAsync(() -> {
            Collection<PlayerAccount> accounts = onlineOnly ? playerDataManager.getOnlineAccounts() : playerDataManager.getAllAccounts();
            List<UUID> onlineUuids = onlineOnly ? new ArrayList<>() : null;
            if (onlineOnly && accounts != null) {
                for (PlayerAccount a : accounts) {
                    onlineUuids.add(a.getUuid());
                }
            }
            Map<UUID, BigDecimal> oldBalances = new HashMap<>();
            Map<UUID, String> nameMap = new HashMap<>();
            for (PlayerAccount a : accounts) {
                oldBalances.put(a.getUuid(), a.getBalance());
                nameMap.put(a.getUuid(), a.getPlayerName());
            }
            int totalAccounts = accounts.size();
            if (totalAccounts == 0) {
                return new BatchContext(new BatchResult(0, 0, amount), oldBalances, nameMap, amount, "SET_ALL", operator, operatorName);
            }
            try {
                int updated = plugin.getPlayerDataManager().getPlayerDAO()
                    .setAllBatch(amount.doubleValue(), onlineOnly, onlineUuids);
                return new BatchContext(new BatchResult(updated, totalAccounts - updated, amount), oldBalances, nameMap, amount, "SET_ALL", operator, operatorName);
            } catch (SQLException e) {
                plugin.getLogger().severe("批量设置余额失败：" + e.getMessage());
                return new BatchContext(new BatchResult(0, totalAccounts, amount), oldBalances, nameMap, amount, "SET_ALL", operator, operatorName);
            }
        }, (ctx) -> {
            for (Map.Entry<UUID, BigDecimal> e : ctx.oldBalances.entrySet()) {
                BalanceChangeEvent event = new BalanceChangeEvent(e.getKey(), e.getValue().doubleValue(), ctx.amount.doubleValue(), ctx.amount.subtract(e.getValue()).doubleValue(), BalanceChangeReason.ADMIN);
                Bukkit.getPluginManager().callEvent(event);
                logManager.logBalanceChange(e.getKey(), ctx.nameMap.get(e.getKey()), ctx.action, ctx.amount.subtract(e.getValue()).abs().doubleValue(), e.getValue().doubleValue(), ctx.amount.doubleValue(), ctx.operator, ctx.operatorName, null);
                if (plugin.getRedisSyncManager() != null) {
                    plugin.getRedisSyncManager().publishBalanceUpdate(e.getKey(), ctx.nameMap.get(e.getKey()), ctx.amount.doubleValue());
                }
            }
            playerDataManager.invalidateAllCache();
        }, callback);
    }
    
    private void runBatchAsync(java.util.function.Supplier<BatchContext> asyncWork,
                               Consumer<BatchContext> syncPostProcess,
                               Consumer<BatchResult> callback) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            BatchContext ctx = asyncWork.get();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                syncPostProcess.accept(ctx);
                callback.accept(ctx.result);
            });
        });
    }
    
    private static class BatchContext {
        final BatchResult result;
        final Map<UUID, BigDecimal> oldBalances;
        final Map<UUID, String> nameMap;
        final BigDecimal amount;
        final String action;
        final String operator;
        final String operatorName;
        BatchContext(BatchResult result, Map<UUID, BigDecimal> oldBalances, Map<UUID, String> nameMap,
                     BigDecimal amount, String action, String operator, String operatorName) {
            this.result = result;
            this.oldBalances = oldBalances;
            this.nameMap = nameMap;
            this.amount = amount;
            this.action = action;
            this.operator = operator;
            this.operatorName = operatorName;
        }
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
        private final String errorMessage;
        
        public EconomyResult(boolean success, BigDecimal amount, BigDecimal balance, String errorMessage) {
            this.success = success;
            this.amount = amount;
            this.balance = balance;
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
            return 0;
        }
    }
}
