package com.oolonghoo.wooeco.command;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 子命令处理器抽象基类
 * 提供通用的依赖注入和工具方法
 */
public abstract class AbstractSubCommandHandler implements SubCommandHandler {
    
    protected final WooEco plugin;
    protected final MessageManager messages;
    
    protected AbstractSubCommandHandler(WooEco plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageManager();
    }
    
    /**
     * 检查发送者是否为玩家
     */
    protected boolean requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            messages.send(sender, "player-only");
            return false;
        }
        return true;
    }
    
    /**
     * 检查发送者权限
     */
    protected boolean requirePermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            messages.send(sender, "no-permission");
            return false;
        }
        return true;
    }
    
    /**
     * 解析金额参数
     */
    protected Double parseAmount(String amountStr) {
        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                return null;
            }
            return amount;
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 获取在线玩家名称列表
     */
    protected List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取页码补全建议
     */
    protected List<String> getPageCompletions(int maxPage) {
        return java.util.stream.IntStream.rangeClosed(1, maxPage)
            .mapToObj(String::valueOf)
            .collect(Collectors.toList());
    }
}
