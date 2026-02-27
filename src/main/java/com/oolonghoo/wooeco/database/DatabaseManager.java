package com.oolonghoo.wooeco.database;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.config.DatabaseConfig;
import com.oolonghoo.wooeco.database.dao.LogDAO;
import com.oolonghoo.wooeco.database.dao.NonPlayerAccountDAO;
import com.oolonghoo.wooeco.database.dao.OfflineTransferTipDAO;
import com.oolonghoo.wooeco.database.dao.PlayerDAO;
import com.oolonghoo.wooeco.database.dao.TransactionDAO;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 数据库管理器
 * 支持 SQLite 和 MySQL，使用 HikariCP 连接池
 * 
 * @author oolongho
 */
public class DatabaseManager {
    
    private final WooEco plugin;
    private final DatabaseConfig config;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    private HikariDataSource dataSource;
    private String tablePrefix;
    
    private PlayerDAO playerDAO;
    private TransactionDAO transactionDAO;
    private LogDAO logDAO;
    private OfflineTransferTipDAO offlineTransferTipDAO;
    private NonPlayerAccountDAO nonPlayerAccountDAO;
    
    public DatabaseManager(WooEco plugin) {
        this.plugin = plugin;
        this.config = plugin.getDatabaseConfig();
        this.tablePrefix = config.getMysqlTablePrefix();
    }
    
    public void initialize() {
        try {
            initDataSource();
            createTables();
            initializeDAOs();
            
            DatabaseUpgrader upgrader = new DatabaseUpgrader(plugin, this);
            upgrader.checkAndUpgrade();
            
            plugin.getLogger().info("数据库初始化完成 (" + config.getType() + ")");
        } catch (SQLException e) {
            plugin.getLogger().severe("数据库初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void initDataSource() {
        HikariConfig hikariConfig = new HikariConfig();
        
        if (config.isMySQL()) {
            hikariConfig.setPoolName("[WooEco-MySQL]");
            hikariConfig.setJdbcUrl(config.getJdbcUrl());
            hikariConfig.setUsername(config.getMysqlUser());
            hikariConfig.setPassword(config.getMysqlPassword());
            hikariConfig.setMaximumPoolSize(config.getMysqlPoolSize());
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setIdleTimeout(30000);
            hikariConfig.setMaxLifetime(1800000);
            hikariConfig.setConnectionTimeout(10000);
            
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
            hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
            hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
            hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
            hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
            hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
        } else {
            hikariConfig.setPoolName("[WooEco-SQLite]");
            hikariConfig.setJdbcUrl(config.getJdbcUrl());
            hikariConfig.setMaximumPoolSize(1);
            hikariConfig.setMinimumIdle(1);
            
            File dbFile = new File(plugin.getDataFolder(), config.getSqliteFile());
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }
        }
        
        dataSource = new HikariDataSource(hikariConfig);
    }
    
    private void createTables() throws SQLException {
        rwLock.writeLock().lock();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            String accountsTable = config.isMySQL() ? 
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "accounts (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "player_name VARCHAR(16) NOT NULL, " +
                "balance DECIMAL(20,2) NOT NULL DEFAULT 0, " +
                "daily_income DECIMAL(20,2) NOT NULL DEFAULT 0, " +
                "last_income_reset BIGINT NOT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "updated_at BIGINT NOT NULL, " +
                "INDEX idx_uuid (uuid), " +
                "INDEX idx_balance (balance)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                :
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "accounts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "player_name VARCHAR(16) NOT NULL, " +
                "balance REAL NOT NULL DEFAULT 0, " +
                "daily_income REAL NOT NULL DEFAULT 0, " +
                "last_income_reset INTEGER NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL)";
            
            stmt.execute(accountsTable);
            
            if (!config.isMySQL()) {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_accounts_uuid ON " + tablePrefix + "accounts(uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_accounts_balance ON " + tablePrefix + "accounts(balance)");
            }
            
            String transactionsTable = config.isMySQL() ?
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "transactions (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "sender_uuid VARCHAR(36) NOT NULL, " +
                "sender_name VARCHAR(16) NOT NULL, " +
                "receiver_uuid VARCHAR(36) NOT NULL, " +
                "receiver_name VARCHAR(16) NOT NULL, " +
                "amount DECIMAL(20,2) NOT NULL, " +
                "tax DECIMAL(20,2) NOT NULL DEFAULT 0, " +
                "timestamp BIGINT NOT NULL, " +
                "INDEX idx_sender (sender_uuid), " +
                "INDEX idx_receiver (receiver_uuid), " +
                "INDEX idx_timestamp (timestamp)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                :
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender_uuid VARCHAR(36) NOT NULL, " +
                "sender_name VARCHAR(16) NOT NULL, " +
                "receiver_uuid VARCHAR(36) NOT NULL, " +
                "receiver_name VARCHAR(16) NOT NULL, " +
                "amount REAL NOT NULL, " +
                "tax REAL NOT NULL DEFAULT 0, " +
                "timestamp INTEGER NOT NULL)";
            
            stmt.execute(transactionsTable);
            
            String logsTable = config.isMySQL() ?
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "logs (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "player_name VARCHAR(16) NOT NULL, " +
                "action VARCHAR(32) NOT NULL, " +
                "amount DECIMAL(20,2) NOT NULL, " +
                "balance_before DECIMAL(20,2) NOT NULL, " +
                "balance_after DECIMAL(20,2) NOT NULL, " +
                "operator VARCHAR(36), " +
                "operator_name VARCHAR(16), " +
                "reason VARCHAR(255), " +
                "timestamp BIGINT NOT NULL, " +
                "INDEX idx_uuid (uuid), " +
                "INDEX idx_action (action), " +
                "INDEX idx_timestamp (timestamp)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                :
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "player_name VARCHAR(16) NOT NULL, " +
                "action VARCHAR(32) NOT NULL, " +
                "amount REAL NOT NULL, " +
                "balance_before REAL NOT NULL, " +
                "balance_after REAL NOT NULL, " +
                "operator VARCHAR(36), " +
                "operator_name VARCHAR(16), " +
                "reason VARCHAR(255), " +
                "timestamp INTEGER NOT NULL)";
            
            stmt.execute(logsTable);
            
            String offlineTipsTable = config.isMySQL() ?
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "offline_tips (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "receiver_uuid VARCHAR(36) NOT NULL, " +
                "sender_name VARCHAR(16) NOT NULL, " +
                "amount DECIMAL(20,2) NOT NULL, " +
                "timestamp BIGINT NOT NULL, " +
                "notified TINYINT DEFAULT 0, " +
                "INDEX idx_receiver (receiver_uuid)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                :
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "offline_tips (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "receiver_uuid VARCHAR(36) NOT NULL, " +
                "sender_name VARCHAR(16) NOT NULL, " +
                "amount REAL NOT NULL, " +
                "timestamp INTEGER NOT NULL, " +
                "notified INTEGER DEFAULT 0)";
            
            stmt.execute(offlineTipsTable);
            
            String nonPlayerAccountsTable = config.isMySQL() ?
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "non_player_accounts (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "account_name VARCHAR(64) NOT NULL UNIQUE, " +
                "balance DECIMAL(20,2) NOT NULL DEFAULT 0, " +
                "created_at BIGINT NOT NULL, " +
                "updated_at BIGINT NOT NULL, " +
                "PRIMARY KEY (account_name)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                :
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "non_player_accounts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "account_name VARCHAR(64) NOT NULL UNIQUE, " +
                "balance REAL NOT NULL DEFAULT 0, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL)";
            
            stmt.execute(nonPlayerAccountsTable);
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    private void initializeDAOs() {
        playerDAO = new PlayerDAO(this);
        transactionDAO = new TransactionDAO(this);
        logDAO = new LogDAO(this);
        offlineTransferTipDAO = new OfflineTransferTipDAO(this);
        nonPlayerAccountDAO = new NonPlayerAccountDAO(this);
    }
    
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
    
    public ReentrantReadWriteLock.ReadLock getReadLock() {
        return rwLock.readLock();
    }
    
    public ReentrantReadWriteLock.WriteLock getWriteLock() {
        return rwLock.writeLock();
    }
    
    public ReentrantReadWriteLock getLock() {
        return rwLock;
    }
    
    public String getTablePrefix() {
        return tablePrefix;
    }
    
    public boolean isMySQL() {
        return config.isMySQL();
    }
    
    public PlayerDAO getPlayerDAO() {
        return playerDAO;
    }
    
    public TransactionDAO getTransactionDAO() {
        return transactionDAO;
    }
    
    public LogDAO getLogDAO() {
        return logDAO;
    }
    
    public OfflineTransferTipDAO getOfflineTransferTipDAO() {
        return offlineTransferTipDAO;
    }
    
    public NonPlayerAccountDAO getNonPlayerAccountDAO() {
        return nonPlayerAccountDAO;
    }
    
    public WooEco getPlugin() {
        return plugin;
    }
}
