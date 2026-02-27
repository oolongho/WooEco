package com.oolonghoo.wooeco.model;

import java.util.UUID;

/**
 * 离线交易提示模型
 * 
 * @author oolongho
 */
public class OfflineTransferTip {
    
    private final long id;
    private final UUID receiverUuid;
    private final String senderName;
    private final double amount;
    private final long timestamp;
    private boolean notified;
    
    public OfflineTransferTip(long id, UUID receiverUuid, String senderName, 
                               double amount, long timestamp, boolean notified) {
        this.id = id;
        this.receiverUuid = receiverUuid;
        this.senderName = senderName;
        this.amount = amount;
        this.timestamp = timestamp;
        this.notified = notified;
    }
    
    public OfflineTransferTip(UUID receiverUuid, String senderName, double amount) {
        this.id = -1;
        this.receiverUuid = receiverUuid;
        this.senderName = senderName;
        this.amount = amount;
        this.timestamp = System.currentTimeMillis();
        this.notified = false;
    }
    
    public long getId() {
        return id;
    }
    
    public UUID getReceiverUuid() {
        return receiverUuid;
    }
    
    public String getSenderName() {
        return senderName;
    }
    
    public double getAmount() {
        return amount;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public boolean isNotified() {
        return notified;
    }
    
    public void setNotified(boolean notified) {
        this.notified = notified;
    }
}
