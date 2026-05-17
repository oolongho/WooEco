package com.oolonghoo.wooeco.model;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class NonPlayerAccount {
    
    private final String accountName;
    private volatile BigDecimal balance;
    private final AtomicLong createdAt;
    private final AtomicLong updatedAt;
    private final AtomicBoolean dirty;
    
    public NonPlayerAccount(String accountName) {
        this.accountName = accountName;
        this.balance = BigDecimal.ZERO;
        this.createdAt = new AtomicLong(System.currentTimeMillis());
        this.updatedAt = new AtomicLong(System.currentTimeMillis());
        this.dirty = new AtomicBoolean(false);
    }
    
    public NonPlayerAccount(String accountName, double balance, long createdAt, long updatedAt) {
        this(accountName, BigDecimal.valueOf(balance), createdAt, updatedAt);
    }
    
    public NonPlayerAccount(String accountName, BigDecimal balance, long createdAt, long updatedAt) {
        this.accountName = accountName;
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.createdAt = new AtomicLong(createdAt);
        this.updatedAt = new AtomicLong(updatedAt);
        this.dirty = new AtomicBoolean(false);
    }
    
    public String getAccountName() {
        return accountName;
    }
    
    public BigDecimal getBalance() {
        synchronized (this) {
            return balance;
        }
    }
    
    public double getBalanceDouble() {
        return getBalance().doubleValue();
    }
    
    public void setBalance(BigDecimal newBalance) {
        synchronized (this) {
            this.balance = newBalance;
        }
        this.updatedAt.set(System.currentTimeMillis());
        this.dirty.set(true);
    }
    
    public void setBalance(double newBalance) {
        setBalance(BigDecimal.valueOf(newBalance));
    }
    
    public void deposit(BigDecimal amount) {
        synchronized (this) {
            BigDecimal newBalance = this.balance.add(amount);
            this.balance = newBalance;
        }
        this.updatedAt.set(System.currentTimeMillis());
        this.dirty.set(true);
    }
    
    public void withdraw(BigDecimal amount) {
        synchronized (this) {
            BigDecimal newBalance = this.balance.subtract(amount);
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                newBalance = BigDecimal.ZERO;
            }
            this.balance = newBalance;
        }
        this.updatedAt.set(System.currentTimeMillis());
        this.dirty.set(true);
    }
    
    public boolean hasEnough(BigDecimal amount) {
        synchronized (this) {
            return this.balance.compareTo(amount) >= 0;
        }
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
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        NonPlayerAccount that = (NonPlayerAccount) obj;
        return accountName.equals(that.accountName);
    }
    
    @Override
    public int hashCode() {
        return accountName.hashCode();
    }
    
    @Override
    public String toString() {
        return "NonPlayerAccount{" +
                "accountName='" + accountName + '\'' +
                ", balance=" + balance +
                '}';
    }
}
