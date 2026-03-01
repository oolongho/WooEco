package com.oolonghoo.wooeco.command.sub;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.command.AbstractSubCommandHandler;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

/**
 * 帮助命令处理器
 */
public class HelpCommand extends AbstractSubCommandHandler {
    
    public HelpCommand(WooEco plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "help";
    }
    
    @Override
    public String getDescription() {
        return "查看帮助信息";
    }
    
    @Override
    public String getPermission() {
        return "wooeco.help";
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage(messages.get("help.header"));
        sender.sendMessage(messages.get("help.look", Map.of("command", "eco")));
        sender.sendMessage(messages.get("help.pay", Map.of("command", "eco")));
        sender.sendMessage(messages.get("help.income", Map.of("command", "eco")));
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
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
