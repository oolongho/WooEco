package com.oolonghoo.wooeco.database.dao;

import com.oolonghoo.wooeco.database.DatabaseManager;
import com.oolonghoo.wooeco.model.EconomyLog;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 经济日志数据访问对象
 * 使用读写锁优化并发性能
 * 
 * @author oolongho
 */
public class LogDAO {
    
    private final DatabaseManager dbManager;
    private final String tablePrefix;
    
    public LogDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.tablePrefix = dbManager.getTablePrefix();
    }
    
    public void saveLog(EconomyLog log) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix + "logs (uuid, player_name, action, amount, balance_before, balance_after, operator, operator_name, reason, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        dbManager.getWriteLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, log.getUuid().toString());
            stmt.setString(2, log.getPlayerName());
            stmt.setString(3, log.getAction());
            stmt.setDouble(4, log.getAmount());
            stmt.setDouble(5, log.getBalanceBefore());
            stmt.setDouble(6, log.getBalanceAfter());
            stmt.setString(7, log.getOperator());
            stmt.setString(8, log.getOperatorName());
            stmt.setString(9, log.getReason());
            stmt.setLong(10, log.getTimestamp());
            stmt.executeUpdate();
        } finally {
            dbManager.getWriteLock().unlock();
        }
    }
    
    public List<EconomyLog> getLogsByUuid(UUID uuid, int limit) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "logs WHERE uuid = ? ORDER BY timestamp DESC LIMIT ?";
        List<EconomyLog> logs = new ArrayList<>();
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                logs.add(new EconomyLog(
                    rs.getLong("id"),
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("player_name"),
                    rs.getString("action"),
                    rs.getDouble("amount"),
                    rs.getDouble("balance_before"),
                    rs.getDouble("balance_after"),
                    rs.getString("operator"),
                    rs.getString("operator_name"),
                    rs.getString("reason"),
                    rs.getLong("timestamp")
                ));
            }
        } finally {
            dbManager.getReadLock().unlock();
        }
        return logs;
    }
    
    public List<EconomyLog> getLogsByAction(String action, int limit) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "logs WHERE action = ? ORDER BY timestamp DESC LIMIT ?";
        List<EconomyLog> logs = new ArrayList<>();
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, action);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                logs.add(new EconomyLog(
                    rs.getLong("id"),
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("player_name"),
                    rs.getString("action"),
                    rs.getDouble("amount"),
                    rs.getDouble("balance_before"),
                    rs.getDouble("balance_after"),
                    rs.getString("operator"),
                    rs.getString("operator_name"),
                    rs.getString("reason"),
                    rs.getLong("timestamp")
                ));
            }
        } finally {
            dbManager.getReadLock().unlock();
        }
        return logs;
    }
    
    public void cleanupOldLogs(int retentionDays) throws SQLException {
        if (retentionDays <= 0) return;
        
        long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60 * 60 * 1000);
        String sql = "DELETE FROM " + tablePrefix + "logs WHERE timestamp < ?";
        dbManager.getWriteLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, cutoffTime);
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                dbManager.getPlugin().getLogger().info("清理了 " + deleted + " 条过期日志记录");
            }
        } finally {
            dbManager.getWriteLock().unlock();
        }
    }
}
