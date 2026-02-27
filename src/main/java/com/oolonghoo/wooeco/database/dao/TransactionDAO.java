package com.oolonghoo.wooeco.database.dao;

import com.oolonghoo.wooeco.database.DatabaseManager;
import com.oolonghoo.wooeco.model.Transaction;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 交易记录数据访问对象
 * 使用读写锁优化并发性能
 * 
 * @author oolongho
 */
public class TransactionDAO {
    
    private final DatabaseManager dbManager;
    private final String tablePrefix;
    
    public TransactionDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.tablePrefix = dbManager.getTablePrefix();
    }
    
    public void saveTransaction(Transaction transaction) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix + "transactions (sender_uuid, sender_name, receiver_uuid, receiver_name, amount, tax, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)";
        dbManager.getWriteLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, transaction.getSenderUuid().toString());
            stmt.setString(2, transaction.getSenderName());
            stmt.setString(3, transaction.getReceiverUuid().toString());
            stmt.setString(4, transaction.getReceiverName());
            stmt.setDouble(5, transaction.getAmount());
            stmt.setDouble(6, transaction.getTax());
            stmt.setLong(7, transaction.getTimestamp());
            stmt.executeUpdate();
        } finally {
            dbManager.getWriteLock().unlock();
        }
    }
    
    public List<Transaction> getTransactionsBySender(UUID uuid, int limit) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "transactions WHERE sender_uuid = ? ORDER BY timestamp DESC LIMIT ?";
        return getTransactions(uuid, limit, sql);
    }
    
    public List<Transaction> getTransactionsByReceiver(UUID uuid, int limit) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "transactions WHERE receiver_uuid = ? ORDER BY timestamp DESC LIMIT ?";
        return getTransactions(uuid, limit, sql);
    }
    
    private List<Transaction> getTransactions(UUID uuid, int limit, String sql) throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                transactions.add(new Transaction(
                    rs.getLong("id"),
                    UUID.fromString(rs.getString("sender_uuid")),
                    rs.getString("sender_name"),
                    UUID.fromString(rs.getString("receiver_uuid")),
                    rs.getString("receiver_name"),
                    rs.getDouble("amount"),
                    rs.getDouble("tax"),
                    rs.getLong("timestamp")
                ));
            }
        } finally {
            dbManager.getReadLock().unlock();
        }
        return transactions;
    }
    
    public void cleanupOldTransactions(int retentionDays) throws SQLException {
        if (retentionDays <= 0) return;
        
        long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60 * 60 * 1000);
        String sql = "DELETE FROM " + tablePrefix + "transactions WHERE timestamp < ?";
        dbManager.getWriteLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, cutoffTime);
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                dbManager.getPlugin().getLogger().info("清理了 " + deleted + " 条过期交易记录");
            }
        } finally {
            dbManager.getWriteLock().unlock();
        }
    }
    
    public List<Transaction> getTransactionsRelated(UUID uuid, int offset, int limit) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "transactions " +
                     "WHERE sender_uuid = ? OR receiver_uuid = ? " +
                     "ORDER BY timestamp DESC LIMIT ? OFFSET ?";
        List<Transaction> transactions = new ArrayList<>();
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, uuid.toString());
            stmt.setInt(3, limit);
            stmt.setInt(4, offset);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                transactions.add(new Transaction(
                    rs.getLong("id"),
                    UUID.fromString(rs.getString("sender_uuid")),
                    rs.getString("sender_name"),
                    UUID.fromString(rs.getString("receiver_uuid")),
                    rs.getString("receiver_name"),
                    rs.getDouble("amount"),
                    rs.getDouble("tax"),
                    rs.getLong("timestamp")
                ));
            }
        } finally {
            dbManager.getReadLock().unlock();
        }
        return transactions;
    }
    
    public int countTransactionsRelated(UUID uuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tablePrefix + "transactions " +
                     "WHERE sender_uuid = ? OR receiver_uuid = ?";
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } finally {
            dbManager.getReadLock().unlock();
        }
        return 0;
    }
}
