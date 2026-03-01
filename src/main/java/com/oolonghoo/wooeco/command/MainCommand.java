package com.oolonghoo.wooeco.command;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.command.sub.*;
import com.oolonghoo.wooeco.config.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;

/**
 * 主命令路由器
 * 负责子命令的注册和分发
 */
public class MainCommand implements CommandExecutor, TabCompleter {
    
    private final WooEco plugin;
    private final MessageManager messages;
    private final Map<String, SubCommandHandler> handlers;
    
    public MainCommand(WooEco plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageManager();
        this.handlers = new LinkedHashMap<>();
        
        registerHandlers();
    }
    
    /**
     * 注册所有子命令处理器
     */
    private void registerHandlers() {
        register(new BalanceCommand(plugin));
        register(new PayCommandHandler(plugin));
        register(new IncomeCommandHandler(plugin));
        register(new TopCommand(plugin));
        register(new LookCommand(plugin));
        register(new HistoryCommand(plugin));
        register(new HelpCommand(plugin));
        
        register(new GiveCommand(plugin));
        register(new GiveAllCommand(plugin));
        register(new TakeCommand(plugin));
        register(new TakeAllCommand(plugin));
        register(new SetCommand(plugin));
        register(new SetAllCommand(plugin));
        register(new ReloadCommand(plugin));
        register(new DebugCommand(plugin));
    }
    
    /**
     * 注册子命令处理器
     */
    private void register(SubCommandHandler handler) {
        handlers.put(handler.getName().toLowerCase(), handler);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        long startTime = System.nanoTime();
        plugin.getDebugManager().command(sender, label, args);
        
        boolean result;
        if (args.length == 0) {
            SubCommandHandler balanceHandler = handlers.get("balance");
            result = balanceHandler != null && balanceHandler.execute(sender, args);
        } else {
            String subCommand = args[0].toLowerCase();
            SubCommandHandler handler = handlers.get(subCommand);
            
            if (handler == null) {
                sender.sendMessage(messages.getWithPrefix("unknown-command"));
                result = true;
            } else {
                if (!sender.hasPermission(handler.getPermission())) {
                    messages.send(sender, "no-permission");
                    result = true;
                } else {
                    String[] handlerArgs = Arrays.copyOfRange(args, 1, args.length);
                    result = handler.execute(sender, handlerArgs);
                }
            }
        }
        
        long elapsed = System.nanoTime() - startTime;
        plugin.getDebugManager().commandResult(sender, label + " " + String.join(" ", args), result, "completed", elapsed);
        
        return result;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            for (SubCommandHandler handler : handlers.values()) {
                if (sender.hasPermission(handler.getPermission())) {
                    completions.add(handler.getName());
                }
            }
        } else if (args.length > 1) {
            String subCommand = args[0].toLowerCase();
            SubCommandHandler handler = handlers.get(subCommand);
            if (handler != null && sender.hasPermission(handler.getPermission())) {
                String[] handlerArgs = Arrays.copyOfRange(args, 1, args.length);
                completions.addAll(handler.tabComplete(sender, handlerArgs));
            }
        }
        
        String input = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        
        return completions;
    }
    
    /**
     * 获取所有已注册的子命令处理器
     */
    public Collection<SubCommandHandler> getHandlers() {
        return handlers.values();
    }
}
