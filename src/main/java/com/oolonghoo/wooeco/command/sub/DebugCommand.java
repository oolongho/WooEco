package com.oolonghoo.wooeco.command.sub;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.command.AbstractSubCommandHandler;
import com.oolonghoo.wooeco.config.MessageManager;
import com.oolonghoo.wooeco.util.DebugManager;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.List;

public class DebugCommand extends AbstractSubCommandHandler {
    
    public DebugCommand(WooEco plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "debug";
    }
    
    @Override
    public String getDescription() {
        return "调试工具";
    }
    
    @Override
    public String getPermission() {
        return "wooeco.admin.debug";
    }
    
    @Override
    public boolean isAdminCommand() {
        return true;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "wooeco.admin.debug")) {
            return true;
        }
        
        DebugManager debug = plugin.getDebugManager();
        
        if (args.length < 1) {
            Audience audience = (Audience) sender;
            audience.sendMessage(Component.text("========== WooEco 调试帮助 ==========", NamedTextColor.YELLOW));
            audience.sendMessage(Component.text("/eco debug on ", NamedTextColor.YELLOW).append(Component.text("- 开启调试模式", NamedTextColor.GRAY)));
            audience.sendMessage(Component.text("/eco debug off ", NamedTextColor.YELLOW).append(Component.text("- 关闭调试模式", NamedTextColor.GRAY)));
            audience.sendMessage(Component.text("/eco debug status ", NamedTextColor.YELLOW).append(Component.text("- 查看状态诊断", NamedTextColor.GRAY)));
            audience.sendMessage(Component.text("/eco debug player <玩家> ", NamedTextColor.YELLOW).append(Component.text("- 查看玩家数据", NamedTextColor.GRAY)));
            audience.sendMessage(Component.text("/eco debug reload ", NamedTextColor.YELLOW).append(Component.text("- 重载调试配置", NamedTextColor.GRAY)));
            audience.sendMessage(Component.text("====================================", NamedTextColor.YELLOW));
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "on" -> {
                debug.setEnabled(true);
                ((Audience) sender).sendMessage(MessageManager.deserialize("&a[WooEco] 调试模式已启用"));
            }
            case "off" -> {
                debug.setEnabled(false);
                ((Audience) sender).sendMessage(MessageManager.deserialize("&c[WooEco] 调试模式已关闭"));
            }
            case "status" -> debug.dumpState(sender);
            case "player" -> {
                if (args.length < 2) {
                    ((Audience) sender).sendMessage(MessageManager.deserialize("&c用法: /eco debug player <玩家名>"));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                debug.dumpPlayerState(sender, target.getUniqueId());
            }
            case "reload" -> {
                debug.reload();
                ((Audience) sender).sendMessage(MessageManager.deserialize("&a[WooEco] 调试配置已重载"));
            }
            default -> ((Audience) sender).sendMessage(MessageManager.deserialize("&c未知的调试命令: " + subCommand));
        }
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("on", "off", "status", "player", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
            return getOnlinePlayerNames();
        }
        return List.of();
    }
}
