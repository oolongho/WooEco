package com.oolonghoo.wooeco.database;

import com.oolonghoo.wooeco.WooEco;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * 数据库版本升级器
 * 管理数据库表结构的版本升级
 *
 */
public class DatabaseUpgrader {
    
    private static final int CURRENT_VERSION = 2;
    
    private final WooEco plugin;
    private final DatabaseManager databaseManager;
    private final String tablePrefix;
    private final ReentrantLock lock = new ReentrantLock();
    
    public DatabaseUpgrader(WooEco plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.tablePrefix = databaseManager.getTablePrefix();
    }
    
    public void checkAndUpgrade() {
        lock.lock();
        try {
            ensureVersionTableExists();

            int currentVersion = getCurrentVersion();
            if (currentVersion < CURRENT_VERSION) {
                performUpgrade(currentVersion, CURRENT_VERSION);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "数据库版本检查失败: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }
    
    private void ensureVersionTableExists() throws SQLException {
        String sql;
        if (databaseManager.isMySQL()) {
            sql = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "db_version (" +
                  "id INT PRIMARY KEY AUTO_INCREMENT, " +
                  "version INT NOT NULL, " +
                  "upgraded_at BIGINT NOT NULL, " +
                  "description VARCHAR(255)" +
                  ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        } else {
            sql = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "db_version (" +
                  "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                  "version INTEGER NOT NULL, " +
                  "upgraded_at INTEGER NOT NULL, " +
                  "description TEXT" +
                  ")";
        }
        
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    private int getCurrentVersion() throws SQLException {
        String sql = "SELECT MAX(version) FROM " + tablePrefix + "db_version";
        
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                int version = rs.getInt(1);
                if (rs.wasNull()) {
                    return CURRENT_VERSION;
                }
                return version;
            }
        }
        return CURRENT_VERSION;
    }
    
    private void performUpgrade(int fromVersion, int toVersion) throws SQLException {
        databaseManager.getWriteLock().lock();
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            
            for (int v = fromVersion; v < toVersion; v++) {
                upgradeToVersion(v + 1, stmt);
            }
            
            String insertVersion = "INSERT INTO " + tablePrefix + "db_version (version, upgraded_at, description) VALUES (" +
                                   toVersion + ", " + System.currentTimeMillis() + ", 'Upgraded to version " + toVersion + "')";
            stmt.execute(insertVersion);
            
            plugin.getLogger().info("数据库已从版本 " + fromVersion + " 升级到版本 " + toVersion);
        } finally {
            databaseManager.getWriteLock().unlock();
        }
    }
    
    private void upgradeToVersion(int version, Statement stmt) throws SQLException {
        if (version == 2) {
            upgradeToV2(stmt);
        }
    }
    
    private void upgradeToV2(Statement stmt) throws SQLException {
        int decimalPlaces = plugin.getConfig().getInt("currency.decimal-places", 2);
        String dp = String.valueOf(decimalPlaces);
        
        if (databaseManager.isMySQL()) {
            stmt.execute("ALTER TABLE " + tablePrefix + "accounts MODIFY COLUMN balance DECIMAL(20," + dp + ") NOT NULL DEFAULT 0");
            stmt.execute("ALTER TABLE " + tablePrefix + "accounts MODIFY COLUMN daily_income DECIMAL(20," + dp + ") NOT NULL DEFAULT 0");
            stmt.execute("ALTER TABLE " + tablePrefix + "transactions MODIFY COLUMN amount DECIMAL(20," + dp + ") NOT NULL");
            stmt.execute("ALTER TABLE " + tablePrefix + "transactions MODIFY COLUMN tax DECIMAL(20," + dp + ") NOT NULL DEFAULT 0");
            stmt.execute("ALTER TABLE " + tablePrefix + "logs MODIFY COLUMN amount DECIMAL(20," + dp + ") NOT NULL");
            stmt.execute("ALTER TABLE " + tablePrefix + "logs MODIFY COLUMN balance_before DECIMAL(20," + dp + ") NOT NULL");
            stmt.execute("ALTER TABLE " + tablePrefix + "logs MODIFY COLUMN balance_after DECIMAL(20," + dp + ") NOT NULL");
            stmt.execute("ALTER TABLE " + tablePrefix + "offline_tips MODIFY COLUMN amount DECIMAL(20," + dp + ") NOT NULL");
            stmt.execute("ALTER TABLE " + tablePrefix + "non_player_accounts MODIFY COLUMN balance DECIMAL(20," + dp + ") NOT NULL DEFAULT 0");
        } else {
            plugin.getLogger().info("SQLite 不支持 ALTER COLUMN 精度修改，新数据将使用新精度写入");
        }
    }
    
    public static int getCurrentDbVersion() {
        return CURRENT_VERSION;
    }
}
