package com.oolonghoo.wooeco.api.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;
import java.util.UUID;

public class BalanceChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUUID;
    private final double oldBalance;
    private double newBalance;
    private final double amount;
    private final BalanceChangeReason reason;
    private boolean cancelled;
    private final BigDecimal oldBalanceDecimal;
    private BigDecimal newBalanceDecimal;
    private final BigDecimal amountDecimal;

    public BalanceChangeEvent(UUID playerUUID, double oldBalance, double newBalance,
                               double amount, BalanceChangeReason reason) {
        this.playerUUID = playerUUID;
        this.oldBalance = oldBalance;
        this.newBalance = newBalance;
        this.amount = amount;
        this.reason = reason;
        this.cancelled = false;
        this.oldBalanceDecimal = BigDecimal.valueOf(oldBalance);
        this.newBalanceDecimal = BigDecimal.valueOf(newBalance);
        this.amountDecimal = BigDecimal.valueOf(amount);
    }

    public BalanceChangeEvent(UUID playerUUID, BigDecimal oldBalance, BigDecimal newBalance,
                               BigDecimal amount, BalanceChangeReason reason) {
        this.playerUUID = playerUUID;
        this.oldBalance = oldBalance.doubleValue();
        this.newBalance = newBalance.doubleValue();
        this.amount = amount.doubleValue();
        this.reason = reason;
        this.cancelled = false;
        this.oldBalanceDecimal = oldBalance;
        this.newBalanceDecimal = newBalance;
        this.amountDecimal = amount;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public double getOldBalance() {
        return oldBalance;
    }

    public BigDecimal getOldBalanceDecimal() {
        return oldBalanceDecimal;
    }

    public double getNewBalance() {
        return newBalance;
    }

    public void setNewBalance(double newBalance) {
        if (Double.isNaN(newBalance) || Double.isInfinite(newBalance)) {
            throw new IllegalArgumentException("余额不能为NaN或Infinity");
        }
        if (newBalance < 0) {
            throw new IllegalArgumentException("余额不能为负数");
        }
        this.newBalance = newBalance;
        this.newBalanceDecimal = BigDecimal.valueOf(newBalance);
    }

    public BigDecimal getNewBalanceDecimal() {
        return newBalanceDecimal;
    }

    public void setNewBalanceDecimal(BigDecimal newBalance) {
        if (newBalance == null || newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("余额不能为负数");
        }
        this.newBalanceDecimal = newBalance;
        this.newBalance = newBalance.doubleValue();
    }

    public double getAmount() {
        return amount;
    }

    public BigDecimal getAmountDecimal() {
        return amountDecimal;
    }

    public BalanceChangeReason getReason() {
        return reason;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
