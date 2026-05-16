package com.oolonghoo.wooeco.command.sub;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.command.AbstractSubCommandHandler;
import com.oolonghoo.wooeco.config.MessageManager;
import com.oolonghoo.wooeco.manager.LeaderboardManager;
import com.oolonghoo.wooeco.model.IncomePeriod;
import com.oolonghoo.wooeco.model.PlayerAccount;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TopCommand extends AbstractSubCommandHandler {
    
    private final LeaderboardManager leaderboardManager;
    
    public TopCommand(WooEco plugin) {
        super(plugin);
        this.leaderboardManager = plugin.getLeaderboardManager();
    }
    
    @Override
    public String getName() {
        return "top";
    }
    
    @Override
    public String getDescription() {
        return "查看财富排行榜";
    }
    
    @Override
    public String getPermission() {
        return "wooeco.top";
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "wooeco.top")) {
            return true;
        }
        
        if (!checkCooldown(sender)) {
            return true;
        }
        
        if (args.length == 0) {
            ((Audience) sender).sendMessage(MessageManager.deserialize(messages.getWithPrefix("top.usage")));
            return true;
        }
        
        String type = args[0].toLowerCase();
        boolean incomeMode;
        IncomePeriod incomePeriod = IncomePeriod.DAY;
        
        if (type.equals("all")) {
            incomeMode = false;
        } else if (type.equals("income")) {
            incomeMode = true;
            if (args.length > 1 && IncomePeriod.isPeriodKeyword(args[1])) {
                incomePeriod = IncomePeriod.fromString(args[1]);
            }
        } else {
            ((Audience) sender).sendMessage(MessageManager.deserialize(messages.getWithPrefix("top.usage")));
            return true;
        }
        
        int page = 1;
        int pageIdx = incomeMode && incomePeriod != IncomePeriod.DAY ? 2 : 1;
        if (pageIdx < args.length) {
            try {
                page = Integer.parseInt(args[pageIdx]);
            } catch (NumberFormatException ignored) {
            }
        }
        
        if (page < 1) page = 1;
        
        int perPage = plugin.getConfig().getInt("leaderboard.per-page", 10);
        List<PlayerAccount> accounts;
        int totalPages;
        String title;
        
        if (incomeMode) {
            accounts = leaderboardManager.getIncomeTopByPeriod(incomePeriod, page, perPage);
            totalPages = leaderboardManager.getTotalIncomePagesByPeriod(incomePeriod, perPage);
            title = switch (incomePeriod) {
                case WEEK -> "周收入排行榜";
                case MONTH -> "月收入排行榜";
                default -> "日收入排行榜";
            };
        } else {
            accounts = leaderboardManager.getBalanceTop(page, perPage);
            totalPages = leaderboardManager.getTotalBalancePages(perPage);
            title = "财富排行榜";
        }
        
        ((Audience) sender).sendMessage(MessageManager.deserialize(messages.get("top.header", Map.of("title", title))));
        
        if (accounts.isEmpty()) {
            messages.send(sender, "top.no-data");
        } else {
            int rank = (page - 1) * perPage + 1;
            for (PlayerAccount account : accounts) {
                double value = incomeMode ? account.getDailyIncomeDouble() : account.getBalanceDouble();
                String formatted = plugin.getCurrencyConfig().format(value);
                ((Audience) sender).sendMessage(MessageManager.deserialize(messages.get("top.format", Map.of(
                    "rank", String.valueOf(rank),
                    "player", account.getPlayerName(),
                    "symbol", messages.getSymbol(),
                    "balance", formatted
                ))));
                rank++;
            }
        }
        
        ((Audience) sender).sendMessage(MessageManager.deserialize(messages.get("top.page-info", Map.of(
            "page", String.valueOf(page),
            "total", String.valueOf(Math.max(totalPages, 1))
        ))));
        ((Audience) sender).sendMessage(MessageManager.deserialize(messages.get("top.footer")));
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("all", "income");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("income")) {
            return Arrays.asList("day", "week", "month");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("all")) {
            return getPageCompletions(10);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("income") && IncomePeriod.isPeriodKeyword(args[1])) {
            return getPageCompletions(10);
        }
        return List.of();
    }
}
