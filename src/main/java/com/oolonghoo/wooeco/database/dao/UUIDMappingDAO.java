package com.oolonghoo.wooeco.database.dao;

import com.oolonghoo.wooeco.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UUIDMappingDAO {
    private final DatabaseManager dbManager;
    private final String tablePrefix;

    public UUIDMappingDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.tablePrefix = dbManager.getTablePrefix();
    }

    public UUID getOnlineUUID(UUID offlineUuid) {
        String sql = "SELECT online_uuid FROM " + tablePrefix + "uuid_mapping WHERE offline_uuid = ?";
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, offlineUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("online_uuid"));
            }
        } catch (SQLException e) {
            dbManager.getPlugin().getLogger().warning("查询UUID映射失败: " + e.getMessage());
        } finally {
            dbManager.getReadLock().unlock();
        }
        return null;
    }

    public void saveMapping(UUID offlineUuid, UUID onlineUuid, String playerName) {
        String sql;
        if (dbManager.isMySQL()) {
            sql = "INSERT INTO " + tablePrefix + "uuid_mapping (offline_uuid, online_uuid, player_name, updated_at) VALUES (?, ?, ?, ?) AS new_val " +
                  "ON DUPLICATE KEY UPDATE online_uuid = new_val.online_uuid, player_name = new_val.player_name, updated_at = new_val.updated_at";
        } else {
            sql = "INSERT INTO " + tablePrefix + "uuid_mapping (offline_uuid, online_uuid, player_name, updated_at) VALUES (?, ?, ?, ?) " +
                  "ON CONFLICT(offline_uuid) DO UPDATE SET online_uuid = excluded.online_uuid, player_name = excluded.player_name, updated_at = excluded.updated_at";
        }
        dbManager.getWriteLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, offlineUuid.toString());
            stmt.setString(2, onlineUuid.toString());
            stmt.setString(3, playerName);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            dbManager.getPlugin().getLogger().warning("保存UUID映射失败: " + e.getMessage());
        } finally {
            dbManager.getWriteLock().unlock();
        }
    }

    public Map<UUID, UUID> loadAll() {
        Map<UUID, UUID> map = new HashMap<>();
        String sql = "SELECT offline_uuid, online_uuid FROM " + tablePrefix + "uuid_mapping";
        dbManager.getReadLock().lock();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                UUID offline = UUID.fromString(rs.getString("offline_uuid"));
                UUID online = UUID.fromString(rs.getString("online_uuid"));
                map.put(offline, online);
            }
        } catch (SQLException e) {
            dbManager.getPlugin().getLogger().warning("加载UUID映射失败: " + e.getMessage());
        } finally {
            dbManager.getReadLock().unlock();
        }
        return map;
    }
}
