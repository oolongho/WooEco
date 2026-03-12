package com.oolonghoo.wooeco.manager;

import com.oolonghoo.wooeco.WooEco;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 命令冷却管理器
 * 防止命令刷屏
 * 
 * @author oolongho
 */
public class CooldownManager {
    
    private final WooEco plugin;
    private boolean enabled;
    private final Map<String, Integer> cooldowns;
    private String cooldownMessage;
    private final Map<UUID, Map<String, Long>> playerCooldowns;
    
    public CooldownManager(WooEco plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
        this.playerCooldowns = new ConcurrentHashMap<>();
        loadConfig();
    }
    
    private void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("command-cooldown.enabled", true);
        this.cooldowns.clear();
        
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("command-cooldown.cooldowns");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                cooldowns.put(key.toLowerCase(), section.getInt(key, 0));
            }
        }
        
        this.cooldownMessage = plugin.getConfig().getString("command-cooldown.message", "&c请等待 %time% 秒后再次使用此命令");
    }
    
    public void reload() {
        loadConfig();
    }
    
    public boolean isOnCooldown(Player player, String command) {
        if (!enabled) {
            return false;
        }
        
        String cmd = command.toLowerCase();
        Integer cooldown = cooldowns.get(cmd);
        if (cooldown == null || cooldown <= 0) {
            return false;
        }
        
        UUID uuid = player.getUniqueId();
        Map<String, Long> playerCmdCooldowns = playerCooldowns.get(uuid);
        if (playerCmdCooldowns == null) {
            return false;
        }
        
        Long lastUse = playerCmdCooldowns.get(cmd);
        if (lastUse == null) {
            return false;
        }
        
        long elapsed = (System.currentTimeMillis() - lastUse) / 1000;
        return elapsed < cooldown;
    }
    
    public int getRemainingCooldown(Player player, String command) {
        if (!enabled) {
            return 0;
        }
        
        String cmd = command.toLowerCase();
        Integer cooldown = cooldowns.get(cmd);
        if (cooldown == null || cooldown <= 0) {
            return 0;
        }
        
        UUID uuid = player.getUniqueId();
        Map<String, Long> playerCmdCooldowns = playerCooldowns.get(uuid);
        if (playerCmdCooldowns == null) {
            return 0;
        }
        
        Long lastUse = playerCmdCooldowns.get(cmd);
        if (lastUse == null) {
            return 0;
        }
        
        long elapsed = (System.currentTimeMillis() - lastUse) / 1000;
        int remaining = (int) (cooldown - elapsed);
        return Math.max(0, remaining);
    }
    
    public void setCooldown(Player player, String command) {
        if (!enabled) {
            return;
        }
        
        String cmd = command.toLowerCase();
        Integer cooldown = cooldowns.get(cmd);
        if (cooldown == null || cooldown <= 0) {
            return;
        }
        
        UUID uuid = player.getUniqueId();
        playerCooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
            .put(cmd, System.currentTimeMillis());
    }
    
    public String getCooldownMessage(int remainingTime) {
        return cooldownMessage.replace("%time%", String.valueOf(remainingTime));
    }
    
    public void clearCooldown(Player player) {
        playerCooldowns.remove(player.getUniqueId());
    }
    
    public void clearAllCooldowns() {
        playerCooldowns.clear();
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public int getCooldown(String command) {
        return cooldowns.getOrDefault(command.toLowerCase(), 0);
    }
}
