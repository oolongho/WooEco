package com.oolonghoo.wooeco.command.sub;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.command.AbstractSubCommandHandler;
import com.oolonghoo.wooeco.database.dao.TransactionDAO;
import com.oolonghoo.wooeco.manager.PlayerDataManager;
import com.oolonghoo.wooeco.model.PlayerAccount;
import com.oolonghoo.wooeco.model.Transaction;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 交易历史命令处理器
 */
public class HistoryCommand extends AbstractSubCommandHandler {
    
    private final PlayerDataManager playerDataManager;
    
    public HistoryCommand(WooEco plugin) {
        super(plugin);
        this.playerDataManager = plugin.getPlayerDataManager();
    }
    
    @Override
    public String getName() {
        return "history";
    }
    
    @Override
    public String getDescription() {
        return "查看交易历史";
    }
    
    @Override
    public String getPermission() {
        return "wooeco.history";
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        UUID targetUuid = null;
        String targetName = null;
        int page = 1;
        
        if (args.length == 0) {
            if (!requirePlayer(sender)) {
                return true;
            }
            if (!requirePermission(sender, "wooeco.history")) {
                return true;
            }
            if (!checkCooldown(sender)) {
                return true;
            }
            targetUuid = ((Player) sender).getUniqueId();
            targetName = sender.getName();
        } else if (args.length == 1) {
            try {
                page = Integer.parseInt(args[0]);
                if (!requirePlayer(sender)) {
                    return true;
                }
                if (!requirePermission(sender, "wooeco.history")) {
                    return true;
                }
                if (!checkCooldown(sender)) {
                    return true;
                }
                targetUuid = ((Player) sender).getUniqueId();
                targetName = sender.getName();
            } catch (NumberFormatException e) {
                if (!requirePermission(sender, "wooeco.history.other")) {
                    return true;
                }
                if (!checkCooldown(sender)) {
                    return true;
                }
                PlayerAccount account = playerDataManager.getAccount(args[0]);
                if (account == null) {
                    messages.send(sender, "player-not-found", Map.of("player", args[0]));
                    return true;
                }
                targetUuid = account.getUuid();
                targetName = account.getPlayerName();
            }
        } else if (args.length >= 2) {
            if (!requirePermission(sender, "wooeco.history.other")) {
                return true;
            }
            if (!checkCooldown(sender)) {
                return true;
            }
            PlayerAccount account = playerDataManager.getAccount(args[0]);
            if (account == null) {
                messages.send(sender, "player-not-found", Map.of("player", args[0]));
                return true;
            }
            targetUuid = account.getUuid();
            targetName = account.getPlayerName();
            try {
                page = Integer.parseInt(args[1]);
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
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (sender.hasPermission("wooeco.history.other")) {
                return getOnlinePlayerNames();
            }
            return getPageCompletions(10);
        }
        if (args.length == 2) {
            return getPageCompletions(10);
        }
        return List.of();
    }
}
