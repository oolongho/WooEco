package com.oolonghoo.wooeco.command.sub;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.command.AbstractSubCommandHandler;
import com.oolonghoo.wooeco.manager.EconomyManager;
import com.oolonghoo.wooeco.manager.PlayerDataManager;
import com.oolonghoo.wooeco.model.PlayerAccount;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * 余额查询命令
 */
public class BalanceCommand extends AbstractSubCommandHandler {
    
    private final PlayerDataManager playerDataManager;
    private final EconomyManager economyManager;
    
    public BalanceCommand(WooEco plugin) {
        super(plugin);
        this.playerDataManager = plugin.getPlayerDataManager();
        this.economyManager = plugin.getEconomyManager();
    }
    
    @Override
    public String getName() {
        return "balance";
    }
    
    @Override
    public String getDescription() {
        return "查询玩家余额";
    }
    
    @Override
    public String getPermission() {
        return "wooeco.balance";
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return showOwnBalance(sender);
        } else {
            return showOtherBalance(sender, args[0]);
        }
    }
    
    private boolean showOwnBalance(CommandSender sender) {
        if (!requirePlayer(sender)) {
            return true;
        }
        
        if (!requirePermission(sender, "wooeco.balance")) {
            return true;
        }
        
        Player player = (Player) sender;
        double balance = economyManager.getBalance(player.getUniqueId());
        String formatted = plugin.getCurrencyConfig().format(balance);
        
        sender.sendMessage(messages.getWithPrefix("currency.balance", Map.of(
            "symbol", messages.getSymbol(),
            "balance", formatted
        )));
        
        return true;
    }
    
    private boolean showOtherBalance(CommandSender sender, String targetName) {
        if (!requirePermission(sender, "wooeco.balance.other")) {
            return true;
        }
        
        PlayerAccount account = playerDataManager.getAccount(targetName);
        if (account == null) {
            messages.send(sender, "player-not-found", Map.of("player", targetName));
            return true;
        }
        
        String formatted = plugin.getCurrencyConfig().format(account.getBalanceDouble());
        sender.sendMessage(messages.getWithPrefix("currency.balance-other", Map.of(
            "player", account.getPlayerName(),
            "symbol", messages.getSymbol(),
            "balance", formatted
        )));
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission("wooeco.balance.other")) {
            return getOnlinePlayerNames();
        }
        return List.of();
    }
}
