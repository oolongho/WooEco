package com.oolonghoo.wooeco.manager;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.database.dao.PayToggleDAO;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PayToggleManager {
    private final WooEco plugin;
    private final PayToggleDAO payToggleDAO;
    private final Map<UUID, Boolean> toggleCache = new ConcurrentHashMap<>();

    public PayToggleManager(WooEco plugin) {
        this.plugin = plugin;
        this.payToggleDAO = plugin.getDatabaseManager().getPayToggleDAO();
    }

    public boolean isPayEnabled(UUID uuid) {
        Boolean cached = toggleCache.get(uuid);
        if (cached != null) {
            return cached;
        }
        boolean enabled = payToggleDAO.isEnabled(uuid);
        toggleCache.put(uuid, enabled);
        return enabled;
    }

    public void setPayEnabled(UUID uuid, boolean enabled) {
        toggleCache.put(uuid, enabled);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            payToggleDAO.setEnabled(uuid, enabled);
        });
    }

    public void toggle(UUID uuid) {
        setPayEnabled(uuid, !isPayEnabled(uuid));
    }
    
    public void removeFromCache(UUID uuid) {
        toggleCache.remove(uuid);
    }
}
