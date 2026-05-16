package com.oolonghoo.wooeco.model;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 离线交易提示模型
 *
 */
public class OfflineTransferTip {

    private final long id;
    private final UUID receiverUuid;
    private final String senderName;
    private final BigDecimal amount;
    private final long timestamp;
    private final AtomicBoolean notified;

    public OfflineTransferTip(long id, UUID receiverUuid, String senderName,
                               BigDecimal amount, long timestamp, boolean notified) {
        this.id = id;
        this.receiverUuid = receiverUuid;
        this.senderName = senderName;
        this.amount = amount;
        this.timestamp = timestamp;
        this.notified = new AtomicBoolean(notified);
    }

    public OfflineTransferTip(UUID receiverUuid, String senderName, BigDecimal amount) {
        this.id = -1;
        this.receiverUuid = receiverUuid;
        this.senderName = senderName;
        this.amount = amount;
        this.timestamp = System.currentTimeMillis();
        this.notified = new AtomicBoolean(false);
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

    public BigDecimal getAmount() {
        return amount;
    }

    public double getAmountDouble() {
        return amount.doubleValue();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isNotified() {
        return notified.get();
    }

    public void setNotified(boolean notified) {
        this.notified.set(notified);
    }
}
