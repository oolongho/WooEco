package com.oolonghoo.wooeco.database.dao;

import com.oolonghoo.wooeco.database.DatabaseManager;
import com.oolonghoo.wooeco.model.OfflineTransferTip;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 离线交易提示数据访问对象
 * 使用读写锁优化并发性能
 * 
 * @author oolongho
 */
public class OfflineTransferTipDAO {
    
    private final DatabaseManager dbManager;
    private final String tablePrefix;
    
    public OfflineTransferTipDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.tablePrefix = dbManager.getTablePrefix();
    }
    
    public void saveTip(OfflineTransferTip tip) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix + "offline_tips (receiver_uuid, sender_name, amount, timestamp, notified) VALUES (?, ?, ?, ?, 0)";
        dbManager.getWriteLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tip.getReceiverUuid().toString());
            stmt.setString(2, tip.getSenderName());
            stmt.setDouble(3, tip.getAmount());
            stmt.setLong(4, tip.getTimestamp());
            stmt.executeUpdate();
        } finally {
            dbManager.getWriteLock().unlock();
        }
    }
    
    public List<OfflineTransferTip> getUnnotifiedTips(UUID uuid) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "offline_tips WHERE receiver_uuid = ? AND notified = 0 ORDER BY timestamp ASC";
        List<OfflineTransferTip> tips = new ArrayList<>();
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                tips.add(new OfflineTransferTip(
                    rs.getLong("id"),
                    UUID.fromString(rs.getString("receiver_uuid")),
                    rs.getString("sender_name"),
                    rs.getDouble("amount"),
                    rs.getLong("timestamp"),
                    rs.getInt("notified") == 1
                ));
            }
        } finally {
            dbManager.getReadLock().unlock();
        }
        return tips;
    }
    
    public void markAsNotified(UUID uuid) throws SQLException {
        String sql = "UPDATE " + tablePrefix + "offline_tips SET notified = 1 WHERE receiver_uuid = ? AND notified = 0";
        dbManager.getWriteLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } finally {
            dbManager.getWriteLock().unlock();
        }
    }
    
    public int getUnnotifiedCount(UUID uuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tablePrefix + "offline_tips WHERE receiver_uuid = ? AND notified = 0";
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } finally {
            dbManager.getReadLock().unlock();
        }
        return 0;
    }
    
    public void cleanupOldTips(int retentionDays) throws SQLException {
        if (retentionDays <= 0) return;
        
        long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60 * 60 * 1000);
        String sql = "DELETE FROM " + tablePrefix + "offline_tips WHERE timestamp < ? AND notified = 1";
        dbManager.getWriteLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, cutoffTime);
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                dbManager.getPlugin().getLogger().info("清理了 " + deleted + " 条过期离线交易提示");
            }
        } finally {
            dbManager.getWriteLock().unlock();
        }
    }
}
