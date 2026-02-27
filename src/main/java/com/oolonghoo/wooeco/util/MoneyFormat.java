package com.oolonghoo.wooeco.util;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.config.CurrencyConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 金额格式化工具类
 * 使用 BigDecimal 确保精度
 * 
 * @author oolongho
 */
public class MoneyFormat {
    
    private static WooEco plugin;
    private static boolean integerBalance = false;
    private static int decimalPlaces = 2;
    private static RoundingMode roundingMode = RoundingMode.DOWN;
    private static String thousandsSeparator = ",";
    private static String displayFormat = "%balance% %currencyname%";
    private static String singularName = "金币";
    private static String pluralName = "金币";
    private static boolean formatBalanceEnabled = false;
    private static TreeMap<Double, String> formatThresholds = new TreeMap<>();
    
    private static DecimalFormat decimalFormat;
    
    public static void initialize(WooEco wooEco) {
        plugin = wooEco;
        loadConfig();
    }
    
    public static void loadConfig() {
        CurrencyConfig config = plugin.getCurrencyConfig();
        integerBalance = config.isIntegerBalance();
        decimalPlaces = config.getDecimalPlaces();
        thousandsSeparator = config.getThousandsSeparator();
        displayFormat = config.getDisplayFormat();
        singularName = config.getSingularName();
        pluralName = config.getPluralName();
        formatBalanceEnabled = config.isFormatBalanceEnabled();
        formatThresholds = config.getFormatBalanceThresholds();
        
        int mode = config.getRoundingMode();
        roundingMode = switch (mode) {
            case 1 -> RoundingMode.UP;
            case 2 -> RoundingMode.HALF_UP;
            default -> RoundingMode.DOWN;
        };
        
        updateDecimalFormat();
    }
    
    private static void updateDecimalFormat() {
        StringBuilder pattern = new StringBuilder();
        if (!",".equals(thousandsSeparator)) {
            pattern.append("#,##0");
        } else {
            pattern.append("#,##0");
        }
        
        if (!integerBalance && decimalPlaces > 0) {
            pattern.append(".");
            for (int i = 0; i < decimalPlaces; i++) {
                pattern.append("0");
            }
        }
        
        decimalFormat = new DecimalFormat(pattern.toString(), DecimalFormatSymbols.getInstance(Locale.US));
        
        if (!",".equals(thousandsSeparator)) {
            decimalFormat.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
        }
    }
    
    public static BigDecimal formatInput(double amount) {
        return formatInput(BigDecimal.valueOf(amount));
    }
    
    public static BigDecimal formatInput(BigDecimal amount) {
        int places = integerBalance ? 0 : decimalPlaces;
        return amount.setScale(places, roundingMode);
    }
    
    public static BigDecimal formatInput(String amount) {
        try {
            return formatInput(new BigDecimal(amount));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
    
    public static String format(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        
        amount = formatInput(amount);
        double value = amount.doubleValue();
        
        if (formatBalanceEnabled) {
            for (Map.Entry<Double, String> entry : formatThresholds.entrySet()) {
                if (value >= entry.getKey()) {
                    double divisor = entry.getKey();
                    double abbreviated = value / divisor;
                    String suffix = entry.getValue();
                    
                    String formatted;
                    if (integerBalance) {
                        formatted = String.format("%.0f", abbreviated);
                    } else {
                        int abbrPlaces = Math.min(decimalPlaces, 2);
                        formatted = String.format("%." + abbrPlaces + "f", abbreviated);
                    }
                    return formatted + suffix;
                }
            }
        }
        
        if (integerBalance) {
            return String.format("%.0f", value);
        }
        
        String formatted = decimalFormat.format(value);
        if (!",".equals(thousandsSeparator)) {
            formatted = formatted.replace(",", thousandsSeparator);
        }
        
        return formatted;
    }
    
    public static String format(double amount) {
        return format(BigDecimal.valueOf(amount));
    }
    
    public static String formatDisplay(BigDecimal amount) {
        String balanceStr = format(amount);
        String currencyName = amount != null && amount.compareTo(BigDecimal.ONE) == 0 ? singularName : pluralName;
        
        return displayFormat
            .replace("%balance%", balanceStr)
            .replace("%currencyname%", currencyName);
    }
    
    public static String formatDisplay(double amount) {
        return formatDisplay(BigDecimal.valueOf(amount));
    }
    
    public static boolean isMax(BigDecimal amount) {
        BigDecimal max = BigDecimal.valueOf(plugin.getCurrencyConfig().getMaxBalance());
        return amount.compareTo(max) >= 0;
    }
    
    public static boolean isNegative(BigDecimal amount) {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }
    
    public static boolean isZero(BigDecimal amount) {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }
    
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return formatInput(a.add(b));
    }
    
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return formatInput(a.subtract(b));
    }
    
    public static BigDecimal multiply(BigDecimal a, BigDecimal multiplier) {
        return formatInput(a.multiply(multiplier));
    }
    
    public static BigDecimal divide(BigDecimal a, BigDecimal divisor) {
        return formatInput(a.divide(divisor, decimalPlaces, roundingMode));
    }
    
    public static BigDecimal getStartingBalance() {
        return BigDecimal.valueOf(plugin.getCurrencyConfig().getStartingBalance());
    }
    
    public static BigDecimal getMaxBalance() {
        return BigDecimal.valueOf(plugin.getCurrencyConfig().getMaxBalance());
    }
    
    public static String getSingularName() {
        return singularName;
    }
    
    public static String getPluralName() {
        return pluralName;
    }
}
