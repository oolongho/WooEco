package com.oolonghoo.wooeco.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 交易记录模型
 *
 */
public class Transaction {

    private final long id;
    private final UUID senderUuid;
    private final String senderName;
    private final UUID receiverUuid;
    private final String receiverName;
    private final BigDecimal amount;
    private final BigDecimal tax;
    private final long timestamp;

    public Transaction(long id, UUID senderUuid, String senderName, UUID receiverUuid,
                       String receiverName, BigDecimal amount, BigDecimal tax, long timestamp) {
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
                       String receiverName, BigDecimal amount, BigDecimal tax) {
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

    public BigDecimal getAmount() {
        return amount;
    }

    public double getAmountDouble() {
        return amount.doubleValue();
    }

    public BigDecimal getTaxDecimal() {
        return tax;
    }

    public double getTax() {
        return tax.doubleValue();
    }

    public long getTimestamp() {
        return timestamp;
    }
}
