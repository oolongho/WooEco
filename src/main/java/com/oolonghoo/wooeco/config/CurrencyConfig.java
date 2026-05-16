package com.oolonghoo.wooeco.config;

import com.oolonghoo.wooeco.WooEco;
import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 货币配置类
 * 
 */
public class CurrencyConfig {
    
    private final WooEco plugin;
    
    private String singularName;
    private String pluralName;
    private String symbol;
    private List<String> aliases;
    private double startingBalance;
    private double maxBalance;
    private String thousandsSeparator;
    private int decimalPlaces;
    private boolean integerBalance;
    private int roundingMode;
    private String displayFormat;
    private boolean formatBalanceEnabled;
    private TreeMap<Double, String> formatBalanceThresholds;
    
    public CurrencyConfig(WooEco plugin) {
        this.plugin = plugin;
        this.formatBalanceThresholds = new TreeMap<>(Collections.reverseOrder());
    }
    
    public void load() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("currency");
        if (section == null) {
            setDefaults();
            return;
        }
        
        this.singularName = section.getString("singular-name", "金币");
        this.pluralName = section.getString("plural-name", "金币");
        this.symbol = section.getString("symbol", "&e￥&r");
        this.aliases = section.getStringList("aliases");
        if (this.aliases.isEmpty()) {
            this.aliases = Arrays.asList("money", "bal", "coin");
        }
        this.startingBalance = section.getDouble("starting-balance", 0);
        this.maxBalance = section.getDouble("max-balance", 1e16);
        this.integerBalance = section.getBoolean("integer-balance", false);
        this.roundingMode = section.getInt("rounding-mode", 0);
        
        ConfigurationSection formatSection = section.getConfigurationSection("format");
        if (formatSection != null) {
            this.thousandsSeparator = formatSection.getString("thousands-separator", ",");
            this.decimalPlaces = formatSection.getInt("decimal-places", 2);
            this.displayFormat = formatSection.getString("display-format", "%balance% %currencyname%");
        } else {
            this.thousandsSeparator = ",";
            this.decimalPlaces = 2;
            this.displayFormat = "%balance% %currencyname%";
        }
        
        ConfigurationSection formatBalanceSection = section.getConfigurationSection("format-balance");
        this.formatBalanceEnabled = false;
        this.formatBalanceThresholds.clear();
        
        if (formatBalanceSection != null) {
            this.formatBalanceEnabled = formatBalanceSection.getBoolean("enabled", false);
            
            ConfigurationSection thresholdsSection = formatBalanceSection.getConfigurationSection("thresholds");
            if (thresholdsSection != null) {
                for (String key : thresholdsSection.getKeys(false)) {
                    try {
                        double threshold = Double.parseDouble(key);
                        String suffix = thresholdsSection.getString(key);
                        this.formatBalanceThresholds.put(threshold, suffix);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        
        if (this.formatBalanceThresholds.isEmpty()) {
            this.formatBalanceThresholds.put(1000.0, "k");
            this.formatBalanceThresholds.put(1000000.0, "m");
            this.formatBalanceThresholds.put(1000000000.0, "b");
        }
    }
    
    private void setDefaults() {
        this.singularName = "金币";
        this.pluralName = "金币";
        this.symbol = "&e￥&r";
        this.aliases = Arrays.asList("money", "bal", "coin");
        this.startingBalance = 0;
        this.maxBalance = 1e16;
        this.thousandsSeparator = ",";
        this.decimalPlaces = 2;
        this.integerBalance = false;
        this.roundingMode = 0;
        this.displayFormat = "%balance% %currencyname%";
        this.formatBalanceEnabled = false;
        this.formatBalanceThresholds = new TreeMap<>(Collections.reverseOrder());
        this.formatBalanceThresholds.put(1000.0, "k");
        this.formatBalanceThresholds.put(1000000.0, "m");
        this.formatBalanceThresholds.put(1000000000.0, "b");
    }
    
    public String format(BigDecimal amount) {
        RoundingMode rm = switch (roundingMode) {
            case 1 -> RoundingMode.UP;
            case 2 -> RoundingMode.HALF_UP;
            default -> RoundingMode.DOWN;
        };
        
        int places = integerBalance ? 0 : decimalPlaces;
        BigDecimal bd = amount.setScale(places, rm);
        
        if (formatBalanceEnabled) {
            for (Map.Entry<Double, String> entry : formatBalanceThresholds.entrySet()) {
                if (bd.compareTo(BigDecimal.valueOf(entry.getKey())) >= 0) {
                    BigDecimal divisor = BigDecimal.valueOf(entry.getKey());
                    int abbrPlaces = integerBalance ? 0 : Math.min(decimalPlaces, 2);
                    BigDecimal abbreviated = bd.divide(divisor, abbrPlaces, rm);
                    return abbreviated.setScale(abbrPlaces, rm).toPlainString() + entry.getValue();
                }
            }
        }
        
        if (integerBalance) {
            return bd.toPlainString();
        }
        
        String plain = bd.toPlainString();
        int dotIdx = plain.indexOf('.');
        String intPart = dotIdx >= 0 ? plain.substring(0, dotIdx) : plain;
        String decPart = dotIdx >= 0 ? plain.substring(dotIdx) : "";
        
        StringBuilder sb = new StringBuilder();
        int len = intPart.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) {
                sb.append(thousandsSeparator);
            }
            sb.append(intPart.charAt(i));
        }
        return sb.append(decPart).toString();
    }
    
    public String format(double amount) {
        if (integerBalance) {
            amount = Math.round(amount);
        }
        return format(BigDecimal.valueOf(amount));
    }
    
    public String formatWithColor(double amount) {
        return getSymbol() + format(amount);
    }
    
    public String formatDisplay(double amount) {
        String balanceStr = format(amount);
        String currencyName = Math.abs(amount - 1) < 0.001 ? singularName : pluralName;
        
        return displayFormat
            .replace("%balance%", balanceStr)
            .replace("%currencyname%", currencyName);
    }
    
    public String formatDisplayWithColor(double amount) {
        return MessageManager.translateColors(formatDisplay(amount));
    }
    
    public String getSingularName() {
        return singularName;
    }
    
    public String getPluralName() {
        return pluralName;
    }
    
    public String getName() {
        return singularName;
    }
    
    public String getNamePlural() {
        return pluralName;
    }
    
    public String getSymbol() {
        return MessageManager.translateColors(symbol);
    }
    
    public String getRawSymbol() {
        return symbol;
    }
    
    public List<String> getAliases() {
        return aliases;
    }
    
    public double getStartingBalance() {
        return startingBalance;
    }
    
    public double getMaxBalance() {
        return maxBalance;
    }
    
    public String getThousandsSeparator() {
        return thousandsSeparator;
    }
    
    public int getDecimalPlaces() {
        return decimalPlaces;
    }
    
    public boolean isIntegerBalance() {
        return integerBalance;
    }
    
    public int getRoundingMode() {
        return roundingMode;
    }
    
    public String getDisplayFormat() {
        return displayFormat;
    }
    
    public boolean isFormatBalanceEnabled() {
        return formatBalanceEnabled;
    }
    
    public BigDecimal formatInput(BigDecimal amount) {
        int places = integerBalance ? 0 : decimalPlaces;
        RoundingMode rm = switch (roundingMode) {
            case 1 -> RoundingMode.UP;
            case 2 -> RoundingMode.HALF_UP;
            default -> RoundingMode.DOWN;
        };
        return amount.setScale(places, rm);
    }

    public BigDecimal formatInput(double amount) {
        return formatInput(BigDecimal.valueOf(amount));
    }

    public BigDecimal getMaxBalanceBigDecimal() {
        return BigDecimal.valueOf(maxBalance);
    }

    public TreeMap<Double, String> getFormatBalanceThresholds() {
        return formatBalanceThresholds;
    }
}
