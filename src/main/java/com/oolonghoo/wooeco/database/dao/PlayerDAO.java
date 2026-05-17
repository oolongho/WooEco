package com.oolonghoo.wooeco.database.dao;

import com.oolonghoo.wooeco.database.DatabaseManager;
import com.oolonghoo.wooeco.model.PlayerAccount;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 玩家数据访问对象
 * 使用读写锁优化并发性能
 * 
 */
public class PlayerDAO {
    
    private final DatabaseManager dbManager;
    private final String tablePrefix;
    
    public PlayerDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.tablePrefix = dbManager.getTablePrefix();
    }
    
    public PlayerAccount getAccount(UUID uuid) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "accounts WHERE uuid = ?";
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToPlayerAccount(rs);
            }
        } finally {
            dbManager.getReadLock().unlock();
        }
        return null;
    }
    
    public PlayerAccount getAccountByName(String name) throws SQLException {
        boolean ignoreCase = dbManager.getPlugin().getConfig().getBoolean("username-ignore-case", true);
        String sql = ignoreCase
            ? "SELECT * FROM " + tablePrefix + "accounts WHERE LOWER(player_name) = LOWER(?)"
            : "SELECT * FROM " + tablePrefix + "accounts WHERE player_name = ?";
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToPlayerAccount(rs);
            }
        } finally {
            dbManager.getReadLock().unlock();
        }
        return null;
    }
    
    public void createAccount(PlayerAccount account) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix + "accounts (uuid, player_name, balance, daily_income, last_income_reset, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        dbManager.getWriteLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, account.getUuid().toString());
            stmt.setString(2, account.getPlayerName());
            stmt.setBigDecimal(3, account.getBalance());
            stmt.setBigDecimal(4, account.getDailyIncome());
            stmt.setLong(5, account.getLastIncomeReset());
            stmt.setLong(6, account.getCreatedAt());
            stmt.setLong(7, account.getUpdatedAt());
            stmt.executeUpdate();
        } finally {
            dbManager.getWriteLock().unlock();
        }
    }
    
    public void updateAccount(PlayerAccount account) throws SQLException {
        String sql = "UPDATE " + tablePrefix + "accounts SET player_name = ?, balance = ?, daily_income = ?, last_income_reset = ?, updated_at = ? WHERE uuid = ?";
        dbManager.getWriteLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, account.getPlayerName());
            stmt.setBigDecimal(2, account.getBalance());
            stmt.setBigDecimal(3, account.getDailyIncome());
            stmt.setLong(4, account.getLastIncomeReset());
            stmt.setLong(5, System.currentTimeMillis());
            stmt.setString(6, account.getUuid().toString());
            stmt.executeUpdate();
            account.markSaved();
        } finally {
            dbManager.getWriteLock().unlock();
        }
    }
    
    public void saveOrUpdateAccount(PlayerAccount account) throws SQLException {
        String sql;
        if (dbManager.isMySQL()) {
            sql = "INSERT INTO " + tablePrefix + "accounts (uuid, player_name, balance, daily_income, last_income_reset, created_at, updated_at) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?) AS new_val " +
                  "ON DUPLICATE KEY UPDATE player_name = new_val.player_name, balance = new_val.balance, " +
                  "daily_income = new_val.daily_income, last_income_reset = new_val.last_income_reset, updated_at = new_val.updated_at";
        } else {
            sql = "INSERT INTO " + tablePrefix + "accounts (uuid, player_name, balance, daily_income, last_income_reset, created_at, updated_at) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                  "ON CONFLICT(uuid) DO UPDATE SET player_name = excluded.player_name, balance = excluded.balance, " +
                  "daily_income = excluded.daily_income, last_income_reset = excluded.last_income_reset, updated_at = excluded.updated_at";
        }
        
        dbManager.getWriteLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            stmt.setString(1, account.getUuid().toString());
            stmt.setString(2, account.getPlayerName());
            stmt.setBigDecimal(3, account.getBalance());
            stmt.setBigDecimal(4, account.getDailyIncome());
            stmt.setLong(5, account.getLastIncomeReset());
            stmt.setLong(6, account.getCreatedAt());
            stmt.setLong(7, now);
            stmt.executeUpdate();
            account.markSaved();
        } finally {
            dbManager.getWriteLock().unlock();
        }
    }
    
    public List<PlayerAccount> getTopBalances(int limit) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "accounts ORDER BY balance DESC LIMIT ?";
        List<PlayerAccount> accounts = new ArrayList<>();
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                accounts.add(mapResultSetToPlayerAccount(rs));
            }
        } finally {
            dbManager.getReadLock().unlock();
        }
        return accounts;
    }
    
    public List<PlayerAccount> getTopIncomes(int limit) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "accounts ORDER BY daily_income DESC LIMIT ?";
        List<PlayerAccount> accounts = new ArrayList<>();
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                accounts.add(mapResultSetToPlayerAccount(rs));
            }
        } finally {
            dbManager.getReadLock().unlock();
        }
        return accounts;
    }
    
    public List<PlayerAccount> getAllAccounts() throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "accounts";
        List<PlayerAccount> accounts = new ArrayList<>();
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                accounts.add(mapResultSetToPlayerAccount(rs));
            }
        } finally {
            dbManager.getReadLock().unlock();
        }
        return accounts;
    }
    
    public int countAccounts() throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tablePrefix + "accounts";
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } finally {
            dbManager.getReadLock().unlock();
        }
        return 0;
    }
    
    public void resetAllDailyIncome() throws SQLException {
        String sql = "UPDATE " + tablePrefix + "accounts SET daily_income = 0, last_income_reset = ?";
        dbManager.getWriteLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.executeUpdate();
        } finally {
            dbManager.getWriteLock().unlock();
        }
    }
    
    public void updateBalance(UUID uuid, BigDecimal newBalance) throws SQLException {
        String sql = "UPDATE " + tablePrefix + "accounts SET balance = ?, updated_at = ? WHERE uuid = ?";
        dbManager.getWriteLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBigDecimal(1, newBalance);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, uuid.toString());
            stmt.executeUpdate();
        } finally {
            dbManager.getWriteLock().unlock();
        }
    }
    
    public void resetDailyIncome(UUID uuid) throws SQLException {
        String sql = "UPDATE " + tablePrefix + "accounts SET daily_income = 0, last_income_reset = ? WHERE uuid = ?";
        dbManager.getWriteLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } finally {
            dbManager.getWriteLock().unlock();
        }
    }
    
    public java.math.BigDecimal getTotalBalance() throws SQLException {
        String sql = "SELECT SUM(balance) FROM " + tablePrefix + "accounts";
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                java.math.BigDecimal total = rs.getBigDecimal(1);
                return total != null ? total : java.math.BigDecimal.ZERO;
            }
        } finally {
            dbManager.getReadLock().unlock();
        }
        return java.math.BigDecimal.ZERO;
    }
    
    public java.math.BigDecimal getTotalDailyIncome() throws SQLException {
        String sql = "SELECT SUM(daily_income) FROM " + tablePrefix + "accounts";
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                java.math.BigDecimal total = rs.getBigDecimal(1);
                return total != null ? total : java.math.BigDecimal.ZERO;
            }
        } finally {
            dbManager.getReadLock().unlock();
        }
        return java.math.BigDecimal.ZERO;
    }
    
    private PlayerAccount mapResultSetToPlayerAccount(ResultSet rs) throws SQLException {
        return new PlayerAccount(
            UUID.fromString(rs.getString("uuid")),
            rs.getString("player_name"),
            rs.getBigDecimal("balance"),
            rs.getBigDecimal("daily_income"),
            rs.getLong("last_income_reset"),
            rs.getLong("created_at"),
            rs.getLong("updated_at")
        );
    }
    
    private static final int BATCH_SIZE = 500;
    
    private String buildPlaceholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(",");
            sb.append("?");
        }
        return sb.toString();
    }
    
    public int depositAllBatch(BigDecimal amount, boolean onlineOnly, List<UUID> onlineUuids) throws SQLException {
        if (!onlineOnly || onlineUuids == null || onlineUuids.isEmpty()) {
            String sql = "UPDATE " + tablePrefix + "accounts SET balance = balance + ?, updated_at = ?";
            dbManager.getWriteLock().lock();
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setBigDecimal(1, amount);
                stmt.setLong(2, System.currentTimeMillis());
                return stmt.executeUpdate();
            } finally {
                dbManager.getWriteLock().unlock();
            }
        }
        
        int total = 0;
        for (int i = 0; i < onlineUuids.size(); i += BATCH_SIZE) {
            List<UUID> batch = onlineUuids.subList(i, Math.min(i + BATCH_SIZE, onlineUuids.size()));
            String sql = "UPDATE " + tablePrefix + "accounts SET balance = balance + ?, updated_at = ? WHERE uuid IN (" + buildPlaceholders(batch.size()) + ")";
            dbManager.getWriteLock().lock();
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                int paramIndex = 1;
                stmt.setBigDecimal(paramIndex++, amount);
                stmt.setLong(paramIndex++, System.currentTimeMillis());
                for (UUID uuid : batch) {
                    stmt.setString(paramIndex++, uuid.toString());
                }
                total += stmt.executeUpdate();
            } finally {
                dbManager.getWriteLock().unlock();
            }
        }
        return total;
    }
    
    public int withdrawAllBatch(BigDecimal amount, boolean onlineOnly, List<UUID> onlineUuids) throws SQLException {
        if (!onlineOnly || onlineUuids == null || onlineUuids.isEmpty()) {
            String sql = "UPDATE " + tablePrefix + "accounts SET balance = balance - ?, updated_at = ? WHERE balance >= ?";
            dbManager.getWriteLock().lock();
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setBigDecimal(1, amount);
                stmt.setLong(2, System.currentTimeMillis());
                stmt.setBigDecimal(3, amount);
                return stmt.executeUpdate();
            } finally {
                dbManager.getWriteLock().unlock();
            }
        }
        
        int total = 0;
        for (int i = 0; i < onlineUuids.size(); i += BATCH_SIZE) {
            List<UUID> batch = onlineUuids.subList(i, Math.min(i + BATCH_SIZE, onlineUuids.size()));
            String sql = "UPDATE " + tablePrefix + "accounts SET balance = balance - ?, updated_at = ? WHERE balance >= ? AND uuid IN (" + buildPlaceholders(batch.size()) + ")";
            dbManager.getWriteLock().lock();
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                int paramIndex = 1;
                stmt.setBigDecimal(paramIndex++, amount);
                stmt.setLong(paramIndex++, System.currentTimeMillis());
                stmt.setBigDecimal(paramIndex++, amount);
                for (UUID uuid : batch) {
                    stmt.setString(paramIndex++, uuid.toString());
                }
                total += stmt.executeUpdate();
            } finally {
                dbManager.getWriteLock().unlock();
            }
        }
        return total;
    }
    
    public int setAllBatch(BigDecimal amount, boolean onlineOnly, List<UUID> onlineUuids) throws SQLException {
        if (!onlineOnly || onlineUuids == null || onlineUuids.isEmpty()) {
            String sql = "UPDATE " + tablePrefix + "accounts SET balance = ?, updated_at = ?";
            dbManager.getWriteLock().lock();
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setBigDecimal(1, amount);
                stmt.setLong(2, System.currentTimeMillis());
                return stmt.executeUpdate();
            } finally {
                dbManager.getWriteLock().unlock();
            }
        }
        
        int total = 0;
        for (int i = 0; i < onlineUuids.size(); i += BATCH_SIZE) {
            List<UUID> batch = onlineUuids.subList(i, Math.min(i + BATCH_SIZE, onlineUuids.size()));
            String sql = "UPDATE " + tablePrefix + "accounts SET balance = ?, updated_at = ? WHERE uuid IN (" + buildPlaceholders(batch.size()) + ")";
            dbManager.getWriteLock().lock();
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                int paramIndex = 1;
                stmt.setBigDecimal(paramIndex++, amount);
                stmt.setLong(paramIndex++, System.currentTimeMillis());
                for (UUID uuid : batch) {
                    stmt.setString(paramIndex++, uuid.toString());
                }
                total += stmt.executeUpdate();
            } finally {
                dbManager.getWriteLock().unlock();
            }
        }
        return total;
    }
}
