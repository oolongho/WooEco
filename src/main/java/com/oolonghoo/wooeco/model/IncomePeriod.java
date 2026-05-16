package com.oolonghoo.wooeco.model;

import java.util.Locale;

public enum IncomePeriod {
    DAY,
    WEEK,
    MONTH;

    public static IncomePeriod fromString(String str) {
        if (str == null) return null;
        String lower = str.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "day":
            case "d":
            case "日":
            case "今日":
            case "今天":
                return DAY;
            case "week":
            case "w":
            case "周":
            case "本周":
            case "这周":
                return WEEK;
            case "month":
            case "m":
            case "月":
            case "本月":
            case "这个月":
                return MONTH;
            default:
                return null;
        }
    }

    public static boolean isPeriodKeyword(String str) {
        return fromString(str) != null;
    }
}
