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
        if (!requirePermission(sender, "wooeco.balance.other")) {
            return true;
        }
        
        if (args.length < 1) {
            sender.sendMessage(messages.getWithPrefix("look.usage"));
            return true;
        }
        
        String targetName = args[0];
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
