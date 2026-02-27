package com.oolonghoo.wooeco.model;

import java.util.UUID;

/**
 * 交易记录模型
 * 
 * @author oolongho
 */
public class Transaction {
    
    private final long id;
    private final UUID senderUuid;
    private final String senderName;
    private final UUID receiverUuid;
    private final String receiverName;
    private final double amount;
    private final double tax;
    private final long timestamp;
    
    public Transaction(long id, UUID senderUuid, String senderName, UUID receiverUuid, 
                       String receiverName, double amount, double tax, long timestamp) {
        this.id = id;
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.receiverUuid = receiverUuid;
        this.receiverName = receiverName;
        this.amount = amount;
        this.tax = tax;
        this.timestamp = timestamp;
    }
    
    public Transaction(UUID senderUuid, String senderName, UUID receiverUuid, 
                       String receiverName, double amount, double tax) {
        this.id = -1;
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.receiverUuid = receiverUuid;
        this.receiverName = receiverName;
        this.amount = amount;
        this.tax = tax;
        this.timestamp = System.currentTimeMillis();
    }
    
    public long getId() {
        return id;
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
    
    public double getTax() {
        return tax;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
}
