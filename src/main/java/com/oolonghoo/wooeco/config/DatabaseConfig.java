package com.oolonghoo.wooeco.config;

import com.oolonghoo.wooeco.WooEco;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 数据库配置类
 * 
 * @author oolongho
 */
public class DatabaseConfig {
    
    private final WooEco plugin;
    
    private DatabaseType type;
    private String sqliteFile;
    
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUser;
    private String mysqlPassword;
    private int mysqlPoolSize;
    private String mysqlTablePrefix;
    
    private boolean syncEnabled;
    private String redisHost;
    private int redisPort;
    private String redisPassword;
    private String redisChannel;
    private String serverId;
    
    private UUIDMode uuidMode;
    private boolean usernameIgnoreCase;
    
    public enum DatabaseType {
        SQLITE,
        MYSQL
    }
    
    public DatabaseConfig(WooEco plugin) {
        this.plugin = plugin;
    }
    
    public void load() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("database");
        if (section == null) {
            setDefaults();
            return;
        }
        
        String typeStr = section.getString("type", "SQLite").toUpperCase();
        try {
            this.type = DatabaseType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            this.type = DatabaseType.SQLITE;
        }
        
        this.sqliteFile = section.getString("file", "data.db");
        
        ConfigurationSection mysqlSection = section.getConfigurationSection("mysql");
        if (mysqlSection != null) {
            this.mysqlHost = mysqlSection.getString("host", "localhost");
            this.mysqlPort = mysqlSection.getInt("port", 3306);
            this.mysqlDatabase = mysqlSection.getString("database", "wooeco");
            this.mysqlUser = mysqlSection.getString("user", "root");
            this.mysqlPassword = mysqlSection.getString("password", "");
            this.mysqlPoolSize = mysqlSection.getInt("pool-size", 10);
            this.mysqlTablePrefix = mysqlSection.getString("table-prefix", "wooeco_");
        } else {
            setMySqlDefaults();
        }
        
        ConfigurationSection syncSection = plugin.getConfig().getConfigurationSection("sync");
        if (syncSection != null) {
            this.syncEnabled = syncSection.getBoolean("enable", false);
            this.serverId = syncSection.getString("server-id", "server-1");
            
            ConfigurationSection redisSection = syncSection.getConfigurationSection("redis");
            if (redisSection != null) {
                this.redisHost = redisSection.getString("host", "localhost");
                this.redisPort = redisSection.getInt("port", 6379);
                this.redisPassword = redisSection.getString("password", "");
                this.redisChannel = redisSection.getString("channel", "wooeco:sync");
            } else {
                setRedisDefaults();
            }
        } else {
            setSyncDefaults();
        }
        
        String uuidModeStr = plugin.getConfig().getString("settings.uuid-mode", "Default").toUpperCase();
        try {
            this.uuidMode = UUIDMode.valueOf(uuidModeStr);
        } catch (IllegalArgumentException e) {
            this.uuidMode = UUIDMode.DEFAULT;
        }
        
        this.usernameIgnoreCase = plugin.getConfig().getBoolean("settings.username-ignore-case", false);
    }
    
    private void setDefaults() {
        this.type = DatabaseType.SQLITE;
        this.sqliteFile = "data.db";
        this.uuidMode = UUIDMode.DEFAULT;
        this.usernameIgnoreCase = false;
        setMySqlDefaults();
        setSyncDefaults();
    }
    
    private void setMySqlDefaults() {
        this.mysqlHost = "localhost";
        this.mysqlPort = 3306;
        this.mysqlDatabase = "wooeco";
        this.mysqlUser = "root";
        this.mysqlPassword = "";
        this.mysqlPoolSize = 10;
        this.mysqlTablePrefix = "wooeco_";
    }
    
    private void setRedisDefaults() {
        this.redisHost = "localhost";
        this.redisPort = 6379;
        this.redisPassword = "";
        this.redisChannel = "wooeco:sync";
    }
    
    private void setSyncDefaults() {
        this.syncEnabled = false;
        this.serverId = "server-1";
        setRedisDefaults();
    }
    
    public boolean isMySQL() {
        return type == DatabaseType.MYSQL;
    }
    
    public boolean isSQLite() {
        return type == DatabaseType.SQLITE;
    }
    
    public DatabaseType getType() {
        return type;
    }
    
    public String getSqliteFile() {
        return sqliteFile;
    }
    
    public String getMysqlHost() {
        return mysqlHost;
    }
    
    public int getMysqlPort() {
        return mysqlPort;
    }
    
    public String getMysqlDatabase() {
        return mysqlDatabase;
    }
    
    public String getMysqlUser() {
        return mysqlUser;
    }
    
    public String getMysqlPassword() {
        return mysqlPassword;
    }
    
    public int getMysqlPoolSize() {
        return mysqlPoolSize;
    }
    
    public String getMysqlTablePrefix() {
        return mysqlTablePrefix;
    }
    
    public boolean isSyncEnabled() {
        return syncEnabled;
    }
    
    public String getRedisHost() {
        return redisHost;
    }
    
    public int getRedisPort() {
        return redisPort;
    }
    
    public String getRedisPassword() {
        return redisPassword;
    }
    
    public String getRedisChannel() {
        return redisChannel;
    }
    
    public String getServerId() {
        return serverId;
    }
    
    public UUIDMode getUuidMode() {
        return uuidMode;
    }
    
    public boolean isUsernameIgnoreCase() {
        return usernameIgnoreCase;
    }
    
    public String getJdbcUrl() {
        if (isMySQL()) {
            return "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase 
                + "?useSSL=false&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai";
        } else {
            return "jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/" + sqliteFile;
        }
    }
}
