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
    
    private static final int CURRENT_VERSION = 4;
    
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
            if (currentVersion >= CURRENT_VERSION) {
                return; // 已是最新版本
            }

            // 版本表为空：区分新安装和旧数据库升级
            if (currentVersion == 0) {
                if (hasExistingData()) {
                    // 旧数据库从未记录版本，从版本 1 开始升级
                    performUpgrade(1, CURRENT_VERSION);
                } else {
                    // 新安装，直接记录当前版本
                    recordVersion(CURRENT_VERSION, "新安装");
                    plugin.getLogger().info("数据库版本: " + CURRENT_VERSION);
                }
                return;
            }

            performUpgrade(currentVersion, CURRENT_VERSION);
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
                    // 版本表为空，说明是全新安装
                    return 0;
                }
                return version;
            }
        }
        return 0;
    }

    /**
     * 检查数据库中是否已有数据（区分新安装和旧数据库升级）
     */
    private boolean hasExistingData() throws SQLException {
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tablePrefix + "accounts LIMIT 1")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /**
     * 记录数据库版本号
     */
    private void recordVersion(int version, String description) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix + "db_version (version, upgraded_at, description) VALUES (" +
                     version + ", " + System.currentTimeMillis() + ", '" + description + "')";
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
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
        switch (version) {
            case 2 -> upgradeToV2(stmt);
            case 3 -> upgradeToV3(stmt);
            case 4 -> upgradeToV4(stmt);
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

    /**
     * 升级到 v3：添加复合索引以提升查询性能
     */
    private void upgradeToV3(Statement stmt) throws SQLException {
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_logs_uuid_reason_timestamp ON " + tablePrefix + "logs(uuid, reason, timestamp)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_logs_reason_timestamp ON " + tablePrefix + "logs(reason, timestamp)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_transactions_sender_timestamp ON " + tablePrefix + "transactions(sender_uuid, timestamp)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_transactions_receiver_timestamp ON " + tablePrefix + "transactions(receiver_uuid, timestamp)");
    }

    /**
     * 升级到 v4：优化 getAccountByName 查询走索引而非全表扫描
     * SQLite: 添加 player_name_lower 列及索引，用于大小写不敏感查询
     * MySQL: 修改 player_name 列的 COLLATE 为 utf8mb4_ci，利用排序规则实现大小写不敏感
     */
    private void upgradeToV4(Statement stmt) throws SQLException {
        if (databaseManager.isMySQL()) {
            stmt.execute("ALTER TABLE " + tablePrefix + "accounts MODIFY COLUMN player_name VARCHAR(16) NOT NULL COLLATE utf8mb4_ci");
        } else {
            // 幂等：列已存在时跳过
            try {
                stmt.execute("ALTER TABLE " + tablePrefix + "accounts ADD COLUMN player_name_lower VARCHAR(16)");
            } catch (SQLException e) {
                if (!e.getMessage().contains("duplicate column name")) {
                    throw e;
                }
                // 列已存在，跳过
            }
            stmt.execute("UPDATE " + tablePrefix + "accounts SET player_name_lower = LOWER(player_name) WHERE player_name_lower IS NULL");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_accounts_player_name_lower ON " + tablePrefix + "accounts(player_name_lower)");
        }
    }
    
    public static int getCurrentDbVersion() {
        return CURRENT_VERSION;
    }
}
