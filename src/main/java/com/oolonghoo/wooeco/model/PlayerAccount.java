package com.oolonghoo.wooeco.model;

import com.oolonghoo.wooeco.util.MoneyFormat;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 玩家账户模型 (线程安全)
 * 使用 BigDecimal 确保金额精度
 * 
 * @author oolongho
 */
public class PlayerAccount {
    
    private final UUID uuid;
    private volatile String playerName;
    private volatile BigDecimal balance;
    private volatile BigDecimal dailyIncome;
    private final AtomicLong lastIncomeReset;
    private final AtomicLong createdAt;
    private final AtomicLong updatedAt;
    private final AtomicBoolean dirty;
    private final Object balanceLock = new Object();
    
    public PlayerAccount(UUID uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.balance = BigDecimal.ZERO;
        this.dailyIncome = BigDecimal.ZERO;
        this.lastIncomeReset = new AtomicLong(System.currentTimeMillis());
        this.createdAt = new AtomicLong(System.currentTimeMillis());
        this.updatedAt = new AtomicLong(System.currentTimeMillis());
        this.dirty = new AtomicBoolean(false);
    }
    
    public PlayerAccount(UUID uuid, String playerName, double balance, double dailyIncome, 
                         long lastIncomeReset, long createdAt, long updatedAt) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.balance = MoneyFormat.formatInput(balance);
        this.dailyIncome = MoneyFormat.formatInput(dailyIncome);
        this.lastIncomeReset = new AtomicLong(lastIncomeReset);
        this.createdAt = new AtomicLong(createdAt);
        this.updatedAt = new AtomicLong(updatedAt);
        this.dirty = new AtomicBoolean(false);
    }
    
    public PlayerAccount(UUID uuid, String playerName, BigDecimal balance, BigDecimal dailyIncome, 
                         long lastIncomeReset, long createdAt, long updatedAt) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.dailyIncome = dailyIncome != null ? dailyIncome : BigDecimal.ZERO;
        this.lastIncomeReset = new AtomicLong(lastIncomeReset);
        this.createdAt = new AtomicLong(createdAt);
        this.updatedAt = new AtomicLong(updatedAt);
        this.dirty = new AtomicBoolean(false);
    }
    
    public UUID getUuid() {
        return uuid;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public synchronized void setPlayerName(String playerName) {
        this.playerName = playerName;
        this.dirty.set(true);
    }
    
    public BigDecimal getBalance() {
        synchronized (balanceLock) {
            return balance;
        }
    }
    
    public double getBalanceDouble() {
        return getBalance().doubleValue();
    }
    
    public void setBalance(BigDecimal newBalance) {
        synchronized (balanceLock) {
            this.balance = MoneyFormat.formatInput(newBalance);
        }
        this.updatedAt.set(System.currentTimeMillis());
        this.dirty.set(true);
    }
    
    public void setBalance(double newBalance) {
        setBalance(BigDecimal.valueOf(newBalance));
    }
    
    public BigDecimal getDailyIncome() {
        synchronized (balanceLock) {
            return dailyIncome;
        }
    }
    
    public double getDailyIncomeDouble() {
        return getDailyIncome().doubleValue();
    }
    
    public void setDailyIncome(BigDecimal income) {
        synchronized (balanceLock) {
            this.dailyIncome = MoneyFormat.formatInput(income);
        }
        this.dirty.set(true);
    }
    
    public void setDailyIncome(double income) {
        setDailyIncome(BigDecimal.valueOf(income));
    }
    
    public void addDailyIncome(BigDecimal amount) {
        synchronized (balanceLock) {
            this.dailyIncome = MoneyFormat.formatInput(this.dailyIncome.add(amount));
        }
        this.dirty.set(true);
    }
    
    public void addDailyIncome(double amount) {
        addDailyIncome(BigDecimal.valueOf(amount));
    }
    
    public long getLastIncomeReset() {
        return lastIncomeReset.get();
    }
    
    public void setLastIncomeReset(long time) {
        this.lastIncomeReset.set(time);
        this.dirty.set(true);
    }
    
    public long getCreatedAt() {
        return createdAt.get();
    }
    
    public long getUpdatedAt() {
        return updatedAt.get();
    }
    
    public boolean isDirty() {
        return dirty.get();
    }
    
    public void markSaved() {
        this.dirty.set(false);
        this.updatedAt.set(System.currentTimeMillis());
    }
}
