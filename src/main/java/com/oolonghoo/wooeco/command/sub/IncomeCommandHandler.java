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
import java.util.UUID;

/**
 * 收入查询命令处理器
 */
public class IncomeCommandHandler extends AbstractSubCommandHandler {
    
    private final PlayerDataManager playerDataManager;
    private final EconomyManager economyManager;
    
    public IncomeCommandHandler(WooEco plugin) {
        super(plugin);
        this.playerDataManager = plugin.getPlayerDataManager();
        this.economyManager = plugin.getEconomyManager();
    }
    
    @Override
    public String getName() {
        return "income";
    }
    
    @Override
    public String getDescription() {
        return "查询今日收入";
    }
    
    @Override
    public String getPermission() {
        return "wooeco.income";
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        String targetName = null;
        
        if (args.length > 0) {
            if (!requirePermission(sender, "wooeco.income.other")) {
                return true;
            }
            targetName = args[0];
        } else {
            if (!requirePlayer(sender)) {
                return true;
            }
            if (!requirePermission(sender, "wooeco.income")) {
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
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission("wooeco.income.other")) {
            return getOnlinePlayerNames();
        }
        return List.of();
    }
}
