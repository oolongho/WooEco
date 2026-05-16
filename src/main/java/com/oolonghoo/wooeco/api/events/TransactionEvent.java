package com.oolonghoo.wooeco.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;
import java.util.UUID;

public class TransactionEvent extends Event implements org.bukkit.event.Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID senderUuid;
    private final String senderName;
    private final UUID receiverUuid;
    private final String receiverName;
    private double amount;
    private double tax;
    private boolean cancelled = false;
    private BigDecimal amountDecimal;
    private BigDecimal taxDecimal;

    public TransactionEvent(UUID senderUuid, String senderName,
                            UUID receiverUuid, String receiverName,
                            double amount, double tax) {
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.receiverUuid = receiverUuid;
        this.receiverName = receiverName;
        this.amount = amount;
        this.tax = tax;
        this.amountDecimal = BigDecimal.valueOf(amount);
        this.taxDecimal = BigDecimal.valueOf(tax);
    }

    public TransactionEvent(UUID senderUuid, String senderName,
                            UUID receiverUuid, String receiverName,
                            BigDecimal amount, BigDecimal tax) {
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.receiverUuid = receiverUuid;
        this.receiverName = receiverName;
        this.amount = amount.doubleValue();
        this.tax = tax.doubleValue();
        this.amountDecimal = amount;
        this.taxDecimal = tax;
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
        this.amountDecimal = BigDecimal.valueOf(amount);
    }

    public BigDecimal getAmountDecimal() {
        return amountDecimal;
    }

    public void setAmountDecimal(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("金额不能为负数");
        }
        this.amountDecimal = amount;
        this.amount = amount.doubleValue();
    }

    public double getTax() {
        return tax;
    }

    public void setTax(double tax) {
        if (tax < 0) {
            throw new IllegalArgumentException("税费不能为负数");
        }
        this.tax = tax;
        this.taxDecimal = BigDecimal.valueOf(tax);
    }

    public BigDecimal getTaxDecimal() {
        return taxDecimal;
    }

    public void setTaxDecimal(BigDecimal tax) {
        if (tax == null || tax.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("税费不能为负数");
        }
        this.taxDecimal = tax;
        this.tax = tax.doubleValue();
    }

    public double getTotalCost() {
        return amount + tax;
    }

    public BigDecimal getTotalCostDecimal() {
        return amountDecimal.add(taxDecimal);
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
