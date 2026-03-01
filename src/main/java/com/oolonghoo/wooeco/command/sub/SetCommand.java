package com.oolonghoo.wooeco.command.sub;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.api.events.BalanceChangeReason;
import com.oolonghoo.wooeco.command.AbstractSubCommandHandler;
import com.oolonghoo.wooeco.manager.EconomyManager;
import com.oolonghoo.wooeco.manager.PlayerDataManager;
import com.oolonghoo.wooeco.model.PlayerAccount;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 管理员设置余额命令处理器
 */
public class SetCommand extends AbstractSubCommandHandler {
    
    private final PlayerDataManager playerDataManager;
    private final EconomyManager economyManager;
    
    public SetCommand(WooEco plugin) {
        super(plugin);
        this.playerDataManager = plugin.getPlayerDataManager();
        this.economyManager = plugin.getEconomyManager();
    }
    
    @Override
    public String getName() {
        return "set";
    }
    
    @Override
    public String getDescription() {
        return "设置玩家余额";
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
            messages.send(sender, "admin.usage", Map.of("command", "eco set"));
            return true;
        }
        
        String targetName = args[0];
        double amount;
        
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            messages.send(sender, "invalid-number");
            return true;
        }
        
        if (amount < 0) {
            messages.send(sender, "invalid-amount");
            return true;
        }
        
        PlayerAccount account = playerDataManager.getAccount(targetName);
        if (account == null) {
            messages.send(sender, "player-not-found", Map.of("player", targetName));
            return true;
        }
        
        String operator = sender instanceof Player ? ((Player) sender).getUniqueId().toString() : "CONSOLE";
        String operatorName = sender.getName();
        
        EconomyManager.EconomyResult result = economyManager.set(
            account.getUuid(), BigDecimal.valueOf(amount), BalanceChangeReason.ADMIN, operator, operatorName
        );
        
        if (result.isSuccess()) {
            String formatted = plugin.getCurrencyConfig().format(amount);
            messages.send(sender, "admin.set-success", Map.of(
                "player", account.getPlayerName(),
                "symbol", messages.getSymbol(),
                "amount", formatted
            ));
        } else {
            sender.sendMessage(messages.getPrefix() + "§c" + result.getErrorMessage());
        }
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return getOnlinePlayerNames();
        }
        return List.of();
    }
}
