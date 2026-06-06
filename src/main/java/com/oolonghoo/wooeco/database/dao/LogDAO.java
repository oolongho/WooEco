package com.oolonghoo.wooeco.database.dao;

import com.oolonghoo.wooeco.database.DatabaseManager;
import com.oolonghoo.wooeco.model.EconomyLog;
import com.oolonghoo.wooeco.model.PlayerAccount;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 经济日志数据访问对象
 * 使用读写锁优化并发性能
 * 先获取连接再获取锁，避免持锁期间等待连接池
 *
 */
public class LogDAO {

    private static final String LOG_COLUMNS = "id, uuid, player_name, action, amount, balance_before, balance_after, operator, operator_name, reason, timestamp";

    private final DatabaseManager dbManager;
    private final String tablePrefix;

    public LogDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.tablePrefix = dbManager.getTablePrefix();
    }

    public void saveLog(EconomyLog log) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix + "logs (uuid, player_name, action, amount, balance_before, balance_after, operator, operator_name, reason, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection()) {
            dbManager.getWriteLock().lock();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, log.getUuid().toString());
                stmt.setString(2, log.getPlayerName());
                stmt.setString(3, log.getAction());
                stmt.setBigDecimal(4, log.getAmount());
                stmt.setBigDecimal(5, log.getBalanceBefore());
                stmt.setBigDecimal(6, log.getBalanceAfter());
                stmt.setString(7, log.getOperator());
                stmt.setString(8, log.getOperatorName());
                stmt.setString(9, log.getReason());
                stmt.setLong(10, log.getTimestamp());
                stmt.executeUpdate();
            } finally {
                dbManager.getWriteLock().unlock();
            }
        }
    }

    public List<EconomyLog> getLogsByUuid(UUID uuid, int limit) throws SQLException {
        String sql = "SELECT " + LOG_COLUMNS + " FROM " + tablePrefix + "logs WHERE uuid = ? ORDER BY timestamp DESC LIMIT ?";
        List<EconomyLog> logs = new ArrayList<>();
        try (Connection conn = dbManager.getConnection()) {
            dbManager.getReadLock().lock();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setInt(2, limit);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    logs.add(new EconomyLog(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("player_name"),
                        rs.getString("action"),
                        rs.getBigDecimal("amount"),
                        rs.getBigDecimal("balance_before"),
                        rs.getBigDecimal("balance_after"),
                        rs.getString("operator"),
                        rs.getString("operator_name"),
                        rs.getString("reason"),
                        rs.getLong("timestamp")
                    ));
                }
            } finally {
                dbManager.getReadLock().unlock();
            }
        }
        return logs;
    }

    public List<EconomyLog> getLogsByAction(String action, int limit) throws SQLException {
        String sql = "SELECT " + LOG_COLUMNS + " FROM " + tablePrefix + "logs WHERE action = ? ORDER BY timestamp DESC LIMIT ?";
        List<EconomyLog> logs = new ArrayList<>();
        try (Connection conn = dbManager.getConnection()) {
            dbManager.getReadLock().lock();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, action);
                stmt.setInt(2, limit);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    logs.add(new EconomyLog(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("player_name"),
                        rs.getString("action"),
                        rs.getBigDecimal("amount"),
                        rs.getBigDecimal("balance_before"),
                        rs.getBigDecimal("balance_after"),
                        rs.getString("operator"),
                        rs.getString("operator_name"),
                        rs.getString("reason"),
                        rs.getLong("timestamp")
                    ));
                }
            } finally {
                dbManager.getReadLock().unlock();
            }
        }
        return logs;
    }

    /**
     * 批量保存日志，使用 JDBC batch 减少连接开销
     *
     * @param logs 要保存的日志列表
     * @throws SQLException 数据库异常
     */
    public void saveAllBatch(List<EconomyLog> logs) throws SQLException {
        if (logs.isEmpty()) return;

        String sql = "INSERT INTO " + tablePrefix + "logs (uuid, player_name, action, amount, balance_before, balance_after, operator, operator_name, reason, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection()) {
            dbManager.getWriteLock().lock();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (EconomyLog log : logs) {
                    stmt.setString(1, log.getUuid().toString());
                    stmt.setString(2, log.getPlayerName());
                    stmt.setString(3, log.getAction());
                    stmt.setBigDecimal(4, log.getAmount());
                    stmt.setBigDecimal(5, log.getBalanceBefore());
                    stmt.setBigDecimal(6, log.getBalanceAfter());
                    stmt.setString(7, log.getOperator());
                    stmt.setString(8, log.getOperatorName());
                    stmt.setString(9, log.getReason());
                    stmt.setLong(10, log.getTimestamp());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            } finally {
                dbManager.getWriteLock().unlock();
            }
        }
    }

    public void cleanupOldLogs(int retentionDays) throws SQLException {
        if (retentionDays <= 0) return;

        long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60 * 60 * 1000);
        String sql = "DELETE FROM " + tablePrefix + "logs WHERE timestamp < ?";
        try (Connection conn = dbManager.getConnection()) {
            dbManager.getWriteLock().lock();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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

    public BigDecimal getIncomeInPeriod(UUID uuid, long fromTimestamp) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM " + tablePrefix + "logs " +
                     "WHERE uuid = ? AND reason = 'PAYMENT_RECEIVED' AND timestamp >= ?";
        try (Connection conn = dbManager.getConnection()) {
            dbManager.getReadLock().lock();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setLong(2, fromTimestamp);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    BigDecimal total = rs.getBigDecimal(1);
                    return total != null ? total : BigDecimal.ZERO;
                }
            } finally {
                dbManager.getReadLock().unlock();
            }
        }
        return BigDecimal.ZERO;
    }

    public List<PlayerAccount> getTopIncomesByPeriod(long fromTimestamp, int limit) throws SQLException {
        String sql = "SELECT l.uuid, l.player_name, COALESCE(SUM(l.amount), 0) as period_income " +
                     "FROM " + tablePrefix + "logs l " +
                     "WHERE l.reason = 'PAYMENT_RECEIVED' AND l.timestamp >= ? " +
                     "GROUP BY l.uuid, l.player_name " +
                     "ORDER BY period_income DESC LIMIT ?";
        List<PlayerAccount> accounts = new ArrayList<>();
        try (Connection conn = dbManager.getConnection()) {
            dbManager.getReadLock().lock();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, fromTimestamp);
                stmt.setInt(2, limit);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    PlayerAccount account = new PlayerAccount(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("player_name")
                    );
                    account.setDailyIncome(rs.getBigDecimal("period_income"));
                    accounts.add(account);
                }
            } finally {
                dbManager.getReadLock().unlock();
            }
        }
        return accounts;
    }
}
