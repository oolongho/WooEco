package com.oolonghoo.wooeco.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置验证器
 * 验证配置文件的有效性
 * 
 */
public class ConfigValidator {
    
    private final JavaPlugin plugin;
    private final List<String> errors;
    private final List<String> warnings;
    private boolean hasErrors;
    
    public ConfigValidator(JavaPlugin plugin) {
        this.plugin = plugin;
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.hasErrors = false;
    }
    
    public boolean validate(FileConfiguration config) {
        errors.clear();
        warnings.clear();
        hasErrors = false;
        
        validateSettings(config);
        validateCurrency(config);
        validateTransaction(config);
        validateDatabase(config);
        validatePerformance(config);
        validateCommandCooldown(config);
        
        if (!errors.isEmpty()) {
            plugin.getLogger().warning("========== 配置验证错误 ==========");
            for (String error : errors) {
                plugin.getLogger().warning(error);
            }
            plugin.getLogger().warning("=================================");
        }
        
        if (!warnings.isEmpty()) {
            plugin.getLogger().info("========== 配置验证警告 ==========");
            for (String warning : warnings) {
                plugin.getLogger().info(warning);
            }
            plugin.getLogger().info("=================================");
        }
        
        return !hasErrors;
    }
    
    private void validateSettings(FileConfiguration config) {
        int autoSaveInterval = config.getInt("settings.auto-save-interval", 300);
        if (autoSaveInterval < 60) {
            warnings.add("settings.auto-save-interval 建议至少为60秒，当前为: " + autoSaveInterval);
        }
        
        String uuidMode = config.getString("settings.uuid-mode", "Default");
        if (!uuidMode.equals("Default") && !uuidMode.equals("Online") && !uuidMode.equals("Offline")) {
            errors.add("settings.uuid-mode 无效值: " + uuidMode + "，应为 Default/Online/Offline");
            hasErrors = true;
        }
    }
    
    private void validateCurrency(FileConfiguration config) {
        double startingBalance = config.getDouble("currency.starting-balance", 0);
        if (startingBalance < 0) {
            errors.add("currency.starting-balance 不能为负数: " + startingBalance);
            hasErrors = true;
        }
        
        double maxBalance = config.getDouble("currency.max-balance", 10000000000000000.0);
        if (maxBalance <= 0) {
            errors.add("currency.max-balance 必须大于0: " + maxBalance);
            hasErrors = true;
        }
        
        if (startingBalance > maxBalance) {
            errors.add("currency.starting-balance 不能大于 currency.max-balance");
            hasErrors = true;
        }
        
        int decimalPlaces = config.getInt("currency.format.decimal-places", 2);
        if (decimalPlaces < 0 || decimalPlaces > 10) {
            warnings.add("currency.format.decimal-places 建议在0-10之间，当前为: " + decimalPlaces);
        }
        
        int roundingMode = config.getInt("currency.rounding-mode", 2);
        if (roundingMode < 0 || roundingMode > 2) {
            errors.add("currency.rounding-mode 无效值: " + roundingMode + "，应为 0(向下)/1(向上)/2(四舍五入)");
            hasErrors = true;
        }
    }
    
    private void validateTransaction(FileConfiguration config) {
        double minAmount = config.getDouble("transaction.min-amount", 1);
        double maxAmount = config.getDouble("transaction.max-amount", 1000000);
        
        if (minAmount <= 0) {
            errors.add("transaction.min-amount 必须大于0: " + minAmount);
            hasErrors = true;
        }
        
        if (maxAmount <= 0) {
            errors.add("transaction.max-amount 必须大于0: " + maxAmount);
            hasErrors = true;
        }
        
        if (minAmount > maxAmount) {
            errors.add("transaction.min-amount 不能大于 transaction.max-amount");
            hasErrors = true;
        }
        
        int taxRate = config.getInt("transaction.tax.rate", 5);
        if (taxRate < 0 || taxRate > 100) {
            errors.add("transaction.tax.rate 必须在0-100之间: " + taxRate);
            hasErrors = true;
        }
    }
    
    private void validateDatabase(FileConfiguration config) {
        String type = config.getString("database.type", "SQLite");
        if (!type.equals("SQLite") && !type.equals("MySQL")) {
            errors.add("database.type 无效值: " + type + "，应为 SQLite 或 MySQL");
            hasErrors = true;
        }
        
        if (type.equals("MySQL")) {
            String host = config.getString("database.mysql.host", "");
            int port = config.getInt("database.mysql.port", 3306);
            String database = config.getString("database.mysql.database", "");
            
            if (host.isEmpty()) {
                errors.add("database.mysql.host 不能为空");
                hasErrors = true;
            }
            
            if (port <= 0 || port > 65535) {
                errors.add("database.mysql.port 无效端口号: " + port);
                hasErrors = true;
            }
            
            if (database.isEmpty()) {
                errors.add("database.mysql.database 不能为空");
                hasErrors = true;
            }
            
            int poolSize = config.getInt("database.mysql.pool-size", 10);
            if (poolSize < 1 || poolSize > 100) {
                warnings.add("database.mysql.pool-size 建议在1-100之间，当前为: " + poolSize);
            }
            
            String tablePrefix = config.getString("database.mysql.table-prefix", "wooeco_");
            if (tablePrefix != null && !tablePrefix.matches("^[a-zA-Z0-9_]{1,32}$")) {
                warnings.add("database.mysql.table-prefix 建议仅使用字母、数字、下划线，长度1-32，无效字符将被移除");
            }
        }
        
        int autoSave = config.getInt("database.auto-save", 60);
        if (autoSave < 30) {
            warnings.add("database.auto-save 建议至少为30秒，当前为: " + autoSave);
        }
    }
    
    private void validatePerformance(FileConfiguration config) {
        int asyncTimeout = config.getInt("performance.async-timeout", 3);
        
        int historyPerPage = config.getInt("history.per-page", 10);
        if (historyPerPage < 1 || historyPerPage > 50) {
            warnings.add("history.per-page 建议在1-50之间，当前为: " + historyPerPage);
        }
        if (asyncTimeout < 1 || asyncTimeout > 30) {
            warnings.add("performance.async-timeout 建议在1-30秒之间，当前为: " + asyncTimeout);
        }
        
        boolean disableCache = config.getBoolean("performance.disable-cache", false);
        if (disableCache) {
            warnings.add("performance.disable-cache 为 true，性能可能下降");
        }
    }
    
    private void validateCommandCooldown(FileConfiguration config) {
        boolean enabled = config.getBoolean("command-cooldown.enabled", true);
        if (!enabled) {
            return;
        }
        
        var cooldownsSection = config.getConfigurationSection("command-cooldown.cooldowns");
        if (cooldownsSection == null) {
            warnings.add("command-cooldown.cooldowns 未配置");
            return;
        }
        
        for (String key : cooldownsSection.getKeys(false)) {
            int cooldown = cooldownsSection.getInt(key, 0);
            if (cooldown < 0) {
                errors.add("command-cooldown.cooldowns." + key + " 不能为负数: " + cooldown);
                hasErrors = true;
            }
        }
    }
    
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }
    
    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }
    
    public boolean hasErrors() {
        return hasErrors;
    }
}
