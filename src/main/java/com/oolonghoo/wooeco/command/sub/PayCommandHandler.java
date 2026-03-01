package com.oolonghoo.wooeco.command.sub;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.command.AbstractSubCommandHandler;
import com.oolonghoo.wooeco.manager.PlayerDataManager;
import com.oolonghoo.wooeco.manager.TransactionManager;
import com.oolonghoo.wooeco.model.PlayerAccount;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * 转账命令处理器
 */
public class PayCommandHandler extends AbstractSubCommandHandler {
    
    private final PlayerDataManager playerDataManager;
    private final TransactionManager transactionManager;
    
    public PayCommandHandler(WooEco plugin) {
        super(plugin);
        this.playerDataManager = plugin.getPlayerDataManager();
        this.transactionManager = plugin.getTransactionManager();
    }
    
    @Override
    public String getName() {
        return "pay";
    }
    
    @Override
    public String getDescription() {
        return "向其他玩家转账";
    }
    
    @Override
    public String getPermission() {
        return "wooeco.pay";
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) {
            return true;
        }
        
        if (!requirePermission(sender, "wooeco.pay")) {
            return true;
        }
        
        if (!checkCooldown(sender)) {
            return true;
        }
        
        if (args.length < 2) {
            messages.send(sender, "transaction.pay-usage", Map.of("command", "eco pay"));
            return true;
        }
        
        Player player = (Player) sender;
        String targetName = args[0];
        Double amount = parseAmount(args[1]);
        
        if (amount == null) {
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
                sender.sendMessage(messages.getPrefix() + "§c" + result.getErrorMessage());
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
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return getOnlinePlayerNames();
        }
        return List.of();
    }
}
