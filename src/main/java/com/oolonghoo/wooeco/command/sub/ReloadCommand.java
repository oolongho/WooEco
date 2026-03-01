package com.oolonghoo.wooeco.command.sub;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.command.AbstractSubCommandHandler;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * 重载配置命令处理器
 */
public class ReloadCommand extends AbstractSubCommandHandler {
    
    public ReloadCommand(WooEco plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "reload";
    }
    
    @Override
    public String getDescription() {
        return "重载插件配置";
    }
    
    @Override
    public String getPermission() {
        return "wooeco.admin.reload";
    }
    
    @Override
    public boolean isAdminCommand() {
        return true;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "wooeco.admin.reload")) {
            return true;
        }
        
        plugin.reload();
        messages.send(sender, "reload-success");
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
