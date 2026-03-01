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
 * 管理员扣除货币命令处理器
 */
public class TakeCommand extends AbstractSubCommandHandler {
    
    private final PlayerDataManager playerDataManager;
    private final EconomyManager economyManager;
    
    public TakeCommand(WooEco plugin) {
        super(plugin);
        this.playerDataManager = plugin.getPlayerDataManager();
        this.economyManager = plugin.getEconomyManager();
    }
    
    @Override
    public String getName() {
        return "take";
    }
    
    @Override
    public String getDescription() {
        return "扣除玩家货币";
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
            messages.send(sender, "admin.usage", Map.of("command", "eco take"));
            return true;
        }
        
        String targetName = args[0];
        Double amount = parseAmount(args[1]);
        
        if (amount == null) {
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
        
        EconomyManager.EconomyResult result = economyManager.withdraw(
            account.getUuid(), BigDecimal.valueOf(amount), BalanceChangeReason.ADMIN, operator, operatorName
        );
        
        if (result.isSuccess()) {
            String formatted = plugin.getCurrencyConfig().format(amount);
            messages.send(sender, "admin.take-success", Map.of(
                "player", account.getPlayerName(),
                "symbol", messages.getSymbol(),
                "amount", formatted
            ));
        } else {
            String formatted = plugin.getCurrencyConfig().format(account.getBalanceDouble());
            messages.send(sender, "admin.take-insufficient", Map.of(
                "player", account.getPlayerName(),
                "symbol", messages.getSymbol(),
                "balance", formatted
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
