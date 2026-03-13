package com.oolonghoo.wooeco.manager;

import com.oolonghoo.wooeco.WooEco;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 税率管理器
 * 
 */
public class TaxManager {
    
    private final WooEco plugin;
    
    public TaxManager(WooEco plugin) {
        this.plugin = plugin;
    }
    
    public double calculateTax(UUID uuid, double amount) {
        return calculateTaxDecimal(uuid, BigDecimal.valueOf(amount)).doubleValue();
    }
    
    public BigDecimal calculateTaxDecimal(UUID uuid, BigDecimal amount) {
        if (!isTaxEnabled()) {
            return BigDecimal.ZERO;
        }
        
        if (hasBypassTax(uuid)) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal rate = BigDecimal.valueOf(getTaxRate());
        return amount.multiply(rate).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }
    
    public boolean isTaxEnabled() {
        return plugin.getConfig().getBoolean("transaction.tax.enabled", true);
    }
    
    public double getTaxRate() {
        return plugin.getConfig().getDouble("transaction.tax.rate", 5);
    }
    
    public boolean hasBypassTax(UUID uuid) {
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null) {
            return false;
        }
        return player.hasPermission("wooeco.bypass.tax");
    }
    
    public String getTaxReceiver() {
        return plugin.getConfig().getString("transaction.tax.receiver", null);
    }
    
    public boolean isTaxDestroyed() {
        String receiver = getTaxReceiver();
        return receiver == null || receiver.isEmpty();
    }
    
    public UUID getTaxReceiverUUID() {
        String receiver = getTaxReceiver();
        if (receiver == null || receiver.isEmpty()) {
            return null;
        }
        
        try {
            return UUID.fromString(receiver);
        } catch (IllegalArgumentException e) {
            Player player = plugin.getServer().getPlayer(receiver);
            if (player != null) {
                return player.getUniqueId();
            }
            
            org.bukkit.OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(receiver);
            if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
                return offlinePlayer.getUniqueId();
            }
            
            plugin.getLogger().warning(String.format("[WooEco] 税收接收者 '%s' 未找到，请检查配置", receiver));
            return null;
        }
    }
    
    public String getTaxReceiverName() {
        String receiver = getTaxReceiver();
        if (receiver == null || receiver.isEmpty()) {
            return null;
        }
        
        try {
            UUID uuid = UUID.fromString(receiver);
            org.bukkit.OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
            return player.getName();
        } catch (IllegalArgumentException e) {
            return receiver;
        }
    }
}
