package com.oolonghoo.wooeco.command.sub;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.command.AbstractSubCommandHandler;
import com.oolonghoo.wooeco.manager.PlayerDataManager;
import com.oolonghoo.wooeco.model.PlayerAccount;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

/**
 * 查看他人余额命令处理器
 */
public class LookCommand extends AbstractSubCommandHandler {
    
    private final PlayerDataManager playerDataManager;
    
    public LookCommand(WooEco plugin) {
        super(plugin);
        this.playerDataManager = plugin.getPlayerDataManager();
    }
    
    @Override
    public String getName() {
        return "look";
    }
    
    @Override
    public String getDescription() {
        return "查看其他玩家的余额";
    }
    
    @Override
    public String getPermission() {
        return "wooeco.balance.other";
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
        
        if (!requirePermission(sender, "wooeco.balance.other")) {
            return true;
        }
        
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
        String playerName = player.getName();
        PlayerAccount account = playerDataManager.getAccount(playerName);
        if (account == null) {
            messages.send(sender, "player-not-found", Map.of("player", playerName));
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
        if (args.length == 1) {
            return getOnlinePlayerNames();
        }
        return List.of();
    }
}
