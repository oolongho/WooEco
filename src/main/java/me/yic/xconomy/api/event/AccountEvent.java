package me.yic.xconomy.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;

public class AccountEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final String accountName;
    private final BigDecimal balance;
    private final BigDecimal amount;
    private final Boolean isAdd;
    private final String method;

    public AccountEvent(String accountName, BigDecimal balance, BigDecimal amount, Boolean isAdd, String method) {
        this.accountName = accountName;
        this.balance = balance;
        this.amount = amount;
        this.isAdd = isAdd;
        this.method = method;
    }

    public String getaccountname() {
        return accountName;
    }

    public BigDecimal getbalance() {
        return balance;
    }

    public BigDecimal getamount() {
        return amount;
    }

    public Boolean getisadd() {
        return isAdd;
    }

    public String getmethod() {
        return method;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
