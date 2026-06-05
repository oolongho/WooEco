package me.yic.xconomy.api.event;

import java.math.BigDecimal;
import java.util.UUID;

public class PlayerAccountEvent extends AccountEvent {

    private final UUID uuid;
    private final String reason;

    public PlayerAccountEvent(UUID uuid, String accountName, BigDecimal balance,
                               BigDecimal amount, Boolean isAdd, String method, String reason) {
        super(accountName, balance, amount, isAdd, method);
        this.uuid = uuid;
        this.reason = reason;
    }

    public UUID getUniqueId() {
        return uuid;
    }

    public String getreason() {
        return reason;
    }
}
