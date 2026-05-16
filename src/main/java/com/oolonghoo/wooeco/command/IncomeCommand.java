package com.oolonghoo.wooeco.command;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.config.MessageManager;
import com.oolonghoo.wooeco.manager.EconomyManager;
import com.oolonghoo.wooeco.manager.PlayerDataManager;
import com.oolonghoo.wooeco.model.PlayerAccount;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * 收入快捷命令
 * 
 */
public class IncomeCommand implements CommandExecutor {
    
    private final WooEco plugin;
    private final MessageManager messages;
    private final EconomyManager economyManager;
    private final PlayerDataManager playerDataManager;
    
    public IncomeCommand(WooEco plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageManager();
        this.economyManager = plugin.getEconomyManager();
        this.playerDataManager = plugin.getPlayerDataManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String targetName = null;
        
        if (args.length > 0) {
            if (!sender.hasPermission("wooeco.income.other")) {
                messages.send(sender, "no-permission");
                return true;
            }
            targetName = args[0];
        } else {
            if (!(sender instanceof Player)) {
                messages.send(sender, "player-only");
                return true;
            }
            if (!sender.hasPermission("wooeco.income")) {
                messages.send(sender, "no-permission");
                return true;
            }
        }
        
        UUID uuid;
        String playerName;
        
        if (targetName != null) {
            PlayerAccount account = playerDataManager.getAccount(targetName);
            if (account == null) {
                messages.send(sender, "player-not-found", Map.of("player", targetName));
                return true;
            }
            uuid = account.getUuid();
            playerName = account.getPlayerName();
        } else {
            Player player = (Player) sender;
            uuid = player.getUniqueId();
            playerName = player.getName();
        }
        
        double income = economyManager.getDailyIncome(uuid);
        String formatted = plugin.getCurrencyConfig().format(income);
        
        if (targetName != null) {
            messages.send(sender, "income.today-other", Map.of(
                "player", playerName,
                "symbol", messages.getSymbol(),
                "income", formatted
            ));
        } else {
            messages.send(sender, "income.today", Map.of(
                "symbol", messages.getSymbol(),
                "income", formatted
            ));
        }
        
        return true;
    }
}
