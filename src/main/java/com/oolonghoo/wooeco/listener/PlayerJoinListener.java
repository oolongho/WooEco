package com.oolonghoo.wooeco.listener;

import com.oolonghoo.wooeco.WooEco;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家加入/退出监听器
 * 
 * @author oolongho
 */
public class PlayerJoinListener implements Listener {
    
    private final WooEco plugin;
    
    public PlayerJoinListener(WooEco plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getPlayerDataManager().loadPlayer(event.getPlayer().getUniqueId());
        
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getOfflineTransferManager().checkAndNotifyPlayer(event.getPlayer());
        }, 40L);
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getPlayerDataManager().unloadPlayer(event.getPlayer().getUniqueId());
    }
}
