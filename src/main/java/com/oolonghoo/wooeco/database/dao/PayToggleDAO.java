package com.oolonghoo.wooeco.database.dao;

import com.oolonghoo.wooeco.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PayToggleDAO {
    private final DatabaseManager dbManager;
    private final String tablePrefix;

    public PayToggleDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.tablePrefix = dbManager.getTablePrefix();
    }

    public boolean isEnabled(UUID uuid) {
        String sql = "SELECT enabled FROM " + tablePrefix + "pay_toggle WHERE uuid = ?";
        try (Connection conn = dbManager.getConnection()) {
            dbManager.getReadLock().lock();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getBoolean("enabled");
                }
            } finally {
                dbManager.getReadLock().unlock();
            }
        } catch (SQLException e) {
            dbManager.getPlugin().getLogger().warning("查询收款开关失败: " + e.getMessage());
        }
        return true;
    }

    public void setEnabled(UUID uuid, boolean enabled) {
        String sql;
        if (dbManager.isMySQL()) {
            sql = "INSERT INTO " + tablePrefix + "pay_toggle (uuid, enabled, updated_at) VALUES (?, ?, ?) AS new_val " +
                  "ON DUPLICATE KEY UPDATE enabled = new_val.enabled, updated_at = new_val.updated_at";
        } else {
            sql = "INSERT INTO " + tablePrefix + "pay_toggle (uuid, enabled, updated_at) VALUES (?, ?, ?) " +
                  "ON CONFLICT(uuid) DO UPDATE SET enabled = excluded.enabled, updated_at = excluded.updated_at";
        }
        try (Connection conn = dbManager.getConnection()) {
            dbManager.getWriteLock().lock();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setBoolean(2, enabled);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.executeUpdate();
            } finally {
                dbManager.getWriteLock().unlock();
            }
        } catch (SQLException e) {
            dbManager.getPlugin().getLogger().warning("设置收款开关失败: " + e.getMessage());
        }
    }

    public Map<UUID, Boolean> loadAll() {
        Map<UUID, Boolean> map = new HashMap<>();
        String sql = "SELECT uuid, enabled FROM " + tablePrefix + "pay_toggle";
        try (Connection conn = dbManager.getConnection()) {
            dbManager.getReadLock().lock();
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    map.put(uuid, rs.getBoolean("enabled"));
                }
            } finally {
                dbManager.getReadLock().unlock();
            }
        } catch (SQLException e) {
            dbManager.getPlugin().getLogger().warning("加载收款开关数据失败: " + e.getMessage());
        }
        return map;
    }
}
