package com.oolonghoo.wooeco.database.dao;

import com.oolonghoo.wooeco.database.DatabaseManager;
import com.oolonghoo.wooeco.model.NonPlayerAccount;
import com.oolonghoo.wooeco.util.MoneyFormat;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 非玩家账户数据访问对象
 * 使用读写锁优化并发性能
 * 
 * @author oolongho
 */
public class NonPlayerAccountDAO {
    
    private final DatabaseManager databaseManager;
    private final String tablePrefix;
    
    public NonPlayerAccountDAO(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.tablePrefix = databaseManager.getTablePrefix();
    }
    
    public NonPlayerAccount getAccount(String accountName) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "non_player_accounts WHERE account_name = ?";
        
        databaseManager.getReadLock().lock();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, accountName);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return new NonPlayerAccount(
                    rs.getString("account_name"),
                    rs.getBigDecimal("balance"),
                    rs.getLong("created_at"),
                    rs.getLong("updated_at")
                );
            }
        } finally {
            databaseManager.getReadLock().unlock();
        }
        return null;
    }
    
    public void createAccount(NonPlayerAccount account) throws SQLException {
        String sql;
        if (databaseManager.isMySQL()) {
            sql = "INSERT INTO " + tablePrefix + "non_player_accounts " +
                  "(account_name, balance, created_at, updated_at) VALUES (?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE balance = ?";
        } else {
            sql = "INSERT OR REPLACE INTO " + tablePrefix + "non_player_accounts " +
                  "(account_name, balance, created_at, updated_at) VALUES (?, ?, ?, ?)";
        }
        
        databaseManager.getWriteLock().lock();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            long now = System.currentTimeMillis();
            stmt.setString(1, account.getAccountName());
            stmt.setBigDecimal(2, account.getBalance());
            stmt.setLong(3, now);
            stmt.setLong(4, now);
            
            if (databaseManager.isMySQL()) {
                stmt.setBigDecimal(5, account.getBalance());
            }
            
            stmt.executeUpdate();
        } finally {
            databaseManager.getWriteLock().unlock();
        }
    }
    
    public void saveOrUpdateAccount(NonPlayerAccount account) throws SQLException {
        String sql;
        if (databaseManager.isMySQL()) {
            sql = "INSERT INTO " + tablePrefix + "non_player_accounts " +
                  "(account_name, balance, created_at, updated_at) VALUES (?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE balance = ?, updated_at = ?";
        } else {
            sql = "INSERT OR REPLACE INTO " + tablePrefix + "non_player_accounts " +
                  "(account_name, balance, created_at, updated_at) VALUES (?, ?, ?, ?)";
        }
        
        databaseManager.getWriteLock().lock();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            long now = System.currentTimeMillis();
            stmt.setString(1, account.getAccountName());
            stmt.setBigDecimal(2, account.getBalance());
            stmt.setLong(3, account.getCreatedAt());
            stmt.setLong(4, now);
            
            if (databaseManager.isMySQL()) {
                stmt.setBigDecimal(5, account.getBalance());
                stmt.setLong(6, now);
            }
            
            stmt.executeUpdate();
            account.markSaved();
        } finally {
            databaseManager.getWriteLock().unlock();
        }
    }
    
    public void deleteAccount(String accountName) throws SQLException {
        String sql = "DELETE FROM " + tablePrefix + "non_player_accounts WHERE account_name = ?";
        
        databaseManager.getWriteLock().lock();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, accountName);
            stmt.executeUpdate();
        } finally {
            databaseManager.getWriteLock().unlock();
        }
    }
    
    public List<NonPlayerAccount> getAllAccounts() throws SQLException {
        List<NonPlayerAccount> accounts = new ArrayList<>();
        String sql = "SELECT * FROM " + tablePrefix + "non_player_accounts ORDER BY balance DESC";
        
        databaseManager.getReadLock().lock();
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                accounts.add(new NonPlayerAccount(
                    rs.getString("account_name"),
                    rs.getBigDecimal("balance"),
                    rs.getLong("created_at"),
                    rs.getLong("updated_at")
                ));
            }
        } finally {
            databaseManager.getReadLock().unlock();
        }
        return accounts;
    }
    
    public int countAccounts() throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tablePrefix + "non_player_accounts";
        
        databaseManager.getReadLock().lock();
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
        } finally {
            databaseManager.getReadLock().unlock();
        }
        return 0;
    }
    
    public BigDecimal getTotalBalance() throws SQLException {
        String sql = "SELECT SUM(balance) FROM " + tablePrefix + "non_player_accounts";
        
        databaseManager.getReadLock().lock();
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                BigDecimal total = rs.getBigDecimal(1);
                return total != null ? total : BigDecimal.ZERO;
            }
        } finally {
            databaseManager.getReadLock().unlock();
        }
        return BigDecimal.ZERO;
    }
    
    public void updateBalance(String accountName, BigDecimal newBalance) throws SQLException {
        String sql = "UPDATE " + tablePrefix + "non_player_accounts " +
                     "SET balance = ?, updated_at = ? WHERE account_name = ?";
        
        databaseManager.getWriteLock().lock();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setBigDecimal(1, MoneyFormat.formatInput(newBalance));
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, accountName);
            stmt.executeUpdate();
        } finally {
            databaseManager.getWriteLock().unlock();
        }
    }
    
    public void addToBalance(String accountName, BigDecimal amount) throws SQLException {
        String sql = "UPDATE " + tablePrefix + "non_player_accounts " +
                     "SET balance = balance + ?, updated_at = ? WHERE account_name = ?";
        
        databaseManager.getWriteLock().lock();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setBigDecimal(1, amount);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, accountName);
            stmt.executeUpdate();
        } finally {
            databaseManager.getWriteLock().unlock();
        }
    }
    
    public void subtractFromBalance(String accountName, BigDecimal amount) throws SQLException {
        String sql = "UPDATE " + tablePrefix + "non_player_accounts " +
                     "SET balance = balance - ?, updated_at = ? WHERE account_name = ?";
        
        databaseManager.getWriteLock().lock();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setBigDecimal(1, amount);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, accountName);
            stmt.executeUpdate();
        } finally {
            databaseManager.getWriteLock().unlock();
        }
    }
}
