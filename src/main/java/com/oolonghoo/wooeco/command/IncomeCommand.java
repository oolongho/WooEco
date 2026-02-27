package com.oolonghoo.wooeco.command;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.config.MessageManager;
import com.oolonghoo.wooeco.manager.EconomyManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * 收入快捷命令
 * 
 * @author oolongho
 */
public class IncomeCommand implements CommandExecutor {
    
    private final WooEco plugin;
    private final MessageManager messages;
    private final EconomyManager economyManager;
    
    public IncomeCommand(WooEco plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageManager();
        this.economyManager = plugin.getEconomyManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            messages.send(sender, "player-only");
            return true;
        }
        
        if (!sender.hasPermission("wooeco.income")) {
            messages.send(sender, "no-permission");
            return true;
        }
        
        Player player = (Player) sender;
        double income = economyManager.getDailyIncome(player.getUniqueId());
        String formatted = plugin.getCurrencyConfig().format(income);
        
        messages.send(sender, "income.today", Map.of(
            "symbol", messages.getSymbol(),
            "income", formatted
        ));
        
        return true;
    }
}
