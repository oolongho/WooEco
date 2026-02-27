package com.oolonghoo.wooeco.command;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.api.events.BalanceChangeReason;
import com.oolonghoo.wooeco.config.MessageManager;
import com.oolonghoo.wooeco.database.dao.TransactionDAO;
import com.oolonghoo.wooeco.manager.EconomyManager;
import com.oolonghoo.wooeco.manager.LeaderboardManager;
import com.oolonghoo.wooeco.manager.PlayerDataManager;
import com.oolonghoo.wooeco.manager.TransactionManager;
import com.oolonghoo.wooeco.model.PlayerAccount;
import com.oolonghoo.wooeco.model.Transaction;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 主命令处理器
 * 
 * @author oolongho
 */
public class MainCommand implements CommandExecutor, TabCompleter {
    
    private final WooEco plugin;
    private final MessageManager messages;
    private final PlayerDataManager playerDataManager;
    private final EconomyManager economyManager;
    private final TransactionManager transactionManager;
    private final LeaderboardManager leaderboardManager;
    
    public MainCommand(WooEco plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.economyManager = plugin.getEconomyManager();
        this.transactionManager = plugin.getTransactionManager();
        this.leaderboardManager = plugin.getLeaderboardManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        long startTime = System.nanoTime();
        plugin.getDebugManager().command(sender, label, args);
        
        boolean result;
        if (args.length == 0) {
            result = handleBalance(sender, null);
        } else {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "pay":
                    result = handlePay(sender, args);
                    break;
                case "income":
                    result = handleIncome(sender, args);
                    break;
                case "top":
                    result = handleTop(sender, args);
                    break;
                case "look":
                    result = handleLook(sender, args);
                    break;
                case "history":
                    result = handleHistory(sender, args);
                    break;
                case "give":
                    result = handleGive(sender, args);
                    break;
                case "giveall":
                    result = handleGiveAll(sender, args);
                    break;
                case "take":
                    result = handleTake(sender, args);
                    break;
                case "takeall":
                    result = handleTakeAll(sender, args);
                    break;
                case "set":
                    result = handleSet(sender, args);
                    break;
                case "setall":
                    result = handleSetAll(sender, args);
                    break;
                case "reload":
                    result = handleReload(sender);
                    break;
                case "debug":
                    result = handleDebug(sender, args);
                    break;
                case "help":
                    result = handleHelp(sender);
                    break;
                default:
                    sender.sendMessage(messages.getWithPrefix("unknown-command"));
                    result = true;
            }
        }
        
        long elapsed = System.nanoTime() - startTime;
        plugin.getDebugManager().commandResult(sender, label + " " + String.join(" ", args), result, "completed", elapsed);
        
        return result;
    }
    
    private boolean handleBalance(CommandSender sender, String targetName) {
        if (targetName == null) {
            if (!(sender instanceof Player)) {
                messages.send(sender, "player-only");
                return true;
            }
            
            if (!sender.hasPermission("wooeco.balance")) {
                messages.send(sender, "no-permission");
                return true;
            }
            
            Player player = (Player) sender;
            double balance = economyManager.getBalance(player.getUniqueId());
            String formatted = plugin.getCurrencyConfig().format(balance);
            
            sender.sendMessage(messages.getWithPrefix("currency.balance", Map.of(
                "symbol", messages.getSymbol(),
                "balance", formatted
            )));
        } else {
            if (!sender.hasPermission("wooeco.balance.other")) {
                messages.send(sender, "no-permission");
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
        }
        return true;
    }
    
    private boolean handlePay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            messages.send(sender, "player-only");
            return true;
        }
        
        if (!sender.hasPermission("wooeco.pay")) {
            messages.send(sender, "no-permission");
            return true;
        }
        
        if (args.length < 3) {
            messages.send(sender, "transaction.pay-usage", Map.of("command", "eco"));
            return true;
        }
        
        Player player = (Player) sender;
        String targetName = args[1];
        double amount;
        
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            messages.send(sender, "invalid-number");
            return true;
        }
        
        if (amount <= 0) {
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
    
    private boolean handleIncome(CommandSender sender, String[] args) {
        String targetName = null;
        
        if (args.length > 1) {
            if (!sender.hasPermission("wooeco.income.other")) {
                messages.send(sender, "no-permission");
                return true;
            }
            targetName = args[1];
        } else {
            if (!(sender instanceof Player)) {
                messages.send(sender, "player-only");
                return true;
            }
            if (!sender.hasPermission("wooeco.income")) {
                messages.send(sender, "no-permission");
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
    
    private boolean handleTop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("wooeco.top")) {
            messages.send(sender, "no-permission");
            return true;
        }
        
        boolean incomeMode = false;
        int page = 1;
        
        if (args.length > 1) {
            String type = args[1].toLowerCase();
            if ("income".equals(type)) {
                incomeMode = true;
            } else if ("all".equals(type)) {
                incomeMode = false;
            } else {
                sender.sendMessage(messages.getWithPrefix("top.usage"));
                return true;
            }
            
            if (args.length > 2) {
                try {
                    page = Integer.parseInt(args[2]);
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
    
    private boolean handleLook(CommandSender sender, String[] args) {
        if (!sender.hasPermission("wooeco.balance.other")) {
            messages.send(sender, "no-permission");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(messages.getWithPrefix("look.usage"));
            return true;
        }
        
        String targetName = args[1];
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
    
    private boolean handleHistory(CommandSender sender, String[] args) {
        UUID targetUuid = null;
        String targetName = null;
        int page = 1;
        
        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                messages.send(sender, "player-only");
                return true;
            }
            if (!sender.hasPermission("wooeco.history")) {
                messages.send(sender, "no-permission");
                return true;
            }
            targetUuid = ((Player) sender).getUniqueId();
            targetName = sender.getName();
        } else if (args.length == 2) {
            try {
                page = Integer.parseInt(args[1]);
                if (!(sender instanceof Player)) {
                    messages.send(sender, "player-only");
                    return true;
                }
                if (!sender.hasPermission("wooeco.history")) {
                    messages.send(sender, "no-permission");
                    return true;
                }
                targetUuid = ((Player) sender).getUniqueId();
                targetName = sender.getName();
            } catch (NumberFormatException e) {
                if (!sender.hasPermission("wooeco.history.other")) {
                    messages.send(sender, "no-permission");
                    return true;
                }
                PlayerAccount account = playerDataManager.getAccount(args[1]);
                if (account == null) {
                    messages.send(sender, "player-not-found", Map.of("player", args[1]));
                    return true;
                }
                targetUuid = account.getUuid();
                targetName = account.getPlayerName();
            }
        } else if (args.length >= 3) {
            if (!sender.hasPermission("wooeco.history.other")) {
                messages.send(sender, "no-permission");
                return true;
            }
            PlayerAccount account = playerDataManager.getAccount(args[1]);
            if (account == null) {
                messages.send(sender, "player-not-found", Map.of("player", args[1]));
                return true;
            }
            targetUuid = account.getUuid();
            targetName = account.getPlayerName();
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                page = 1;
            }
        }
        
        if (page < 1) page = 1;
        
        final UUID uuid = targetUuid;
        final String name = targetName;
        final int currentPage = page;
        final int perPage = 10;
        final int offset = (page - 1) * perPage;
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                TransactionDAO transactionDAO = plugin.getDatabaseManager().getTransactionDAO();
                int total = transactionDAO.countTransactionsRelated(uuid);
                int totalPages = Math.max(1, (int) Math.ceil((double) total / perPage));
                
                if (currentPage > totalPages) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(messages.getWithPrefix("history.no-data"));
                    });
                    return;
                }
                
                List<Transaction> transactions = transactionDAO.getTransactionsRelated(uuid, offset, perPage);
                final int finalTotal = total;
                final int finalTotalPages = totalPages;
                
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(messages.get("history.header", Map.of("player", name)));
                    
                    if (transactions.isEmpty()) {
                        sender.sendMessage(messages.get("history.no-data"));
                    } else {
                        for (Transaction tx : transactions) {
                            boolean isSender = tx.getSenderUuid().equals(uuid);
                            String otherName = isSender ? tx.getReceiverName() : tx.getSenderName();
                            String direction = isSender ? messages.get("history.direction-send") : messages.get("history.direction-receive");
                            String action = isSender ? messages.get("history.action-send") : messages.get("history.action-receive");
                            String formattedAmount = plugin.getCurrencyConfig().format(tx.getAmount());
                            String time = formatTime(tx.getTimestamp());
                            
                            sender.sendMessage(messages.get("history.format", Map.of(
                                "direction", direction,
                                "action", action,
                                "player", otherName,
                                "symbol", messages.getSymbol(),
                                "amount", formattedAmount,
                                "time", time
                            )));
                        }
                    }
                    
                    sender.sendMessage(messages.get("history.page-info", Map.of(
                        "page", String.valueOf(currentPage),
                        "total", String.valueOf(finalTotalPages),
                        "count", String.valueOf(finalTotal)
                    )));
                    sender.sendMessage(messages.get("history.footer"));
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("查询交易历史失败: " + e.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(messages.getWithPrefix("history.error"));
                });
            }
        });
        
        return true;
    }
    
    private String formatTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long minutes = diff / (60 * 1000);
        long hours = diff / (60 * 60 * 1000);
        long days = diff / (24 * 60 * 60 * 1000);
        
        if (days > 0) {
            return messages.get("time.days-ago", Map.of("days", String.valueOf(days)));
        } else if (hours > 0) {
            return messages.get("time.hours-ago", Map.of("hours", String.valueOf(hours)));
        } else if (minutes > 0) {
            return messages.get("time.minutes-ago", Map.of("minutes", String.valueOf(minutes)));
        } else {
            return messages.get("time.just-now");
        }
    }
    
    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("wooeco.admin.give")) {
            messages.send(sender, "no-permission");
            return true;
        }
        
        if (args.length < 3) {
            messages.send(sender, "admin.usage", Map.of("command", "eco"));
            return true;
        }
        
        String targetName = args[1];
        double amount;
        
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            messages.send(sender, "invalid-number");
            return true;
        }
        
        if (amount <= 0) {
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
        
        EconomyManager.EconomyResult result = economyManager.deposit(
            account.getUuid(), java.math.BigDecimal.valueOf(amount), BalanceChangeReason.ADMIN, operator, operatorName
        );
        
        if (result.isSuccess()) {
            String formatted = plugin.getCurrencyConfig().format(amount);
            messages.send(sender, "admin.give-success", Map.of(
                "player", account.getPlayerName(),
                "symbol", messages.getSymbol(),
                "amount", formatted
            ));
        } else {
            sender.sendMessage(messages.getPrefix() + "§c" + result.getErrorMessage());
        }
        
        return true;
    }
    
    private boolean handleTake(CommandSender sender, String[] args) {
        if (!sender.hasPermission("wooeco.admin.take")) {
            messages.send(sender, "no-permission");
            return true;
        }
        
        if (args.length < 3) {
            messages.send(sender, "admin.usage", Map.of("command", "eco"));
            return true;
        }
        
        String targetName = args[1];
        double amount;
        
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            messages.send(sender, "invalid-number");
            return true;
        }
        
        if (amount <= 0) {
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
            account.getUuid(), java.math.BigDecimal.valueOf(amount), BalanceChangeReason.ADMIN, operator, operatorName
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
    
    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("wooeco.admin.set")) {
            messages.send(sender, "no-permission");
            return true;
        }
        
        if (args.length < 3) {
            messages.send(sender, "admin.usage", Map.of("command", "eco"));
            return true;
        }
        
        String targetName = args[1];
        double amount;
        
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            messages.send(sender, "invalid-number");
            return true;
        }
        
        if (amount < 0) {
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
        
        EconomyManager.EconomyResult result = economyManager.set(
            account.getUuid(), java.math.BigDecimal.valueOf(amount), BalanceChangeReason.ADMIN, operator, operatorName
        );
        
        if (result.isSuccess()) {
            String formatted = plugin.getCurrencyConfig().format(amount);
            messages.send(sender, "admin.set-success", Map.of(
                "player", account.getPlayerName(),
                "symbol", messages.getSymbol(),
                "amount", formatted
            ));
        } else {
            sender.sendMessage(messages.getPrefix() + "§c" + result.getErrorMessage());
        }
        
        return true;
    }
    
    private boolean handleGiveAll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("wooeco.admin.give")) {
            messages.send(sender, "no-permission");
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(messages.getWithPrefix("admin.giveall-usage"));
            return true;
        }
        
        String targetType = args[1].toLowerCase();
        if (!targetType.equals("all") && !targetType.equals("online")) {
            sender.sendMessage(messages.getWithPrefix("admin.giveall-usage"));
            return true;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            messages.send(sender, "invalid-number");
            return true;
        }
        
        if (amount <= 0) {
            messages.send(sender, "invalid-amount");
            return true;
        }
        
        boolean onlineOnly = targetType.equals("online");
        String operator = sender instanceof Player ? ((Player) sender).getUniqueId().toString() : "CONSOLE";
        String operatorName = sender.getName();
        
        sender.sendMessage(messages.getWithPrefix("admin.batch-start"));
        
        long batchStart = System.nanoTime();
        EconomyManager.BatchResult result = economyManager.depositAll(
            java.math.BigDecimal.valueOf(amount), onlineOnly, operator, operatorName
        );
        long batchTime = System.nanoTime() - batchStart;
        
        plugin.getDebugManager().batchOperation("GIVEALL", result.getSuccessCount(), result.getFailedCount(), batchTime);
        
        String formatted = plugin.getCurrencyConfig().format(amount);
        messages.send(sender, "admin.giveall-success", Map.of(
            "count", String.valueOf(result.getSuccessCount()),
            "failed", String.valueOf(result.getFailedCount()),
            "symbol", messages.getSymbol(),
            "amount", formatted
        ));
        
        return true;
    }
    
    private boolean handleTakeAll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("wooeco.admin.take")) {
            messages.send(sender, "no-permission");
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(messages.getWithPrefix("admin.takeall-usage"));
            return true;
        }
        
        String targetType = args[1].toLowerCase();
        if (!targetType.equals("all") && !targetType.equals("online")) {
            sender.sendMessage(messages.getWithPrefix("admin.takeall-usage"));
            return true;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            messages.send(sender, "invalid-number");
            return true;
        }
        
        if (amount <= 0) {
            messages.send(sender, "invalid-amount");
            return true;
        }
        
        boolean onlineOnly = targetType.equals("online");
        String operator = sender instanceof Player ? ((Player) sender).getUniqueId().toString() : "CONSOLE";
        String operatorName = sender.getName();
        
        sender.sendMessage(messages.getWithPrefix("admin.batch-start"));
        
        EconomyManager.BatchResult result = economyManager.withdrawAll(
            java.math.BigDecimal.valueOf(amount), onlineOnly, operator, operatorName
        );
        
        String formatted = plugin.getCurrencyConfig().format(amount);
        messages.send(sender, "admin.takeall-success", Map.of(
            "count", String.valueOf(result.getSuccessCount()),
            "failed", String.valueOf(result.getFailedCount()),
            "symbol", messages.getSymbol(),
            "amount", formatted
        ));
        
        return true;
    }
    
    private boolean handleSetAll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("wooeco.admin.set")) {
            messages.send(sender, "no-permission");
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(messages.getWithPrefix("admin.setall-usage"));
            return true;
        }
        
        String targetType = args[1].toLowerCase();
        if (!targetType.equals("all") && !targetType.equals("online")) {
            sender.sendMessage(messages.getWithPrefix("admin.setall-usage"));
            return true;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            messages.send(sender, "invalid-number");
            return true;
        }
        
        if (amount < 0) {
            messages.send(sender, "invalid-amount");
            return true;
        }
        
        boolean onlineOnly = targetType.equals("online");
        String operator = sender instanceof Player ? ((Player) sender).getUniqueId().toString() : "CONSOLE";
        String operatorName = sender.getName();
        
        sender.sendMessage(messages.getWithPrefix("admin.batch-start"));
        
        EconomyManager.BatchResult result = economyManager.setAll(
            java.math.BigDecimal.valueOf(amount), onlineOnly, operator, operatorName
        );
        
        String formatted = plugin.getCurrencyConfig().format(amount);
        messages.send(sender, "admin.setall-success", Map.of(
            "count", String.valueOf(result.getSuccessCount()),
            "failed", String.valueOf(result.getFailedCount()),
            "symbol", messages.getSymbol(),
            "amount", formatted
        ));
        
        return true;
    }
    
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("wooeco.admin.reload")) {
            messages.send(sender, "no-permission");
            return true;
        }
        
        plugin.reload();
        messages.send(sender, "reload-success");
        return true;
    }
    
    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("wooeco.admin.debug")) {
            messages.send(sender, "no-permission");
            return true;
        }
        
        com.oolonghoo.wooeco.util.DebugManager debug = plugin.getDebugManager();
        
        if (args.length < 2) {
            sender.sendMessage("§e========== WooEco 调试帮助 ==========");
            sender.sendMessage("§e/eco debug on §7- 开启调试模式");
            sender.sendMessage("§e/eco debug off §7- 关闭调试模式");
            sender.sendMessage("§e/eco debug status §7- 查看状态诊断");
            sender.sendMessage("§e/eco debug player <玩家> §7- 查看玩家数据");
            sender.sendMessage("§e/eco debug reload §7- 重载调试配置");
            sender.sendMessage("§e====================================");
            return true;
        }
        
        String subCommand = args[1].toLowerCase();
        
        switch (subCommand) {
            case "on":
                debug.setEnabled(true);
                sender.sendMessage("§a[WooEco] 调试模式已启用");
                break;
            case "off":
                debug.setEnabled(false);
                sender.sendMessage("§c[WooEco] 调试模式已关闭");
                break;
            case "status":
                debug.dumpState(sender);
                break;
            case "player":
                if (args.length < 3) {
                    sender.sendMessage("§c用法: /eco debug player <玩家名>");
                    return true;
                }
                org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                debug.dumpPlayerState(sender, target.getUniqueId());
                break;
            case "reload":
                debug.reload();
                sender.sendMessage("§a[WooEco] 调试配置已重载");
                break;
            default:
                sender.sendMessage("§c未知的调试命令: " + subCommand);
        }
        
        return true;
    }
    
    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage(messages.get("help.header"));
        sender.sendMessage(messages.get("help.balance", Map.of("command", "eco")));
        sender.sendMessage(messages.get("help.pay", Map.of("command", "eco")));
        sender.sendMessage(messages.get("help.income", Map.of("command", "eco")));
        sender.sendMessage(messages.get("help.look", Map.of("command", "eco")));
        sender.sendMessage(messages.get("help.top", Map.of("command", "eco")));
        sender.sendMessage(messages.get("help.history", Map.of("command", "eco")));
        
        if (sender.hasPermission("wooeco.admin")) {
            sender.sendMessage(messages.get("help.admin-give", Map.of("command", "eco")));
            sender.sendMessage(messages.get("help.admin-giveall", Map.of("command", "eco")));
            sender.sendMessage(messages.get("help.admin-take", Map.of("command", "eco")));
            sender.sendMessage(messages.get("help.admin-takeall", Map.of("command", "eco")));
            sender.sendMessage(messages.get("help.admin-set", Map.of("command", "eco")));
            sender.sendMessage(messages.get("help.admin-setall", Map.of("command", "eco")));
            sender.sendMessage(messages.get("help.reload", Map.of("command", "eco")));
        }
        
        sender.sendMessage(messages.get("help.footer"));
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("pay");
            completions.add("income");
            completions.add("top");
            completions.add("look");
            completions.add("help");
            completions.add("history");
            
            if (sender.hasPermission("wooeco.admin.give")) completions.add("give");
            if (sender.hasPermission("wooeco.admin.give")) completions.add("giveall");
            if (sender.hasPermission("wooeco.admin.take")) completions.add("take");
            if (sender.hasPermission("wooeco.admin.take")) completions.add("takeall");
            if (sender.hasPermission("wooeco.admin.set")) completions.add("set");
            if (sender.hasPermission("wooeco.admin.set")) completions.add("setall");
            if (sender.hasPermission("wooeco.admin.reload")) completions.add("reload");
            if (sender.hasPermission("wooeco.admin.debug")) completions.add("debug");
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            if ("pay".equals(subCommand) || "give".equals(subCommand) || 
                "take".equals(subCommand) || "set".equals(subCommand) || "look".equals(subCommand)) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            } else if ("history".equals(subCommand)) {
                if (sender.hasPermission("wooeco.history.other")) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        completions.add(player.getName());
                    }
                }
                for (int i = 1; i <= 10; i++) {
                    completions.add(String.valueOf(i));
                }
            } else if ("top".equals(subCommand)) {
                completions.add("all");
                completions.add("income");
            } else if ("giveall".equals(subCommand) || "takeall".equals(subCommand) || "setall".equals(subCommand)) {
                completions.add("all");
                completions.add("online");
            } else if ("debug".equals(subCommand)) {
                completions.add("on");
                completions.add("off");
                completions.add("status");
                completions.add("player");
                completions.add("reload");
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            
            if ("top".equals(subCommand) || "history".equals(subCommand)) {
                for (int i = 1; i <= 10; i++) {
                    completions.add(String.valueOf(i));
                }
            }
        }
        
        String input = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        
        return completions;
    }
}
