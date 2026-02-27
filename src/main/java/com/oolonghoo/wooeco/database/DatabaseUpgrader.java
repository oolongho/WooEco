package com.oolonghoo.wooeco.database;

import com.oolonghoo.wooeco.WooEco;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * 数据库版本升级器
 * 管理数据库表结构的版本升级
 * 
 * @author oolongho
 */
public class DatabaseUpgrader {
    
    private static final int CURRENT_VERSION = 1;
    
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
            plugin.getLogger().info("数据库版本: " + currentVersion);
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
                return version > 0 ? version : 1;
            }
        }
        return 1;
    }
    
    public static int getCurrentDbVersion() {
        return CURRENT_VERSION;
    }
}
