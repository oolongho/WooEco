package com.oolonghoo.wooeco.command.sub;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.command.AbstractSubCommandHandler;
import com.oolonghoo.wooeco.manager.EconomyManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 管理员批量设置余额命令处理器
 */
public class SetAllCommand extends AbstractSubCommandHandler {
    
    private final EconomyManager economyManager;
    
    public SetAllCommand(WooEco plugin) {
        super(plugin);
        this.economyManager = plugin.getEconomyManager();
    }
    
    @Override
    public String getName() {
        return "setall";
    }
    
    @Override
    public String getDescription() {
        return "设置所有玩家余额";
    }
    
    @Override
    public String getPermission() {
        return "wooeco.admin.set";
    }
    
    @Override
    public boolean isAdminCommand() {
        return true;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "wooeco.admin.set")) {
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(messages.getWithPrefix("admin.setall-usage"));
            return true;
        }
        
        String targetType = args[0].toLowerCase();
        if (!targetType.equals("all") && !targetType.equals("online")) {
            sender.sendMessage(messages.getWithPrefix("admin.setall-usage"));
            return true;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            messages.send(sender, "invalid-number");
            return true;
        }
        
        if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            messages.send(sender, "invalid-amount");
            return true;
        }
        double maxBalance = plugin.getConfig().getDouble("currency.max-balance", 1e16);
        if (amount > maxBalance) {
            messages.send(sender, "invalid-amount");
            return true;
        }
        
        boolean onlineOnly = targetType.equals("online");
        String operator = sender instanceof Player ? ((Player) sender).getUniqueId().toString() : "CONSOLE";
        String operatorName = sender.getName();
        
        sender.sendMessage(messages.getWithPrefix("admin.batch-start"));
        String formatted = plugin.getCurrencyConfig().format(amount);
        
        if (plugin.getConfig().getBoolean("performance.batch-async", true)) {
            economyManager.setAllAsync(
                BigDecimal.valueOf(amount), onlineOnly, operator, operatorName,
                result -> messages.send(sender, "admin.setall-success", Map.of(
                    "count", String.valueOf(result.getSuccessCount()),
                    "failed", String.valueOf(result.getFailedCount()),
                    "symbol", messages.getSymbol(),
                    "amount", formatted
                ))
            );
        } else {
            EconomyManager.BatchResult result = economyManager.setAll(
                BigDecimal.valueOf(amount), onlineOnly, operator, operatorName
            );
            messages.send(sender, "admin.setall-success", Map.of(
                "count", String.valueOf(result.getSuccessCount()),
                "failed", String.valueOf(result.getFailedCount()),
                "symbol", messages.getSymbol(),
                "amount", formatted
            ));
        }
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("all", "online");
        }
        return List.of();
    }
}
