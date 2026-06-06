package com.oolonghoo.wooeco.hook;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.manager.EconomyManager;
import com.oolonghoo.wooeco.manager.GlobalStatsManager;
import com.oolonghoo.wooeco.manager.LeaderboardManager;
import com.oolonghoo.wooeco.model.IncomePeriod;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * PlaceholderAPI 变量扩展
 */
public class PlaceholderAPIHook extends PlaceholderExpansion {
    
    private final WooEco plugin;
    
    public PlaceholderAPIHook(WooEco plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public @NotNull String getAuthor() {
        return "oolongho";
    }
    
    @Override
    public @NotNull String getIdentifier() {
        return "wooeco";
    }
    
    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true;
    }
    
    @Override
    public boolean canRegister() {
        return true;
    }
    
    @Override
    public boolean register() {
        boolean success = super.register();
        if (success) {
            plugin.getLogger().info("[WooEco] PlaceholderAPI 扩展已注册");
        } else {
            plugin.getLogger().warning("PlaceholderAPI扩展注册失败!");
        }
        return success;
    }
    
    @Override
    @Nullable
    public String onRequest(OfflinePlayer player, String identifier) {
        try {
            return handleRequest(player, identifier);
        } catch (Exception e) {
            plugin.getLogger().warning("处理PAPI变量时出错: " + identifier + " - " + e.getMessage());
            return "";
        }
    }
    
    private String handleRequest(OfflinePlayer player, String identifier) {
        EconomyManager economy = plugin.getEconomyManager();
        if (economy == null) {
            return "0";
        }
        
        if (identifier.equals("balance")) {
            if (player == null) return "0";
            return String.valueOf(economy.getBalance(player.getUniqueId()));
        }
        
        if (identifier.equals("balance_formatted")) {
            if (player == null) return "0";
            double balance = economy.getBalance(player.getUniqueId());
            return plugin.getCurrencyConfig().format(balance);
        }
        
        if (identifier.equals("balance_value")) {
            if (player == null) return "0";
            double balance = economy.getBalance(player.getUniqueId());
            return plugin.getCurrencyConfig().formatInput(BigDecimal.valueOf(balance)).toString();
        }
        
        if (identifier.equals("daily_income")) {
            if (player == null) return "0";
            return String.valueOf(economy.getDailyIncome(player.getUniqueId()));
        }
        
        if (identifier.equals("daily_income_formatted")) {
            if (player == null) return "0";
            return plugin.getCurrencyConfig().format(economy.getDailyIncomeDecimal(player.getUniqueId()));
        }
        
        if (identifier.equals("weekly_income")) {
            if (player == null) return "0";
            return economy.getWeeklyIncomeDecimalCached(player.getUniqueId()).toString();
        }
        
        if (identifier.equals("weekly_income_formatted")) {
            if (player == null) return "0";
            return plugin.getCurrencyConfig().format(economy.getWeeklyIncomeDecimalCached(player.getUniqueId()));
        }
        
        if (identifier.equals("monthly_income")) {
            if (player == null) return "0";
            return economy.getMonthlyIncomeDecimalCached(player.getUniqueId()).toString();
        }
        
        if (identifier.equals("monthly_income_formatted")) {
            if (player == null) return "0";
            return plugin.getCurrencyConfig().format(economy.getMonthlyIncomeDecimalCached(player.getUniqueId()));
        }
        
        if (identifier.equals("top_rank")) {
            if (player == null) return "-";
            return String.valueOf(getPlayerRank(player.getUniqueId()));
        }
        
        if (identifier.startsWith("top_rank_")) {
            String playerName = identifier.substring(9);
            LeaderboardManager lm = plugin.getLeaderboardManager();
            if (lm == null) return "-";
            return String.valueOf(lm.getBalanceRankByName(playerName));
        }
        
        if (identifier.startsWith("top_player_")) {
            String indexStr = identifier.substring(11);
            return getTopPlayer(indexStr, IncomePeriod.DAY);
        }
        
        if (identifier.startsWith("top_balance_formatted_")) {
            String indexStr = identifier.substring(22);
            return getTopBalance(indexStr, IncomePeriod.DAY, true);
        }
        
        if (identifier.startsWith("top_balance_")) {
            String indexStr = identifier.substring(12);
            return getTopBalance(indexStr, IncomePeriod.DAY, false);
        }
        
        if (identifier.startsWith("top_income_formatted_")) {
            String rest = identifier.substring(21);
            IncomePeriod period = IncomePeriod.DAY;
            String indexStr = rest;
            if (rest.startsWith("week_")) {
                period = IncomePeriod.WEEK;
                indexStr = rest.substring(5);
            } else if (rest.startsWith("month_")) {
                period = IncomePeriod.MONTH;
                indexStr = rest.substring(6);
            } else if (rest.startsWith("day_")) {
                period = IncomePeriod.DAY;
                indexStr = rest.substring(4);
            }
            return getTopIncome(indexStr, period, true);
        }
        
        if (identifier.startsWith("top_income_player_")) {
            String rest = identifier.substring(18);
            IncomePeriod period = IncomePeriod.DAY;
            String indexStr = rest;
            if (rest.startsWith("week_")) {
                period = IncomePeriod.WEEK;
                indexStr = rest.substring(5);
            } else if (rest.startsWith("month_")) {
                period = IncomePeriod.MONTH;
                indexStr = rest.substring(6);
            } else if (rest.startsWith("day_")) {
                period = IncomePeriod.DAY;
                indexStr = rest.substring(4);
            }
            return getTopIncomePlayer(indexStr, period);
        }
        
        if (identifier.startsWith("top_income_")) {
            String rest = identifier.substring(11);
            IncomePeriod period = IncomePeriod.DAY;
            String indexStr = rest;
            if (rest.startsWith("week_")) {
                period = IncomePeriod.WEEK;
                indexStr = rest.substring(5);
            } else if (rest.startsWith("month_")) {
                period = IncomePeriod.MONTH;
                indexStr = rest.substring(6);
            } else if (rest.startsWith("day_")) {
                period = IncomePeriod.DAY;
                indexStr = rest.substring(4);
            }
            return getTopIncome(indexStr, period, false);
        }
        
        if (identifier.equals("sum_balance")) {
            GlobalStatsManager stats = plugin.getGlobalStatsManager();
            if (stats == null) return "0";
            return stats.getTotalBalance().toString();
        }
        
        if (identifier.equals("sum_balance_formatted")) {
            GlobalStatsManager stats = plugin.getGlobalStatsManager();
            if (stats == null) return "0";
            return plugin.getCurrencyConfig().format(stats.getTotalBalance());
        }
        
        if (identifier.equals("player_count")) {
            GlobalStatsManager stats = plugin.getGlobalStatsManager();
            if (stats == null) return "0";
            return String.valueOf(stats.getAccountCount());
        }
        
        if (identifier.equals("pay_toggle")) {
            if (player == null) return "true";
            return String.valueOf(plugin.getPayToggleManager().isPayEnabled(player.getUniqueId()));
        }
        
        return null;
    }
    
    private int getPlayerRank(UUID uuid) {
        LeaderboardManager lm = plugin.getLeaderboardManager();
        if (lm == null) return -1;
        
        return lm.getBalanceRank(uuid);
    }
    
    private String getTopPlayer(String indexStr, IncomePeriod period) {
        try {
            int index = Integer.parseInt(indexStr);
            if (index < 1) return "-";
            
            LeaderboardManager lm = plugin.getLeaderboardManager();
            if (lm == null) return "-";
            
            String name = lm.getTopBalancePlayer(index);
            return name != null ? name : "-";
        } catch (NumberFormatException e) {
            return "-";
        }
    }
    
    private String getTopBalance(String indexStr, IncomePeriod period, boolean formatted) {
        try {
            int index = Integer.parseInt(indexStr);
            if (index < 1) return "0";
            
            LeaderboardManager lm = plugin.getLeaderboardManager();
            if (lm == null) return "0";
            
            double balance = lm.getTopBalanceAt(index);
            if (balance < 0) return "0";
            
            if (formatted) {
                return plugin.getCurrencyConfig().format(balance);
            }
            return String.valueOf(balance);
        } catch (NumberFormatException e) {
            return "0";
        }
    }
    
    private String getTopIncomePlayer(String indexStr, IncomePeriod period) {
        try {
            int index = Integer.parseInt(indexStr);
            if (index < 1) return "-";
            
            LeaderboardManager lm = plugin.getLeaderboardManager();
            if (lm == null) return "-";
            
            String name = lm.getTopIncomePlayerByPeriod(period, index);
            return name != null ? name : "-";
        } catch (NumberFormatException e) {
            return "-";
        }
    }
    
    private String getTopIncome(String indexStr, IncomePeriod period, boolean formatted) {
        try {
            int index = Integer.parseInt(indexStr);
            if (index < 1) return "0";
            
            LeaderboardManager lm = plugin.getLeaderboardManager();
            if (lm == null) return "0";
            
            // dailyIncome 字段在周/月排行上下文中存储的是对应周期的收入汇总值
            double income = lm.getTopIncomeAt(period, index);
            if (income < 0) return "0";
            
            if (formatted) {
                return plugin.getCurrencyConfig().format(income);
            }
            return String.valueOf(income);
        } catch (NumberFormatException e) {
            return "0";
        }
    }
}
