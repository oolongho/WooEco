package me.yic.xconomy.api.event;

import java.math.BigDecimal;

public class NonPlayerAccountEvent extends AccountEvent {

    public NonPlayerAccountEvent(String accountName, BigDecimal balance,
                                  BigDecimal amount, Boolean isAdd, String method) {
        super(accountName, balance, amount, isAdd, method);
    }
}
