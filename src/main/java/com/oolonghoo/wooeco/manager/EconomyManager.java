package com.oolonghoo.wooeco.manager;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.api.events.BalanceChangeEvent;
import com.oolonghoo.wooeco.api.events.BalanceChangeReason;
import com.oolonghoo.wooeco.model.PlayerAccount;
import com.oolonghoo.wooeco.util.MoneyFormat;
import org.bukkit.Bukkit;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 经济管理器
 * 处理所有余额相关操作
 * 
 * @author oolongho
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
        int success = 0;
        int failed = 0;
        
        java.util.Collection<PlayerAccount> accounts;
        if (onlineOnly) {
            accounts = playerDataManager.getOnlineAccounts();
        } else {
            accounts = playerDataManager.getAllAccounts();
        }
        
        for (PlayerAccount account : accounts) {
            EconomyResult result = deposit(account.getUuid(), amount, BalanceChangeReason.ADMIN, operator, operatorName);
            if (result.isSuccess()) {
                success++;
            } else {
                failed++;
            }
        }
        
        return new BatchResult(success, failed, amount);
    }
    
    public BatchResult withdrawAll(BigDecimal amount, boolean onlineOnly, String operator, String operatorName) {
        int success = 0;
        int failed = 0;
        
        java.util.Collection<PlayerAccount> accounts;
        if (onlineOnly) {
            accounts = playerDataManager.getOnlineAccounts();
        } else {
            accounts = playerDataManager.getAllAccounts();
        }
        
        for (PlayerAccount account : accounts) {
            EconomyResult result = withdraw(account.getUuid(), amount, BalanceChangeReason.ADMIN, operator, operatorName);
            if (result.isSuccess()) {
                success++;
            } else {
                failed++;
            }
        }
        
        return new BatchResult(success, failed, amount);
    }
    
    public BatchResult setAll(BigDecimal amount, boolean onlineOnly, String operator, String operatorName) {
        int success = 0;
        int failed = 0;
        
        java.util.Collection<PlayerAccount> accounts;
        if (onlineOnly) {
            accounts = playerDataManager.getOnlineAccounts();
        } else {
            accounts = playerDataManager.getAllAccounts();
        }
        
        for (PlayerAccount account : accounts) {
            EconomyResult result = set(account.getUuid(), amount, BalanceChangeReason.ADMIN, operator, operatorName);
            if (result.isSuccess()) {
                success++;
            } else {
                failed++;
            }
        }
        
        return new BatchResult(success, failed, amount);
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
