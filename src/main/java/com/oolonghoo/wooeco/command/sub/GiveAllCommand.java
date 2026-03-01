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
 * 管理员批量发放货币命令处理器
 */
public class GiveAllCommand extends AbstractSubCommandHandler {
    
    private final EconomyManager economyManager;
    
    public GiveAllCommand(WooEco plugin) {
        super(plugin);
        this.economyManager = plugin.getEconomyManager();
    }
    
    @Override
    public String getName() {
        return "giveall";
    }
    
    @Override
    public String getDescription() {
        return "给所有玩家发放货币";
    }
    
    @Override
    public String getPermission() {
        return "wooeco.admin.give";
    }
    
    @Override
    public boolean isAdminCommand() {
        return true;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "wooeco.admin.give")) {
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(messages.getWithPrefix("admin.giveall-usage"));
            return true;
        }
        
        String targetType = args[0].toLowerCase();
        if (!targetType.equals("all") && !targetType.equals("online")) {
            sender.sendMessage(messages.getWithPrefix("admin.giveall-usage"));
            return true;
        }
        
        Double amount = parseAmount(args[1]);
        if (amount == null) {
            messages.send(sender, "invalid-amount");
            return true;
        }
        
        boolean onlineOnly = targetType.equals("online");
        String operator = sender instanceof Player ? ((Player) sender).getUniqueId().toString() : "CONSOLE";
        String operatorName = sender.getName();
        
        sender.sendMessage(messages.getWithPrefix("admin.batch-start"));
        
        long batchStart = System.nanoTime();
        EconomyManager.BatchResult result = economyManager.depositAll(
            BigDecimal.valueOf(amount), onlineOnly, operator, operatorName
        );
        long batchTime = System.nanoTime() - batchStart;
        
        plugin.getDebugManager().batchOperation("GIVEALL", result.getSuccessCount(), result.getFailedCount(), batchTime);
        
        String formatted = plugin.getCurrencyConfig().format(amount);
        messages.send(sender, "admin.giveall-success", Map.of(
            "count", String.valueOf(result.getSuccessCount()),
            "failed", String.valueOf(result.getFailedCount()),
            "symbol", messages.getSymbol(),
            "amount", formatted
        ));
        
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
