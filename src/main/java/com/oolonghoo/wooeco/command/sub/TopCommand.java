package com.oolonghoo.wooeco.command.sub;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.command.AbstractSubCommandHandler;
import com.oolonghoo.wooeco.manager.LeaderboardManager;
import com.oolonghoo.wooeco.model.PlayerAccount;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

/**
 * 排行榜命令处理器
 */
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
        
        boolean incomeMode = false;
        int page = 1;
        
        if (args.length > 0) {
            String type = args[0].toLowerCase();
            if ("income".equals(type)) {
                incomeMode = true;
            } else if ("all".equals(type)) {
                incomeMode = false;
            } else {
                sender.sendMessage(messages.getWithPrefix("top.usage"));
                return true;
            }
            
            if (args.length > 1) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    page = 1;
                }
            }
        } else {
            sender.sendMessage(messages.getWithPrefix("top.usage"));
            return true;
        }
        
        if (page < 1) page = 1;
        
        int perPage = plugin.getConfig().getInt("leaderboard.per-page", 10);
        List<PlayerAccount> accounts;
        int totalPages;
        String title;
        
        if (incomeMode) {
            accounts = leaderboardManager.getIncomeTop(page, perPage);
            totalPages = leaderboardManager.getTotalIncomePages(perPage);
            title = "日收入排行榜";
        } else {
            accounts = leaderboardManager.getBalanceTop(page, perPage);
            totalPages = leaderboardManager.getTotalBalancePages(perPage);
            title = "财富排行榜";
        }
        
        sender.sendMessage(messages.get("top.header", Map.of("title", title)));
        
        if (accounts.isEmpty()) {
            messages.send(sender, "top.no-data");
        } else {
            int rank = (page - 1) * perPage + 1;
            for (PlayerAccount account : accounts) {
                double value = incomeMode ? account.getDailyIncomeDouble() : account.getBalanceDouble();
                String formatted = plugin.getCurrencyConfig().format(value);
                sender.sendMessage(messages.get("top.format", Map.of(
                    "rank", String.valueOf(rank),
                    "player", account.getPlayerName(),
                    "symbol", messages.getSymbol(),
                    "balance", formatted
                )));
                rank++;
            }
        }
        
        sender.sendMessage(messages.get("top.page-info", Map.of(
            "page", String.valueOf(page),
            "total", String.valueOf(Math.max(totalPages, 1))
        )));
        sender.sendMessage(messages.get("top.footer"));
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("all", "income");
        }
        if (args.length == 2) {
            return getPageCompletions(10);
        }
        return List.of();
    }
}
