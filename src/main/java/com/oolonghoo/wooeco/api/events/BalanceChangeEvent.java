package com.oolonghoo.wooeco.api.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 余额变更事件
 * 
 * @author oolongho
 */
public class BalanceChangeEvent extends Event implements Cancellable {
    
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final UUID playerUUID;
    private final double oldBalance;
    private double newBalance;
    private final double amount;
    private final BalanceChangeReason reason;
    private boolean cancelled;
    
    public BalanceChangeEvent(UUID playerUUID, double oldBalance, double newBalance, 
                               double amount, BalanceChangeReason reason) {
        this.playerUUID = playerUUID;
        this.oldBalance = oldBalance;
        this.newBalance = newBalance;
        this.amount = amount;
        this.reason = reason;
        this.cancelled = false;
    }
    
    public UUID getPlayerUUID() {
        return playerUUID;
    }
    
    public double getOldBalance() {
        return oldBalance;
    }
    
    public double getNewBalance() {
        return newBalance;
    }
    
    public void setNewBalance(double newBalance) {
        this.newBalance = newBalance;
    }
    
    public double getAmount() {
        return amount;
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
