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
                return new EconomyResult(false, oldBalance, oldBalance, BigDecimal.ZERO, "余额已达上限");
            }
            account.setBalance(newBalance);
        }
        
        BalanceChangeEvent event = new BalanceChangeEvent(uuid, oldBalance, newBalance, amount, reason);
        Bukkit.getPluginManager().callEvent(event);
        plugin.getDebugManager().event("BalanceChangeEvent", "UUID: " + uuid + " | Amount: " + amount);
        
        if (event.isCancelled()) {
            synchronized (account) {
                BigDecimal current = account.getBalance();
                if (current.compareTo(newBalance) == 0) {
                    account.setBalance(oldBalance);
                }
            }
            return new EconomyResult(false, oldBalance, oldBalance, BigDecimal.ZERO, "操作被取消");
        }
        
        BigDecimal eventBalance = plugin.getCurrencyConfig().formatInput(event.getNewBalanceDecimal());
        if (eventBalance.compareTo(newBalance) != 0) {
            eventBalance = eventBalance.max(BigDecimal.ZERO).min(maxBalance);
            synchronized (account) {
                account.setBalance(eventBalance);
            }
            newBalance = eventBalance;
        }
        
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
                                    newBalance, operator, operatorName, null);
        
        publishSync(uuid, account.getPlayerName(), newBalance);
        
        return new EconomyResult(true, amount, newBalance, BigDecimal.ZERO, null);
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
        Bukkit.getPluginManager().callEvent(event);
        
        if (event.isCancelled()) {
            synchronized (account) {
                BigDecimal current = account.getBalance();
                if (current.compareTo(newBalance) == 0) {
                    account.setBalance(oldBalance);
                }
            }
            return new EconomyResult(false, oldBalance, oldBalance, BigDecimal.ZERO, "操作被取消");
        }
        
        BigDecimal eventBalance = plugin.getCurrencyConfig().formatInput(event.getNewBalanceDecimal());
        if (eventBalance.compareTo(newBalance) != 0) {
            eventBalance = eventBalance.max(BigDecimal.ZERO).min(maxBalance);
            synchronized (account) {
                account.setBalance(eventBalance);
            }
            newBalance = eventBalance;
        }
        
        playerDataManager.saveAccount(account);
        
        logManager.logBalanceChange(uuid, account.getPlayerName(), "WITHDRAW", 
                                    amount, oldBalance, 
                                    newBalance, operator, operatorName, null);
        
        publishSync(uuid, account.getPlayerName(), newBalance);
        
        return new EconomyResult(true, amount, newBalance, BigDecimal.ZERO, null);
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
                return new EconomyResult(false, oldBalance, oldBalance, BigDecimal.ZERO, "余额超出上限");
            }
            newBalance = amount;
            account.setBalance(newBalance);
        }
        
        BalanceChangeEvent event = new BalanceChangeEvent(uuid, oldBalance, newBalance, amount.subtract(oldBalance), reason);
        Bukkit.getPluginManager().callEvent(event);
        
        if (event.isCancelled()) {
            synchronized (account) {
                BigDecimal current = account.getBalance();
                if (current.compareTo(newBalance) == 0) {
                    account.setBalance(oldBalance);
                }
            }
            return new EconomyResult(false, oldBalance, oldBalance, BigDecimal.ZERO, "操作被取消");
        }
        
        BigDecimal eventBalance = plugin.getCurrencyConfig().formatInput(event.getNewBalanceDecimal());
        if (eventBalance.compareTo(newBalance) != 0) {
            eventBalance = eventBalance.max(BigDecimal.ZERO).min(maxBalance);
            synchronized (account) {
                account.setBalance(eventBalance);
            }
            newBalance = eventBalance;
        }
        
        playerDataManager.saveAccount(account);
        
        logManager.logBalanceChange(uuid, account.getPlayerName(), "SET", 
                                    amount.subtract(oldBalance).abs(), 
                                    oldBalance, newBalance, 
                                    operator, operatorName, null);
        
        publishSync(uuid, account.getPlayerName(), newBalance);
        
        return new EconomyResult(true, amount.subtract(oldBalance).abs(), newBalance, BigDecimal.ZERO, null);
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
    
    public BatchResult depositAll(BigDecimal amount, boolean onlineOnly, String operator, String operatorName) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new BatchResult(0, 0, amount);
        }
        
        Collection<PlayerAccount> accounts;
        if (onlineOnly) {
            accounts = playerDataManager.getOnlineAccounts();
        } else {
            accounts = playerDataManager.getAllAccounts();
        }
        
        int totalAccounts = accounts.size();
        if (totalAccounts == 0) {
            return new BatchResult(0, 0, amount);
        }
        
        List<UUID> allowedUuids = new ArrayList<>();
        Map<UUID, BigDecimal> oldBalances = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();
        
        for (PlayerAccount account : accounts) {
            UUID uuid = account.getUuid();
            BigDecimal oldBalance = account.getBalance();
            BigDecimal newBalance = oldBalance.add(amount);
            
            BalanceChangeEvent event = new BalanceChangeEvent(
                uuid, oldBalance, newBalance, amount, BalanceChangeReason.ADMIN
            );
            Bukkit.getPluginManager().callEvent(event);
            
            if (!event.isCancelled()) {
                allowedUuids.add(uuid);
                oldBalances.put(uuid, oldBalance);
                nameMap.put(uuid, account.getPlayerName());
            }
        }
        
        if (allowedUuids.isEmpty()) {
            return new BatchResult(0, totalAccounts, amount);
        }
        
        try {
            playerDataManager.saveAll();
            int updated = plugin.getPlayerDataManager().getPlayerDAO()
                .depositAllBatch(amount, true, allowedUuids);
            
            playerDataManager.invalidateAllCache(false);
            
            for (UUID uuid : allowedUuids) {
                BigDecimal oldBalance = oldBalances.get(uuid);
                BigDecimal newBalance = oldBalance.add(amount);
                String playerName = nameMap.get(uuid);
                
                logManager.logBalanceChange(
                    uuid, playerName, "DEPOSIT_ALL", 
                    amount, oldBalance, newBalance,
                    operator, operatorName, null
                );
                
                if (plugin.getRedisSyncManager() != null) {
                    plugin.getRedisSyncManager().publishBalanceUpdate(uuid, playerName, newBalance);
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
        
        Collection<PlayerAccount> accounts;
        if (onlineOnly) {
            accounts = playerDataManager.getOnlineAccounts();
        } else {
            accounts = playerDataManager.getAllAccounts();
        }
        
        int totalAccounts = accounts.size();
        if (totalAccounts == 0) {
            return new BatchResult(0, 0, amount);
        }
        
        List<UUID> allowedUuids = new ArrayList<>();
        Map<UUID, BigDecimal> oldBalances = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();
        
        for (PlayerAccount account : accounts) {
            UUID uuid = account.getUuid();
            BigDecimal oldBalance = account.getBalance();
            
            if (oldBalance.compareTo(amount) < 0) {
                continue;
            }
            
            BigDecimal newBalance = oldBalance.subtract(amount);
            
            BalanceChangeEvent event = new BalanceChangeEvent(
                uuid, oldBalance, newBalance, amount.negate(), BalanceChangeReason.ADMIN
            );
            Bukkit.getPluginManager().callEvent(event);
            
            if (!event.isCancelled()) {
                allowedUuids.add(uuid);
                oldBalances.put(uuid, oldBalance);
                nameMap.put(uuid, account.getPlayerName());
            }
        }
        
        if (allowedUuids.isEmpty()) {
            return new BatchResult(0, totalAccounts, amount);
        }
        
        try {
            playerDataManager.saveAll();
            int updated = plugin.getPlayerDataManager().getPlayerDAO()
                .withdrawAllBatch(amount, true, allowedUuids);
            
            playerDataManager.invalidateAllCache(false);
            
            for (UUID uuid : allowedUuids) {
                BigDecimal oldBalance = oldBalances.get(uuid);
                BigDecimal newBalance = oldBalance.subtract(amount);
                String playerName = nameMap.get(uuid);
                
                logManager.logBalanceChange(
                    uuid, playerName, "WITHDRAW_ALL",
                    amount, oldBalance, newBalance,
                    operator, operatorName, null
                );
                
                if (plugin.getRedisSyncManager() != null) {
                    plugin.getRedisSyncManager().publishBalanceUpdate(uuid, playerName, newBalance);
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
        
        Collection<PlayerAccount> accounts;
        if (onlineOnly) {
            accounts = playerDataManager.getOnlineAccounts();
        } else {
            accounts = playerDataManager.getAllAccounts();
        }
        
        int totalAccounts = accounts.size();
        if (totalAccounts == 0) {
            return new BatchResult(0, 0, amount);
        }
        
        List<UUID> allowedUuids = new ArrayList<>();
        Map<UUID, BigDecimal> oldBalances = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();
        
        for (PlayerAccount account : accounts) {
            UUID uuid = account.getUuid();
            BigDecimal oldBalance = account.getBalance();
            
            BalanceChangeEvent event = new BalanceChangeEvent(
                uuid, oldBalance, amount, amount.subtract(oldBalance), BalanceChangeReason.ADMIN
            );
            Bukkit.getPluginManager().callEvent(event);
            
            if (!event.isCancelled()) {
                allowedUuids.add(uuid);
                oldBalances.put(uuid, oldBalance);
                nameMap.put(uuid, account.getPlayerName());
            }
        }
        
        if (allowedUuids.isEmpty()) {
            return new BatchResult(0, totalAccounts, amount);
        }
        
        try {
            playerDataManager.saveAll();
            int updated = plugin.getPlayerDataManager().getPlayerDAO()
                .setAllBatch(amount, true, allowedUuids);
            
            playerDataManager.invalidateAllCache(false);
            
            for (UUID uuid : allowedUuids) {
                BigDecimal oldBalance = oldBalances.get(uuid);
                String playerName = nameMap.get(uuid);
                
                logManager.logBalanceChange(
                    uuid, playerName, "SET_ALL",
                    amount.subtract(oldBalance).abs(), oldBalance, amount,
                    operator, operatorName, null
                );
                
                if (plugin.getRedisSyncManager() != null) {
                    plugin.getRedisSyncManager().publishBalanceUpdate(uuid, playerName, amount);
                }
            }
            
            return new BatchResult(updated, totalAccounts - updated, amount);
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("批量设置余额失败：%s", e.getMessage()));
            return new BatchResult(0, totalAccounts, amount);
        }
    }
    
    public void depositAllAsync(BigDecimal amount, boolean onlineOnly, String operator, String operatorName,
                                Consumer<BatchResult> callback) {
        Collection<PlayerAccount> accounts = onlineOnly ? playerDataManager.getOnlineAccounts() : playerDataManager.getAllAccounts();
        if (accounts == null || accounts.isEmpty()) {
            callback.accept(new BatchResult(0, 0, amount));
            return;
        }
        
        List<UUID> allowedUuids = new ArrayList<>();
        Map<UUID, BigDecimal> oldBalances = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();
        
        for (PlayerAccount a : accounts) {
            if (a == null) continue;
            UUID uuid = a.getUuid();
            if (uuid == null) continue;
            BigDecimal oldBalance = a.getBalance();
            BigDecimal newBalance = oldBalance.add(amount);
            
            BalanceChangeEvent event = new BalanceChangeEvent(uuid, oldBalance, newBalance, amount, BalanceChangeReason.ADMIN);
            Bukkit.getPluginManager().callEvent(event);
            
            if (!event.isCancelled()) {
                allowedUuids.add(uuid);
                oldBalances.put(uuid, oldBalance);
                nameMap.put(uuid, a.getPlayerName());
            }
        }
        
        int totalAccounts = accounts.size();
        if (allowedUuids.isEmpty()) {
            callback.accept(new BatchResult(0, totalAccounts, amount));
            return;
        }
        
        playerDataManager.saveAll();
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int updated = plugin.getPlayerDataManager().getPlayerDAO()
                    .depositAllBatch(amount, true, allowedUuids);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    playerDataManager.invalidateAllCache(false);
                    for (UUID uuid : allowedUuids) {
                        BigDecimal oldBalance = oldBalances.get(uuid);
                        BigDecimal newBalance = oldBalance.add(amount);
                        String playerName = nameMap.get(uuid);
                        logManager.logBalanceChange(uuid, playerName, "DEPOSIT_ALL", amount, oldBalance, newBalance, operator, operatorName, null);
                        if (plugin.getRedisSyncManager() != null) {
                            plugin.getRedisSyncManager().publishBalanceUpdate(uuid, playerName, newBalance);
                        }
                    }
                    callback.accept(new BatchResult(updated, totalAccounts - updated, amount));
                });
            } catch (SQLException e) {
                plugin.getLogger().severe(String.format("批量存款失败：%s", e.getMessage()));
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    callback.accept(new BatchResult(0, totalAccounts, amount));
                });
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
        
        List<UUID> allowedUuids = new ArrayList<>();
        Map<UUID, BigDecimal> oldBalances = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();
        
        for (PlayerAccount a : accounts) {
            if (a == null) continue;
            UUID uuid = a.getUuid();
            if (uuid == null) continue;
            BigDecimal oldBalance = a.getBalance();
            if (oldBalance.compareTo(amount) < 0) continue;
            BigDecimal newBalance = oldBalance.subtract(amount);
            
            BalanceChangeEvent event = new BalanceChangeEvent(uuid, oldBalance, newBalance, amount.negate(), BalanceChangeReason.ADMIN);
            Bukkit.getPluginManager().callEvent(event);
            
            if (!event.isCancelled()) {
                allowedUuids.add(uuid);
                oldBalances.put(uuid, oldBalance);
                nameMap.put(uuid, a.getPlayerName());
            }
        }
        
        int totalAccounts = accounts.size();
        if (allowedUuids.isEmpty()) {
            callback.accept(new BatchResult(0, totalAccounts, amount));
            return;
        }
        
        playerDataManager.saveAll();
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int updated = plugin.getPlayerDataManager().getPlayerDAO()
                    .withdrawAllBatch(amount, true, allowedUuids);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    playerDataManager.invalidateAllCache(false);
                    for (UUID uuid : allowedUuids) {
                        BigDecimal oldBalance = oldBalances.get(uuid);
                        BigDecimal newBalance = oldBalance.subtract(amount);
                        String playerName = nameMap.get(uuid);
                        logManager.logBalanceChange(uuid, playerName, "WITHDRAW_ALL", amount, oldBalance, newBalance, operator, operatorName, null);
                        if (plugin.getRedisSyncManager() != null) {
                            plugin.getRedisSyncManager().publishBalanceUpdate(uuid, playerName, newBalance);
                        }
                    }
                    callback.accept(new BatchResult(updated, totalAccounts - updated, amount));
                });
            } catch (SQLException e) {
                plugin.getLogger().severe(String.format("批量扣款失败：%s", e.getMessage()));
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    callback.accept(new BatchResult(0, totalAccounts, amount));
                });
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
        
        List<UUID> allowedUuids = new ArrayList<>();
        Map<UUID, BigDecimal> oldBalances = new HashMap<>();
        Map<UUID, String> nameMap = new HashMap<>();
        
        for (PlayerAccount a : accounts) {
            if (a == null) continue;
            UUID uuid = a.getUuid();
            if (uuid == null) continue;
            BigDecimal oldBalance = a.getBalance();
            
            BalanceChangeEvent event = new BalanceChangeEvent(uuid, oldBalance, amount, amount.subtract(oldBalance), BalanceChangeReason.ADMIN);
            Bukkit.getPluginManager().callEvent(event);
            
            if (!event.isCancelled()) {
                allowedUuids.add(uuid);
                oldBalances.put(uuid, oldBalance);
                nameMap.put(uuid, a.getPlayerName());
            }
        }
        
        int totalAccounts = accounts.size();
        if (allowedUuids.isEmpty()) {
            callback.accept(new BatchResult(0, totalAccounts, amount));
            return;
        }
        
        playerDataManager.saveAll();
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int updated = plugin.getPlayerDataManager().getPlayerDAO()
                    .setAllBatch(amount, true, allowedUuids);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    playerDataManager.invalidateAllCache(false);
                    for (UUID uuid : allowedUuids) {
                        BigDecimal oldBalance = oldBalances.get(uuid);
                        String playerName = nameMap.get(uuid);
                        logManager.logBalanceChange(uuid, playerName, "SET_ALL", amount.subtract(oldBalance).abs(), oldBalance, amount, operator, operatorName, null);
                        if (plugin.getRedisSyncManager() != null) {
                            plugin.getRedisSyncManager().publishBalanceUpdate(uuid, playerName, amount);
                        }
                    }
                    callback.accept(new BatchResult(updated, totalAccounts - updated, amount));
                });
            } catch (SQLException e) {
                plugin.getLogger().severe(String.format("批量设置余额失败：%s", e.getMessage()));
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    callback.accept(new BatchResult(0, totalAccounts, amount));
                });
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
