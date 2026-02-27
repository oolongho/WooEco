package com.oolonghoo.wooeco.manager;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.database.dao.OfflineTransferTipDAO;
import com.oolonghoo.wooeco.model.OfflineTransferTip;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 离线交易提示管理器
 * 
 * @author oolongho
 */
public class OfflineTransferManager {
    
    private final WooEco plugin;
    private final OfflineTransferTipDAO tipDAO;
    
    public OfflineTransferManager(WooEco plugin) {
        this.plugin = plugin;
        this.tipDAO = plugin.getDatabaseManager().getOfflineTransferTipDAO();
    }
    
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("transaction.offline-transfer-tips", true);
    }
    
    public void recordOfflineTransfer(UUID receiverUuid, String senderName, double amount) {
        if (!isEnabled()) return;
        
        OfflineTransferTip tip = new OfflineTransferTip(receiverUuid, senderName, amount);
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                tipDAO.saveTip(tip);
            } catch (SQLException e) {
                plugin.getLogger().warning("保存离线交易提示失败: " + e.getMessage());
            }
        });
    }
    
    public void checkAndNotifyPlayer(Player player) {
        if (!isEnabled()) return;
        
        UUID uuid = player.getUniqueId();
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int count = tipDAO.getUnnotifiedCount(uuid);
                
                if (count > 0) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        notifyPlayer(player, count);
                    });
                    
                    tipDAO.markAsNotified(uuid);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("检查离线交易提示失败: " + e.getMessage());
            }
        });
    }
    
    private void notifyPlayer(Player player, int count) {
        String message = plugin.getMessageManager().getWithPrefix("offline-transfer.tips", Map.of(
            "count", String.valueOf(count)
        ));
        player.sendMessage(message);
    }
    
    public List<OfflineTransferTip> getUnnotifiedTips(UUID uuid) {
        try {
            return tipDAO.getUnnotifiedTips(uuid);
        } catch (SQLException e) {
            plugin.getLogger().warning("获取离线交易提示失败: " + e.getMessage());
            return List.of();
        }
    }
    
    public void cleanupOldTips() {
        int retentionDays = plugin.getConfig().getInt("logging.retention-days", 30);
        if (retentionDays <= 0) return;
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                tipDAO.cleanupOldTips(retentionDays);
            } catch (SQLException e) {
                plugin.getLogger().warning("清理离线交易提示失败: " + e.getMessage());
            }
        });
    }
}
