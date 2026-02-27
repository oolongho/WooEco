package com.oolonghoo.wooeco.manager;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.database.dao.NonPlayerAccountDAO;
import com.oolonghoo.wooeco.model.NonPlayerAccount;
import com.oolonghoo.wooeco.util.AsyncUtils;
import com.oolonghoo.wooeco.util.ThreadUtils;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 非玩家账户管理器
 * 管理城镇、势力、银行等非玩家实体账户
 * 
 * @author oolongho
 */
public class NonPlayerAccountManager {
    
    private final WooEco plugin;
    private final NonPlayerAccountDAO accountDAO;
    private final ConcurrentMap<String, NonPlayerAccount> cache;
    private final List<String> whitelistFields;
    private final boolean whitelistEnabled;
    private final boolean enabled;
    
    public NonPlayerAccountManager(WooEco plugin) {
        this.plugin = plugin;
        this.accountDAO = plugin.getDatabaseManager().getNonPlayerAccountDAO();
        this.cache = new ConcurrentHashMap<>();
        
        this.enabled = plugin.getConfig().getBoolean("non-player-account.enable", false);
        this.whitelistEnabled = plugin.getConfig().getBoolean("non-player-account.whitelist.enable", false);
        this.whitelistFields = plugin.getConfig().getStringList("non-player-account.whitelist.fields-list");
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean isNonPlayerAccount(String accountName) {
        if (!enabled) {
            return false;
        }
        
        if (!whitelistEnabled) {
            return false;
        }
        
        for (String field : whitelistFields) {
            if (accountName.toLowerCase().contains(field.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }
    
    public NonPlayerAccount getAccount(String accountName) {
        if (!enabled) {
            return null;
        }
        
        NonPlayerAccount account = cache.get(accountName);
        if (account != null) {
            return account;
        }
        
        return AsyncUtils.supplyAsyncWithTimeout(() -> {
            try {
                NonPlayerAccount acc = accountDAO.getAccount(accountName);
                if (acc != null) {
                    cache.put(accountName, acc);
                }
                return acc;
            } catch (SQLException e) {
                plugin.getLogger().severe("获取非玩家账户失败: " + e.getMessage());
                return null;
            }
        }, null);
    }
    
    public NonPlayerAccount getOrCreateAccount(String accountName) {
        if (!enabled) {
            return null;
        }
        
        NonPlayerAccount account = getAccount(accountName);
        if (account != null) {
            return account;
        }
        
        account = new NonPlayerAccount(accountName);
        
        try {
            accountDAO.createAccount(account);
            cache.put(accountName, account);
            plugin.getLogger().info("创建非玩家账户: " + accountName);
            return account;
        } catch (SQLException e) {
            plugin.getLogger().severe("创建非玩家账户失败: " + e.getMessage());
            return null;
        }
    }
    
    public boolean hasAccount(String accountName) {
        if (!enabled) {
            return false;
        }
        
        if (cache.containsKey(accountName)) {
            return true;
        }
        
        return AsyncUtils.supplyAsyncWithTimeout(() -> {
            try {
                return accountDAO.getAccount(accountName) != null;
            } catch (SQLException e) {
                return false;
            }
        }, false);
    }
    
    public BigDecimal getBalance(String accountName) {
        NonPlayerAccount account = getAccount(accountName);
        return account != null ? account.getBalance() : BigDecimal.ZERO;
    }
    
    public double getBalanceDouble(String accountName) {
        return getBalance(accountName).doubleValue();
    }
    
    public boolean hasEnough(String accountName, BigDecimal amount) {
        NonPlayerAccount account = getAccount(accountName);
        return account != null && account.hasEnough(amount);
    }
    
    public boolean deposit(String accountName, BigDecimal amount) {
        if (!enabled || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        NonPlayerAccount account = getOrCreateAccount(accountName);
        if (account == null) {
            return false;
        }
        
        BigDecimal maxBalance = com.oolonghoo.wooeco.util.MoneyFormat.getMaxBalance();
        if (account.getBalance().add(amount).compareTo(maxBalance) > 0) {
            return false;
        }
        
        account.deposit(amount);
        saveAccountAsync(account);
        return true;
    }
    
    public boolean withdraw(String accountName, BigDecimal amount) {
        if (!enabled || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        NonPlayerAccount account = getAccount(accountName);
        if (account == null || !account.hasEnough(amount)) {
            return false;
        }
        
        account.withdraw(amount);
        saveAccountAsync(account);
        return true;
    }
    
    public boolean setBalance(String accountName, BigDecimal amount) {
        if (!enabled || amount.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        
        NonPlayerAccount account = getOrCreateAccount(accountName);
        if (account == null) {
            return false;
        }
        
        BigDecimal maxBalance = com.oolonghoo.wooeco.util.MoneyFormat.getMaxBalance();
        if (amount.compareTo(maxBalance) > 0) {
            return false;
        }
        
        account.setBalance(amount);
        saveAccountAsync(account);
        return true;
    }
    
    public boolean deleteAccount(String accountName) {
        if (!enabled) {
            return false;
        }
        
        try {
            accountDAO.deleteAccount(accountName);
            cache.remove(accountName);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("删除非玩家账户失败: " + e.getMessage());
            return false;
        }
    }
    
    public List<NonPlayerAccount> getAllAccounts() {
        if (!enabled) {
            return List.of();
        }
        
        return AsyncUtils.supplyAsyncWithTimeout(() -> {
            try {
                return accountDAO.getAllAccounts();
            } catch (SQLException e) {
                plugin.getLogger().severe("获取所有非玩家账户失败: " + e.getMessage());
                return List.<NonPlayerAccount>of();
            }
        }, List.of());
    }
    
    public int getAccountCount() {
        if (!enabled) {
            return 0;
        }
        
        return AsyncUtils.supplyAsyncWithTimeout(() -> {
            try {
                return accountDAO.countAccounts();
            } catch (SQLException e) {
                return cache.size();
            }
        }, cache.size());
    }
    
    public void saveAccount(NonPlayerAccount account) {
        if (account == null || !account.isDirty()) {
            return;
        }
        
        try {
            accountDAO.saveOrUpdateAccount(account);
        } catch (SQLException e) {
            plugin.getLogger().severe("保存非玩家账户失败: " + e.getMessage());
        }
    }
    
    public void saveAccountAsync(NonPlayerAccount account) {
        ThreadUtils.runSmart(() -> saveAccount(account));
    }
    
    public void saveAll() {
        for (NonPlayerAccount account : cache.values()) {
            if (account.isDirty()) {
                saveAccount(account);
            }
        }
    }
    
    public void clearCache() {
        saveAll();
        cache.clear();
    }
    
    public void removeFromCache(String accountName) {
        NonPlayerAccount account = cache.remove(accountName);
        if (account != null && account.isDirty()) {
            saveAccount(account);
        }
    }
    
    public Map<String, NonPlayerAccount> getCache() {
        return Map.copyOf(cache);
    }
    
    public List<String> getWhitelistFields() {
        return List.copyOf(whitelistFields);
    }
    
    public boolean isWhitelistEnabled() {
        return whitelistEnabled;
    }
}
