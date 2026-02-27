package com.oolonghoo.wooeco.command;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.config.MessageManager;
import com.oolonghoo.wooeco.manager.PlayerDataManager;
import com.oolonghoo.wooeco.manager.TransactionManager;
import com.oolonghoo.wooeco.model.PlayerAccount;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 转账快捷命令
 * 
 * @author oolongho
 */
public class PayCommand implements CommandExecutor, TabCompleter {
    
    private final WooEco plugin;
    private final MessageManager messages;
    private final PlayerDataManager playerDataManager;
    private final TransactionManager transactionManager;
    
    public PayCommand(WooEco plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.transactionManager = plugin.getTransactionManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            messages.send(sender, "player-only");
            return true;
        }
        
        if (!sender.hasPermission("wooeco.pay")) {
            messages.send(sender, "no-permission");
            return true;
        }
        
        if (args.length < 2) {
            messages.send(sender, "transaction.pay-usage", Map.of("command", "pay"));
            return true;
        }
        
        Player player = (Player) sender;
        String targetName = args[0];
        double amount;
        
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            messages.send(sender, "invalid-number");
            return true;
        }
        
        if (amount <= 0) {
            messages.send(sender, "invalid-amount");
            return true;
        }
        
        PlayerAccount targetAccount = playerDataManager.getAccount(targetName);
        if (targetAccount == null) {
            messages.send(sender, "player-not-found", Map.of("player", targetName));
            return true;
        }
        
        TransactionManager.TransactionResult result = transactionManager.transfer(
            player.getUniqueId(), targetAccount.getUuid(), amount
        );
        
        if (!result.isSuccess()) {
            if (result.getErrorMessage().contains("余额不足")) {
                double totalCost = amount + result.getTax();
                String formatted = plugin.getCurrencyConfig().format(totalCost);
                messages.send(sender, "transaction.pay-insufficient", Map.of(
                    "symbol", messages.getSymbol(),
                    "amount", formatted
                ));
            } else {
                messages.send(sender, "transaction.error", Map.of("error", result.getErrorMessage()));
            }
            return true;
        }
        
        String formattedAmount = plugin.getCurrencyConfig().format(result.getAmount());
        String formattedTax = plugin.getCurrencyConfig().format(result.getTax());
        
        messages.send(sender, "transaction.pay-success", Map.of(
            "player", targetAccount.getPlayerName(),
            "symbol", messages.getSymbol(),
            "amount", formattedAmount
        ));
        
        if (result.getTax() > 0) {
            messages.send(sender, "transaction.tax-deducted", Map.of(
                "symbol", messages.getSymbol(),
                "tax", formattedTax
            ));
        }
        
        Player targetPlayer = Bukkit.getPlayer(targetAccount.getUuid());
        if (targetPlayer != null) {
            messages.send(targetPlayer, "transaction.pay-received", Map.of(
                "player", player.getName(),
                "symbol", messages.getSymbol(),
                "amount", formattedAmount
            ));
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }
        
        String input = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        
        return completions;
    }
}
