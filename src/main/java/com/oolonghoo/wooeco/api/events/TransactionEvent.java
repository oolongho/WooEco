package com.oolonghoo.wooeco.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 交易事件
 * 当玩家之间进行转账时触发
 * 
 * @author oolongho
 */
public class TransactionEvent extends Event {
    
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final UUID senderUuid;
    private final String senderName;
    private final UUID receiverUuid;
    private final String receiverName;
    private double amount;
    private double tax;
    private boolean cancelled = false;
    
    public TransactionEvent(UUID senderUuid, String senderName, 
                            UUID receiverUuid, String receiverName,
                            double amount, double tax) {
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.receiverUuid = receiverUuid;
        this.receiverName = receiverName;
        this.amount = amount;
        this.tax = tax;
    }
    
    public UUID getSenderUuid() {
        return senderUuid;
    }
    
    public String getSenderName() {
        return senderName;
    }
    
    public UUID getReceiverUuid() {
        return receiverUuid;
    }
    
    public String getReceiverName() {
        return receiverName;
    }
    
    public double getAmount() {
        return amount;
    }
    
    public void setAmount(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("金额不能为负数");
        }
        this.amount = amount;
    }
    
    public double getTax() {
        return tax;
    }
    
    public void setTax(double tax) {
        if (tax < 0) {
            throw new IllegalArgumentException("税费不能为负数");
        }
        this.tax = tax;
    }
    
    public double getTotalCost() {
        return amount + tax;
    }
    
    public boolean isCancelled() {
        return cancelled;
    }
    
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
