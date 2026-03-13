package com.oolonghoo.wooeco.api.events;

/**
 * 余额变更原因枚举
 * 
 */
public enum BalanceChangeReason {
    ADMIN,
    PAYMENT,
    PAYMENT_RECEIVED,
    TAX,
    PLUGIN,
    OTHER
}
