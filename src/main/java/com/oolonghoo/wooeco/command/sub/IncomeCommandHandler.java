package com.oolonghoo.wooeco.command.sub;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.command.AbstractSubCommandHandler;
import com.oolonghoo.wooeco.manager.EconomyManager;
import com.oolonghoo.wooeco.manager.PlayerDataManager;
import com.oolonghoo.wooeco.model.IncomePeriod;
import com.oolonghoo.wooeco.model.PlayerAccount;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        return "查询收入";
    }
    
    @Override
    public String getPermission() {
        return "wooeco.income";
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        IncomePeriod period = IncomePeriod.DAY;
        String targetName = null;
        
        int argIndex = 0;
        if (args.length > argIndex && IncomePeriod.isPeriodKeyword(args[argIndex])) {
            period = IncomePeriod.fromString(args[argIndex]);
            argIndex++;
        }
        
        if (args.length > argIndex) {
            if (!requirePermission(sender, "wooeco.income.other")) {
                return true;
            }
            targetName = args[argIndex];
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
        
        BigDecimal income = getIncome(uuid, period);
        String formatted = plugin.getCurrencyConfig().format(income);
        String messageKey = getMessageKey(period, targetName != null);
        
        if (targetName != null) {
            messages.send(sender, messageKey, Map.of(
                "player", playerName,
                "symbol", messages.getSymbol(),
                "income", formatted
            ));
        } else {
            messages.send(sender, messageKey, Map.of(
                "symbol", messages.getSymbol(),
                "income", formatted
            ));
        }
        
        return true;
    }
    
    private BigDecimal getIncome(UUID uuid, IncomePeriod period) {
        switch (period) {
            case WEEK:
                return economyManager.getWeeklyIncomeDecimal(uuid);
            case MONTH:
                return economyManager.getMonthlyIncomeDecimal(uuid);
            default:
                return economyManager.getDailyIncomeDecimal(uuid);
        }
    }
    
    private String getMessageKey(IncomePeriod period, boolean isOther) {
        switch (period) {
            case WEEK:
                return isOther ? "income.week-other" : "income.week";
            case MONTH:
                return isOther ? "income.month-other" : "income.month";
            default:
                return isOther ? "income.today-other" : "income.today";
        }
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(Arrays.asList("day", "week", "month"));
            if (sender.hasPermission("wooeco.income.other")) {
                completions.addAll(getOnlinePlayerNames());
            }
        } else if (args.length == 2 && IncomePeriod.isPeriodKeyword(args[0])) {
            if (sender.hasPermission("wooeco.income.other")) {
                completions.addAll(getOnlinePlayerNames());
            }
        }
        
        return completions;
    }
}
