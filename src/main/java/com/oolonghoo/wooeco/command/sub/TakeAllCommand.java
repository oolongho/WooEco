package com.oolonghoo.wooeco.command.sub;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.api.events.BalanceChangeReason;
import com.oolonghoo.wooeco.command.AbstractSubCommandHandler;
import com.oolonghoo.wooeco.manager.EconomyManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 管理员批量扣除货币命令处理器
 */
public class TakeAllCommand extends AbstractSubCommandHandler {
    
    private final EconomyManager economyManager;
    
    public TakeAllCommand(WooEco plugin) {
        super(plugin);
        this.economyManager = plugin.getEconomyManager();
    }
    
    @Override
    public String getName() {
        return "takeall";
    }
    
    @Override
    public String getDescription() {
        return "扣除所有玩家货币";
    }
    
    @Override
    public String getPermission() {
        return "wooeco.admin.take";
    }
    
    @Override
    public boolean isAdminCommand() {
        return true;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "wooeco.admin.take")) {
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(messages.getWithPrefix("admin.takeall-usage"));
            return true;
        }
        
        String targetType = args[0].toLowerCase();
        if (!targetType.equals("all") && !targetType.equals("online")) {
            sender.sendMessage(messages.getWithPrefix("admin.takeall-usage"));
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
        
        EconomyManager.BatchResult result = economyManager.withdrawAll(
            BigDecimal.valueOf(amount), onlineOnly, operator, operatorName
        );
        
        String formatted = plugin.getCurrencyConfig().format(amount);
        messages.send(sender, "admin.takeall-success", Map.of(
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
