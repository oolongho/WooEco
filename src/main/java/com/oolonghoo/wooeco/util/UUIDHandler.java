package com.oolonghoo.wooeco.util;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.config.UUIDMode;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * UUID模式处理器
 * 
 * @author oolongho
 */
public class UUIDHandler {
    
    private final WooEco plugin;
    
    public UUIDHandler(WooEco plugin) {
        this.plugin = plugin;
    }
    
    public UUID getUUID(String playerName) {
        UUIDMode mode = plugin.getDatabaseConfig().getUuidMode();
        return switch (mode) {
            case ONLINE -> getUUIDOnline(playerName);
            case OFFLINE -> getUUIDOffline(playerName);
            case DEFAULT -> getUUIDDefault(playerName);
        };
    }
    
    public UUID getUUID(Player player) {
        return player.getUniqueId();
    }
    
    private UUID getUUIDDefault(String playerName) {
        if (plugin.getServer().getOnlineMode()) {
            return getUUIDOnline(playerName);
        } else {
            return getUUIDOffline(playerName);
        }
    }
    
    private UUID getUUIDOnline(String playerName) {
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }
        
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        return offlinePlayer.getUniqueId();
    }
    
    private UUID getUUIDOffline(String playerName) {
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }
        
        boolean ignoreCase = plugin.getDatabaseConfig().isUsernameIgnoreCase();
        String searchName = ignoreCase ? playerName.toLowerCase() : playerName;
        
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            String name = offlinePlayer.getName();
            if (name != null) {
                if (ignoreCase) {
                    if (name.toLowerCase().equals(searchName)) {
                        return offlinePlayer.getUniqueId();
                    }
                } else {
                    if (name.equals(searchName)) {
                        return offlinePlayer.getUniqueId();
                    }
                }
            }
        }
        
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes());
    }
    
    public String getPlayerName(UUID uuid) {
        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            return onlinePlayer.getName();
        }
        
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        String name = offlinePlayer.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }
    
    public UUIDMode getMode() {
        return plugin.getDatabaseConfig().getUuidMode();
    }
    
    public boolean isOnlineMode() {
        UUIDMode mode = plugin.getDatabaseConfig().getUuidMode();
        return mode == UUIDMode.ONLINE || 
               (mode == UUIDMode.DEFAULT && plugin.getServer().getOnlineMode());
    }
}
