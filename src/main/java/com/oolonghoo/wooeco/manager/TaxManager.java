package com.oolonghoo.wooeco.manager;

import com.oolonghoo.wooeco.WooEco;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 税率管理器
 *
 */
public class TaxManager {
    
    private final WooEco plugin;
    
    private volatile UUID cachedTaxReceiverUUID = null;
    private volatile String cachedTaxReceiverName = null;
    private volatile boolean cachedTaxEnabled = true;
    private volatile double cachedTaxRate = 5;
    
    public TaxManager(WooEco plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 解析并缓存税收配置，应在插件加载和 reload 时调用
     */
    public void cacheTaxReceiver() {
        cachedTaxEnabled = plugin.getConfig().getBoolean("transaction.tax.enabled", true);
        cachedTaxRate = plugin.getConfig().getDouble("transaction.tax.rate", 5);

        String receiver = plugin.getConfig().getString("transaction.tax.receiver", null);
        if (receiver == null || receiver.isEmpty()) {
            cachedTaxReceiverUUID = null;
            cachedTaxReceiverName = null;
            return;
        }
        
        try {
            cachedTaxReceiverUUID = UUID.fromString(receiver);
            OfflinePlayer player = plugin.getServer().getOfflinePlayer(cachedTaxReceiverUUID);
            cachedTaxReceiverName = player.getName();
        } catch (IllegalArgumentException e) {
            // receiver 是玩家名而非 UUID
            Player player = plugin.getServer().getPlayer(receiver);
            if (player != null) {
                cachedTaxReceiverUUID = player.getUniqueId();
                cachedTaxReceiverName = player.getName();
            } else {
                OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(receiver);
                if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
                    cachedTaxReceiverUUID = offlinePlayer.getUniqueId();
                    cachedTaxReceiverName = receiver;
                } else {
                    plugin.getLogger().warning(String.format("[WooEco] 税收接收者 '%s' 未找到，请检查配置", receiver));
                    cachedTaxReceiverUUID = null;
                    cachedTaxReceiverName = null;
                }
            }
        }
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
        return cachedTaxEnabled;
    }
    
    public double getTaxRate() {
        return cachedTaxRate;
    }
    
    public boolean hasBypassTax(UUID uuid) {
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null) {
            return false;
        }
        return player.hasPermission("wooeco.bypass.tax");
    }
    
    public boolean isTaxDestroyed() {
        return cachedTaxReceiverUUID == null;
    }
    
    public UUID getTaxReceiverUUID() {
        return cachedTaxReceiverUUID;
    }
    
    public String getTaxReceiverName() {
        return cachedTaxReceiverName;
    }
}
