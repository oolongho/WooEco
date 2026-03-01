package com.oolonghoo.wooeco.command.sub;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.command.AbstractSubCommandHandler;
import com.oolonghoo.wooeco.util.DebugManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * 调试命令处理器
 */
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
            sender.sendMessage("§e========== WooEco 调试帮助 ==========");
            sender.sendMessage("§e/eco debug on §7- 开启调试模式");
            sender.sendMessage("§e/eco debug off §7- 关闭调试模式");
            sender.sendMessage("§e/eco debug status §7- 查看状态诊断");
            sender.sendMessage("§e/eco debug player <玩家> §7- 查看玩家数据");
            sender.sendMessage("§e/eco debug reload §7- 重载调试配置");
            sender.sendMessage("§e====================================");
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
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
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /eco debug player <玩家名>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
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
