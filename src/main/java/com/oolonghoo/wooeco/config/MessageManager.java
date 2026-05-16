package com.oolonghoo.wooeco.config;

import com.oolonghoo.wooeco.WooEco;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageManager extends ConfigLoader {
    
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    
    private String prefix;
    private final WooEco wooEco;
    
    public MessageManager(WooEco plugin) {
        super(plugin, "lang/" + plugin.getConfig().getString("settings.language", "zh-CN") + ".yml");
        this.wooEco = plugin;
    }
    
    @Override
    protected void loadValues() {
        super.loadValues();
        this.prefix = translateColors(config.getString("prefix", "&8[&6WooEco&8] &r"));
    }
    
    public String get(String path) {
        String message = config.getString(path, "");
        return translateColors(message);
    }
    
    public String getWithPrefix(String path) {
        return prefix + get(path);
    }
    
    public String get(String path, Map<String, String> placeholders) {
        String message = get(path);
        return replacePlaceholders(message, placeholders);
    }
    
    public String getWithPrefix(String path, Map<String, String> placeholders) {
        return prefix + get(path, placeholders);
    }
    
    public void send(CommandSender sender, String path) {
        ((Audience) sender).sendMessage(SERIALIZER.deserialize(getWithPrefix(path)));
    }
    
    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        ((Audience) sender).sendMessage(SERIALIZER.deserialize(getWithPrefix(path, placeholders)));
    }
    
    private String replacePlaceholders(String message, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return message;
        }
        
        String result = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
    
    public static String translateColors(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("&#");
            replacement.append(hex);
            matcher.appendReplacement(result, replacement.toString());
        }
        matcher.appendTail(result);

        return result.toString();
    }
    
    public static Component deserialize(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return SERIALIZER.deserialize(translateColors(message));
    }
    
    public static Map<String, String> placeholders(Object... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            map.put(String.valueOf(keyValues[i]), String.valueOf(keyValues[i + 1]));
        }
        return map;
    }
    
    public String getPrefix() {
        return prefix;
    }
    
    public String getSymbol() {
        return wooEco.getCurrencyConfig().getSymbol();
    }
}
