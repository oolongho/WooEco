package com.oolonghoo.wooeco.command;

import com.oolonghoo.wooeco.WooEco;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 命令别名管理器
 * 动态注册和管理命令别名
 * 
 * @author oolongho
 */
public class CommandAliasManager {
    
    private final WooEco plugin;
    private final MainCommand mainCommand;
    private final Map<String, Command> registeredAliases = new HashMap<>();
    private CommandMap commandMap;
    
    public CommandAliasManager(WooEco plugin, MainCommand mainCommand) {
        this.plugin = plugin;
        this.mainCommand = mainCommand;
        this.commandMap = getCommandMap();
    }
    
    public void registerAliases() {
        List<String> aliases = plugin.getConfig().getStringList("currency.aliases");
        
        if (aliases.isEmpty()) {
            aliases = Arrays.asList("money", "bal", "coin");
        }
        
        for (String alias : aliases) {
            registerAlias(alias);
        }
        
        plugin.getLogger().info("已注册 " + registeredAliases.size() + " 个命令别名: " + String.join(", ", registeredAliases.keySet()));
    }
    
    public void registerAlias(String alias) {
        if (commandMap == null) {
            plugin.getLogger().warning("无法获取 CommandMap，跳过别名注册: " + alias);
            return;
        }
        
        String aliasLower = alias.toLowerCase();
        
        if (registeredAliases.containsKey(aliasLower)) {
            return;
        }
        
        if (commandMap.getCommand(aliasLower) != null) {
            plugin.getLogger().fine("命令别名已存在，跳过: " + alias);
            return;
        }
        
        DynamicCommand dynamicCommand = new DynamicCommand(aliasLower, mainCommand);
        
        try {
            commandMap.register(plugin.getDescription().getName(), dynamicCommand);
            registeredAliases.put(aliasLower, dynamicCommand);
        } catch (Exception e) {
            plugin.getLogger().warning("注册命令别名失败: " + alias + " - " + e.getMessage());
        }
    }
    
    public void unregisterAllAliases() {
        if (commandMap == null) return;
        
        try {
            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);
            
            for (String alias : registeredAliases.keySet()) {
                knownCommands.remove(alias);
                knownCommands.remove(plugin.getDescription().getName().toLowerCase() + ":" + alias);
            }
            
            registeredAliases.clear();
        } catch (Exception e) {
            plugin.getLogger().warning("注销命令别名失败: " + e.getMessage());
        }
    }
    
    public void reloadAliases() {
        unregisterAllAliases();
        registerAliases();
    }
    
    private CommandMap getCommandMap() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            return (CommandMap) commandMapField.get(Bukkit.getServer());
        } catch (Exception e) {
            plugin.getLogger().severe("无法获取 CommandMap: " + e.getMessage());
            return null;
        }
    }
    
    public Set<String> getRegisteredAliases() {
        return Collections.unmodifiableSet(registeredAliases.keySet());
    }
    
    private static class DynamicCommand extends Command {
        
        private final MainCommand executor;
        
        protected DynamicCommand(String name, MainCommand executor) {
            super(name);
            this.executor = executor;
            this.setDescription("WooEco economy command");
            this.setUsage("/<command> [args]");
        }
        
        @Override
        public boolean execute(org.bukkit.command.CommandSender sender, String commandLabel, String[] args) {
            return executor.onCommand(sender, this, commandLabel, args);
        }
        
        @Override
        public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
            return executor.onTabComplete(sender, this, alias, args);
        }
    }
}
