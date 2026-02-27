package com.oolonghoo.wooeco.database.dao;

import com.oolonghoo.wooeco.database.DatabaseManager;
import com.oolonghoo.wooeco.model.PlayerAccount;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 玩家数据访问对象
 * 使用读写锁优化并发性能
 * 
 * @author oolongho
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
                return new PlayerAccount(
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("player_name"),
                    rs.getDouble("balance"),
                    rs.getDouble("daily_income"),
                    rs.getLong("last_income_reset"),
                    rs.getLong("created_at"),
                    rs.getLong("updated_at")
                );
            }
        } finally {
            dbManager.getReadLock().unlock();
        }
        return null;
    }
    
    public PlayerAccount getAccountByName(String name) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "accounts WHERE player_name = ?";
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new PlayerAccount(
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("player_name"),
                    rs.getDouble("balance"),
                    rs.getDouble("daily_income"),
                    rs.getLong("last_income_reset"),
                    rs.getLong("created_at"),
                    rs.getLong("updated_at")
                );
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
            stmt.setDouble(3, account.getBalanceDouble());
            stmt.setDouble(4, account.getDailyIncomeDouble());
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
            stmt.setDouble(2, account.getBalanceDouble());
            stmt.setDouble(3, account.getDailyIncomeDouble());
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
        PlayerAccount existing = getAccount(account.getUuid());
        if (existing == null) {
            createAccount(account);
        } else {
            updateAccount(account);
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
                accounts.add(new PlayerAccount(
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("player_name"),
                    rs.getDouble("balance"),
                    rs.getDouble("daily_income"),
                    rs.getLong("last_income_reset"),
                    rs.getLong("created_at"),
                    rs.getLong("updated_at")
                ));
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
                accounts.add(new PlayerAccount(
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("player_name"),
                    rs.getDouble("balance"),
                    rs.getDouble("daily_income"),
                    rs.getLong("last_income_reset"),
                    rs.getLong("created_at"),
                    rs.getLong("updated_at")
                ));
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
                accounts.add(new PlayerAccount(
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("player_name"),
                    rs.getDouble("balance"),
                    rs.getDouble("daily_income"),
                    rs.getLong("last_income_reset"),
                    rs.getLong("created_at"),
                    rs.getLong("updated_at")
                ));
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
    
    public void updateBalance(UUID uuid, double newBalance) throws SQLException {
        String sql = "UPDATE " + tablePrefix + "accounts SET balance = ?, updated_at = ? WHERE uuid = ?";
        dbManager.getWriteLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, newBalance);
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
}
