package com.oolonghoo.wooeco.database;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.config.DatabaseConfig;
import com.oolonghoo.wooeco.database.dao.LogDAO;
import com.oolonghoo.wooeco.database.dao.NonPlayerAccountDAO;
import com.oolonghoo.wooeco.database.dao.OfflineTransferTipDAO;
import com.oolonghoo.wooeco.database.dao.PayToggleDAO;
import com.oolonghoo.wooeco.database.dao.PlayerDAO;
import com.oolonghoo.wooeco.database.dao.TransactionDAO;
import com.oolonghoo.wooeco.database.dao.UUIDMappingDAO;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 数据库管理器
 * 支持 SQLite 和 MySQL，使用 HikariCP 连接池
 * 
 */
public class DatabaseManager {
    
    private final WooEco plugin;
    private final DatabaseConfig config;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    private static final Lock NO_OP_LOCK = new Lock() {
        @Override public void lock() {}
        @Override public void lockInterruptibly() {}
        @Override public boolean tryLock() { return true; }
        @Override public boolean tryLock(long time, TimeUnit unit) { return true; }
        @Override public void unlock() {}
        @Override public Condition newCondition() { return null; }
    };
    
    private HikariDataSource dataSource;
    private String tablePrefix;
    
    private PlayerDAO playerDAO;
    private TransactionDAO transactionDAO;
    private LogDAO logDAO;
    private OfflineTransferTipDAO offlineTransferTipDAO;
    private NonPlayerAccountDAO nonPlayerAccountDAO;
    private PayToggleDAO payToggleDAO;
    private UUIDMappingDAO uuidMappingDAO;
    
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
        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "数据库初始化失败: " + e.getMessage(), e);
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
        
        // 抑制 HikariCP 的 INFO 日志
        java.util.logging.Logger hikariLogger = java.util.logging.Logger.getLogger("com.zaxxer.hikari");
        hikariLogger.setLevel(java.util.logging.Level.WARNING);

        dataSource = new HikariDataSource(hikariConfig);
    }
    
    private void createTables() throws SQLException {
        int decimalPlaces = plugin.getConfig().getInt("currency.decimal-places", 2);
        
        Lock writeLock = getWriteLock();
        writeLock.lock();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            String accountsTable = config.isMySQL() ? 
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "accounts (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "player_name VARCHAR(16) NOT NULL COLLATE utf8mb4_ci, " +
                "balance DECIMAL(20," + decimalPlaces + ") NOT NULL DEFAULT 0, " +
                "daily_income DECIMAL(20," + decimalPlaces + ") NOT NULL DEFAULT 0, " +
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
                "player_name_lower VARCHAR(16), " +
                "balance DECIMAL(20," + decimalPlaces + ") NOT NULL DEFAULT 0, " +
                "daily_income DECIMAL(20," + decimalPlaces + ") NOT NULL DEFAULT 0, " +
                "last_income_reset INTEGER NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL)";
            
            stmt.execute(accountsTable);
            
            if (!config.isMySQL()) {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_accounts_uuid ON " + tablePrefix + "accounts(uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_accounts_balance ON " + tablePrefix + "accounts(balance)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_accounts_player_name_lower ON " + tablePrefix + "accounts(player_name_lower)");
            }
            
            String transactionsTable = config.isMySQL() ?
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "transactions (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "sender_uuid VARCHAR(36) NOT NULL, " +
                "sender_name VARCHAR(16) NOT NULL, " +
                "receiver_uuid VARCHAR(36) NOT NULL, " +
                "receiver_name VARCHAR(16) NOT NULL, " +
                "amount DECIMAL(20," + decimalPlaces + ") NOT NULL, " +
                "tax DECIMAL(20," + decimalPlaces + ") NOT NULL DEFAULT 0, " +
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
                "amount DECIMAL(20," + decimalPlaces + ") NOT NULL, " +
                "tax DECIMAL(20," + decimalPlaces + ") NOT NULL DEFAULT 0, " +
                "timestamp INTEGER NOT NULL)";
            
            stmt.execute(transactionsTable);
            
            String logsTable = config.isMySQL() ?
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "logs (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "player_name VARCHAR(16) NOT NULL, " +
                "action VARCHAR(32) NOT NULL, " +
                "amount DECIMAL(20," + decimalPlaces + ") NOT NULL, " +
                "balance_before DECIMAL(20," + decimalPlaces + ") NOT NULL, " +
                "balance_after DECIMAL(20," + decimalPlaces + ") NOT NULL, " +
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
                "amount DECIMAL(20," + decimalPlaces + ") NOT NULL, " +
                "balance_before DECIMAL(20," + decimalPlaces + ") NOT NULL, " +
                "balance_after DECIMAL(20," + decimalPlaces + ") NOT NULL, " +
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
                "amount DECIMAL(20," + decimalPlaces + ") NOT NULL, " +
                "timestamp BIGINT NOT NULL, " +
                "notified TINYINT DEFAULT 0, " +
                "INDEX idx_receiver (receiver_uuid)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                :
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "offline_tips (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "receiver_uuid VARCHAR(36) NOT NULL, " +
                "sender_name VARCHAR(16) NOT NULL, " +
                "amount DECIMAL(20," + decimalPlaces + ") NOT NULL, " +
                "timestamp INTEGER NOT NULL, " +
                "notified INTEGER DEFAULT 0)";
            
            stmt.execute(offlineTipsTable);
            
            String nonPlayerAccountsTable = config.isMySQL() ?
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "non_player_accounts (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "account_name VARCHAR(64) NOT NULL UNIQUE, " +
                "balance DECIMAL(20," + decimalPlaces + ") NOT NULL DEFAULT 0, " +
                "created_at BIGINT NOT NULL, " +
                "updated_at BIGINT NOT NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                :
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "non_player_accounts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "account_name VARCHAR(64) NOT NULL UNIQUE, " +
                "balance DECIMAL(20," + decimalPlaces + ") NOT NULL DEFAULT 0, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL)";
            
            stmt.execute(nonPlayerAccountsTable);
            
            String payToggleTable = config.isMySQL() ?
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "pay_toggle (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "enabled TINYINT DEFAULT 1, " +
                "updated_at BIGINT NOT NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                :
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "pay_toggle (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "enabled INTEGER DEFAULT 1, " +
                "updated_at INTEGER NOT NULL)";
            
            stmt.execute(payToggleTable);
            
            String uuidMappingTable = config.isMySQL() ?
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "uuid_mapping (" +
                "offline_uuid VARCHAR(36) PRIMARY KEY, " +
                "online_uuid VARCHAR(36) NOT NULL, " +
                "player_name VARCHAR(16), " +
                "updated_at BIGINT NOT NULL, " +
                "INDEX idx_online_uuid (online_uuid)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                :
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "uuid_mapping (" +
                "offline_uuid VARCHAR(36) PRIMARY KEY, " +
                "online_uuid VARCHAR(36) NOT NULL, " +
                "player_name VARCHAR(16), " +
                "updated_at INTEGER NOT NULL)";
            
            stmt.execute(uuidMappingTable);
            
            if (!config.isMySQL()) {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_uuid_mapping_online ON " + tablePrefix + "uuid_mapping(online_uuid)");
            }

            // 复合索引：优化按原因和时间范围的查询
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_logs_uuid_reason_timestamp ON " + tablePrefix + "logs(uuid, reason, timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_logs_reason_timestamp ON " + tablePrefix + "logs(reason, timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_transactions_sender_timestamp ON " + tablePrefix + "transactions(sender_uuid, timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_transactions_receiver_timestamp ON " + tablePrefix + "transactions(receiver_uuid, timestamp)");
        } finally {
            writeLock.unlock();
        }
    }
    
    private void initializeDAOs() {
        playerDAO = new PlayerDAO(this);
        transactionDAO = new TransactionDAO(this);
        logDAO = new LogDAO(this);
        offlineTransferTipDAO = new OfflineTransferTipDAO(this);
        nonPlayerAccountDAO = new NonPlayerAccountDAO(this);
        payToggleDAO = new PayToggleDAO(this);
        uuidMappingDAO = new UUIDMappingDAO(this);
    }
    
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * 在写锁和数据库事务内执行操作，保证原子性。
     * 事务内只应做 DB 操作，不要触发事件或记录日志。
     *
     * @param operation 接收 Connection 并返回结果的函数
     * @param <T>       返回值类型
     * @return 操作结果
     * @throws SQLException 数据库异常时抛出，事务已回滚
     */
    public <T> T executeInTransaction(TransactionOperation<T> operation) throws SQLException {
        getWriteLock().lock();
        try (Connection conn = getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                T result = operation.execute(conn);
                conn.commit();
                return result;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } finally {
            getWriteLock().unlock();
        }
    }

    /**
     * 事务操作函数式接口，允许抛出 SQLException
     */
    @FunctionalInterface
    public interface TransactionOperation<T> {
        T execute(Connection conn) throws SQLException;
    }
    
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
    
    public Lock getReadLock() {
        return isMySQL() ? rwLock.readLock() : NO_OP_LOCK;
    }
    
    public Lock getWriteLock() {
        return isMySQL() ? rwLock.writeLock() : NO_OP_LOCK;
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
    
    public PayToggleDAO getPayToggleDAO() {
        return payToggleDAO;
    }
    
    public UUIDMappingDAO getUUIDMappingDAO() {
        return uuidMappingDAO;
    }
    
    public WooEco getPlugin() {
        return plugin;
    }
}
