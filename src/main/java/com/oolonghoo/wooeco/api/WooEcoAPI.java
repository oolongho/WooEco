package com.oolonghoo.wooeco.api;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.manager.EconomyManager;
import com.oolonghoo.wooeco.manager.TransactionManager;
import com.oolonghoo.wooeco.model.PlayerAccount;

import java.util.List;
import java.util.UUID;

/**
 * WooEco API接口
 * 
 * @author oolongho
 */
public class WooEcoAPI {
    
    private static WooEco instance;
    
    public static void initialize(WooEco plugin) {
        instance = plugin;
    }
    
    public static WooEco getInstance() {
        return instance;
    }
    
    public static boolean isLoaded() {
        return instance != null && instance.isEnabled();
    }
    
    public static double getBalance(UUID uuid) {
        checkLoaded();
        return instance.getEconomyManager().getBalance(uuid);
    }
    
    public static boolean has(UUID uuid, double amount) {
        checkLoaded();
        return instance.getEconomyManager().has(uuid, amount);
    }
    
    public static EconomyManager.EconomyResult withdraw(UUID uuid, double amount) {
        checkLoaded();
        return instance.getEconomyManager().withdraw(uuid, amount);
    }
    
    public static EconomyManager.EconomyResult deposit(UUID uuid, double amount) {
        checkLoaded();
        return instance.getEconomyManager().deposit(uuid, amount);
    }
    
    public static EconomyManager.EconomyResult set(UUID uuid, double amount) {
        checkLoaded();
        return instance.getEconomyManager().set(uuid, amount);
    }
    
    public static PlayerAccount getAccount(UUID uuid) {
        checkLoaded();
        return instance.getPlayerDataManager().getAccount(uuid);
    }
    
    public static boolean hasAccount(UUID uuid) {
        checkLoaded();
        return instance.getPlayerDataManager().getAccount(uuid) != null;
    }
    
    public static void createAccount(UUID uuid, String name) {
        checkLoaded();
        instance.getPlayerDataManager().createNewAccount(uuid, name);
    }
    
    public static TransactionManager.TransactionResult transfer(UUID from, UUID to, double amount) {
        checkLoaded();
        return instance.getTransactionManager().transfer(from, to, amount);
    }
    
    public static double getDailyIncome(UUID uuid) {
        checkLoaded();
        return instance.getEconomyManager().getDailyIncome(uuid);
    }
    
    public static String getCurrencyName() {
        checkLoaded();
        return instance.getCurrencyConfig().getName();
    }
    
    public static String getCurrencySymbol() {
        checkLoaded();
        return instance.getCurrencyConfig().getSymbol();
    }
    
    public static String format(double amount) {
        checkLoaded();
        return instance.getCurrencyConfig().format(amount);
    }
    
    public static String formatWithColor(double amount) {
        checkLoaded();
        return instance.getCurrencyConfig().formatWithColor(amount);
    }
    
    public static List<PlayerAccount> getTopBalances(int limit) {
        checkLoaded();
        return instance.getLeaderboardManager().getBalanceTop(1, limit);
    }
    
    public static List<PlayerAccount> getTopIncomes(int limit) {
        checkLoaded();
        return instance.getLeaderboardManager().getIncomeTop(1, limit);
    }
    
    private static void checkLoaded() {
        if (!isLoaded()) {
            throw new IllegalStateException("WooEco is not loaded!");
        }
    }
}
