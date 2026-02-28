package com.oolonghoo.wooeco.util;

import com.oolonghoo.wooeco.WooEco;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.*;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 调试管理器
 * 提供详细的调试日志和诊断功能
 * 
 * @author oolongho
 */
@SuppressWarnings("deprecation")
public class DebugManager {
    
    private final WooEco plugin;
    private boolean enabled;
    private boolean logToFile;
    private boolean logToConsole;
    private boolean logToOnlineAdmins;
    private File debugLogFile;
    private PrintWriter logWriter;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private final Set<String> enabledCategories = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Long> timers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> counters = new ConcurrentHashMap<>();
    
    public static final String CATEGORY_DATABASE = "DATABASE";
    public static final String CATEGORY_ECONOMY = "ECONOMY";
    public static final String CATEGORY_TRANSACTION = "TRANSACTION";
    public static final String CATEGORY_CACHE = "CACHE";
    public static final String CATEGORY_SYNC = "SYNC";
    public static final String CATEGORY_COMMAND = "COMMAND";
    public static final String CATEGORY_EVENT = "EVENT";
    public static final String CATEGORY_CONFIG = "CONFIG";
    public static final String CATEGORY_API = "API";
    public static final String CATEGORY_ALL = "ALL";
    
    public DebugManager(WooEco plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("debug.enabled", false);
        this.logToFile = plugin.getConfig().getBoolean("debug.log-to-file", true);
        this.logToConsole = plugin.getConfig().getBoolean("debug.log-to-console", true);
        this.logToOnlineAdmins = plugin.getConfig().getBoolean("debug.log-to-online-admins", false);
        
        List<String> categories = plugin.getConfig().getStringList("debug.categories");
        if (categories.isEmpty() || categories.contains("ALL")) {
            enabledCategories.add(CATEGORY_ALL);
        } else {
            enabledCategories.addAll(categories);
        }
        
        if (enabled && logToFile) {
            initLogFile();
        }
    }
    
    private void initLogFile() {
        try {
            File logFolder = new File(plugin.getDataFolder(), "debug");
            if (!logFolder.exists()) {
                logFolder.mkdirs();
            }
            
            String fileName = "debug_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log";
            debugLogFile = new File(logFolder, fileName);
            
            logWriter = new PrintWriter(new FileWriter(debugLogFile, true), true);
            log("DebugManager", "DEBUG", "调试日志系统初始化完成");
        } catch (IOException e) {
            plugin.getLogger().warning("无法创建调试日志文件: " + e.getMessage());
            logToFile = false;
        }
    }
    
    public void log(String category, String level, String message) {
        if (!enabled) return;
        if (!shouldLog(category)) return;
        
        String timestamp = dateFormat.format(new Date());
        String threadName = Thread.currentThread().getName();
        String formattedMessage = String.format("[%s] [%s] [%s] [%s] %s", 
            timestamp, threadName, category, level, message);
        
        if (logToConsole) {
            String consoleMessage = ChatColor.GRAY + "[WooEco-Debug] " + 
                ChatColor.YELLOW + "[" + category + "] " + 
                ChatColor.WHITE + message;
            Bukkit.getConsoleSender().sendMessage(consoleMessage);
        }
        
        if (logToFile && logWriter != null) {
            logWriter.println(formattedMessage);
        }
        
        if (logToOnlineAdmins) {
            String adminMessage = ChatColor.DARK_GRAY + "[WooEco-Debug] " + 
                ChatColor.YELLOW + "[" + category + "] " + 
                ChatColor.GRAY + message;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("wooeco.admin.debug")) {
                    player.sendMessage(adminMessage);
                }
            }
        }
    }
    
    private boolean shouldLog(String category) {
        return enabledCategories.contains(CATEGORY_ALL) || enabledCategories.contains(category);
    }
    
    public void startTimer(String name) {
        timers.put(name, System.nanoTime());
    }
    
    public long endTimer(String name) {
        Long start = timers.remove(name);
        if (start == null) return -1;
        return System.nanoTime() - start;
    }
    
    public long endTimerAndLog(String name, String category, String operation) {
        long elapsed = endTimer(name);
        if (elapsed > 0) {
            double ms = elapsed / 1_000_000.0;
            log(category, "PERF", String.format("%s | Time: %.3fms", operation, ms));
        }
        return elapsed;
    }
    
    public void incrementCounter(String name) {
        counters.merge(name, 1L, (oldVal, newVal) -> oldVal + newVal);
    }
    
    public long getCounter(String name) {
        return counters.getOrDefault(name, 0L);
    }
    
    public void resetCounter(String name) {
        counters.remove(name);
    }
    
    public Map<String, Long> getAllCounters() {
        return new HashMap<>(counters);
    }
    
    public void database(String message) {
        log(CATEGORY_DATABASE, "INFO", message);
    }
    
    public void database(String operation, String sql, long timeMs) {
        log(CATEGORY_DATABASE, "SQL", String.format("%s | SQL: %s | Time: %.3fms", operation, sql, timeMs / 1_000_000.0));
    }
    
    public void databaseError(String operation, String error) {
        log(CATEGORY_DATABASE, "ERROR", String.format("%s | Error: %s", operation, error));
    }
    
    public void economy(String operation, UUID uuid, String playerName, BigDecimal amount, BigDecimal before, BigDecimal after) {
        log(CATEGORY_ECONOMY, "INFO", String.format("%s | Player: %s (%s) | Amount: %s | Before: %s | After: %s",
            operation, playerName, uuid, amount, before, after));
    }
    
    public void economyError(String operation, UUID uuid, String error) {
        log(CATEGORY_ECONOMY, "ERROR", String.format("%s | UUID: %s | Error: %s", operation, uuid, error));
    }
    
    public void transaction(UUID sender, String senderName, UUID receiver, String receiverName, BigDecimal amount, BigDecimal tax) {
        log(CATEGORY_TRANSACTION, "INFO", String.format("Transfer | %s -> %s | Amount: %s | Tax: %s",
            senderName, receiverName, amount, tax));
    }
    
    public void transactionError(String error) {
        log(CATEGORY_TRANSACTION, "ERROR", "Transfer failed: " + error);
    }
    
    public void cache(String operation, String key, Object value) {
        log(CATEGORY_CACHE, "INFO", String.format("%s | Key: %s | Value: %s", operation, key, value));
    }
    
    public void cacheHit(UUID uuid) {
        incrementCounter("cache_hit");
        log(CATEGORY_CACHE, "HIT", "Cache hit for UUID: " + uuid);
    }
    
    public void cacheMiss(UUID uuid) {
        incrementCounter("cache_miss");
        log(CATEGORY_CACHE, "MISS", "Cache miss for UUID: " + uuid);
    }
    
    public void sync(String direction, String message) {
        log(CATEGORY_SYNC, direction.toUpperCase(), message);
    }
    
    public void syncPublish(String type, UUID uuid, String data) {
        log(CATEGORY_SYNC, "PUBLISH", String.format("Type: %s | UUID: %s | Data: %s", type, uuid, data));
    }
    
    public void syncReceive(String type, UUID uuid, String serverId) {
        log(CATEGORY_SYNC, "RECEIVE", String.format("Type: %s | UUID: %s | From: %s", type, uuid, serverId));
    }
    
    public void command(CommandSender sender, String command, String[] args) {
        String argsStr = args.length > 0 ? String.join(" ", args) : "(no args)";
        log(CATEGORY_COMMAND, "EXECUTE", String.format("Sender: %s | Command: %s | Args: %s", 
            sender.getName(), command, argsStr));
    }
    
    public void commandResult(CommandSender sender, String command, boolean success, String message) {
        log(CATEGORY_COMMAND, "RESULT", String.format("Sender: %s | Command: %s | Success: %s | Message: %s",
            sender.getName(), command, success, message));
    }
    
    public void commandResult(CommandSender sender, String command, boolean success, String message, long timeMs) {
        log(CATEGORY_COMMAND, "RESULT", String.format("Sender: %s | Command: %s | Success: %s | Time: %.3fms | Message: %s",
            sender.getName(), command, success, timeMs / 1_000_000.0, message));
    }
    
    public void event(String eventName, String details) {
        log(CATEGORY_EVENT, "FIRE", String.format("%s | %s", eventName, details));
    }
    
    public void config(String operation, String key, Object value) {
        log(CATEGORY_CONFIG, "INFO", String.format("%s | Key: %s | Value: %s", operation, key, value));
    }
    
    public void api(String method, String params, Object result) {
        log(CATEGORY_API, "CALL", String.format("Method: %s | Params: %s | Result: %s", method, params, result));
    }
    
    public void apiError(String method, String error) {
        log(CATEGORY_API, "ERROR", String.format("Method: %s | Error: %s", method, error));
    }
    
    public void batchOperation(String operation, int successCount, int failedCount, long timeMs) {
        log(CATEGORY_COMMAND, "BATCH", String.format("%s | Success: %d | Failed: %d | Time: %.3fms",
            operation, successCount, failedCount, timeMs / 1_000_000.0));
    }
    
    public void playerLookup(String lookupType, String input, UUID found, long timeMs) {
        log(CATEGORY_DATABASE, "LOOKUP", String.format("Type: %s | Input: %s | Found: %s | Time: %.3fms",
            lookupType, input, found, timeMs / 1_000_000.0));
    }
    
    public void dumpState(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========== WooEco 状态诊断 ==========");
        
        sender.sendMessage(ChatColor.YELLOW + "基本信息:");
        sender.sendMessage(ChatColor.GRAY + "  - 版本: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.GRAY + "  - 数据库类型: " + ChatColor.WHITE + plugin.getDatabaseConfig().getType());
        sender.sendMessage(ChatColor.GRAY + "  - UUID模式: " + ChatColor.WHITE + plugin.getDatabaseConfig().getUuidMode());
        sender.sendMessage(ChatColor.GRAY + "  - 调试模式: " + ChatColor.WHITE + (enabled ? "启用" : "禁用"));
        
        sender.sendMessage(ChatColor.YELLOW + "缓存状态:");
        sender.sendMessage(ChatColor.GRAY + "  - 在线玩家缓存: " + ChatColor.WHITE + plugin.getPlayerDataManager().getOnlineAccounts().size());
        sender.sendMessage(ChatColor.GRAY + "  - 总账户数: " + ChatColor.WHITE + plugin.getPlayerDataManager().getAccountCount());
        sender.sendMessage(ChatColor.GRAY + "  - 缓存命中: " + ChatColor.GREEN + getCounter("cache_hit"));
        sender.sendMessage(ChatColor.GRAY + "  - 缓存未命中: " + ChatColor.RED + getCounter("cache_miss"));
        if (getCounter("cache_hit") + getCounter("cache_miss") > 0) {
            double hitRate = (double) getCounter("cache_hit") / (getCounter("cache_hit") + getCounter("cache_miss")) * 100;
            sender.sendMessage(ChatColor.GRAY + "  - 命中率: " + ChatColor.WHITE + String.format("%.2f%%", hitRate));
        }
        
        sender.sendMessage(ChatColor.YELLOW + "同步状态:");
        boolean syncEnabled = plugin.getDatabaseConfig().isSyncEnabled();
        sender.sendMessage(ChatColor.GRAY + "  - 跨服同步: " + ChatColor.WHITE + (syncEnabled ? "启用" : "禁用"));
        if (syncEnabled && plugin.getRedisSyncManager() != null) {
            sender.sendMessage(ChatColor.GRAY + "  - Redis连接: " + ChatColor.WHITE + 
                (plugin.getRedisSyncManager().isRunning() ? "正常" : "断开"));
            sender.sendMessage(ChatColor.GRAY + "  - 服务器ID: " + ChatColor.WHITE + plugin.getDatabaseConfig().getServerId());
        }
        
        sender.sendMessage(ChatColor.YELLOW + "货币配置:");
        sender.sendMessage(ChatColor.GRAY + "  - 货币名称: " + ChatColor.WHITE + plugin.getCurrencyConfig().getSingularName());
        sender.sendMessage(ChatColor.GRAY + "  - 整数余额: " + ChatColor.WHITE + plugin.getCurrencyConfig().isIntegerBalance());
        sender.sendMessage(ChatColor.GRAY + "  - 小数位数: " + ChatColor.WHITE + plugin.getCurrencyConfig().getDecimalPlaces());
        sender.sendMessage(ChatColor.GRAY + "  - 最大余额: " + ChatColor.WHITE + plugin.getCurrencyConfig().getMaxBalance());
        
        sender.sendMessage(ChatColor.YELLOW + "非玩家账户:");
        boolean npEnabled = plugin.getNonPlayerAccountManager().isEnabled();
        sender.sendMessage(ChatColor.GRAY + "  - 启用状态: " + ChatColor.WHITE + (npEnabled ? "启用" : "禁用"));
        if (npEnabled) {
            sender.sendMessage(ChatColor.GRAY + "  - 白名单: " + ChatColor.WHITE + 
                (plugin.getNonPlayerAccountManager().isWhitelistEnabled() ? "启用" : "禁用"));
        }
        
        sender.sendMessage(ChatColor.GOLD + "====================================");
    }
    
    public void dumpPlayerState(CommandSender sender, UUID uuid) {
        var account = plugin.getPlayerDataManager().getAccount(uuid);
        if (account == null) {
            sender.sendMessage(ChatColor.RED + "玩家账户不存在: " + uuid);
            return;
        }
        
        sender.sendMessage(ChatColor.GOLD + "========== 玩家账户诊断 ==========");
        sender.sendMessage(ChatColor.YELLOW + "基本信息:");
        sender.sendMessage(ChatColor.GRAY + "  - UUID: " + ChatColor.WHITE + account.getUuid());
        sender.sendMessage(ChatColor.GRAY + "  - 名称: " + ChatColor.WHITE + account.getPlayerName());
        sender.sendMessage(ChatColor.GRAY + "  - 创建时间: " + ChatColor.WHITE + new Date(account.getCreatedAt()));
        sender.sendMessage(ChatColor.GRAY + "  - 更新时间: " + ChatColor.WHITE + new Date(account.getUpdatedAt()));
        sender.sendMessage(ChatColor.GRAY + "  - 数据变更: " + ChatColor.WHITE + (account.isDirty() ? "是" : "否"));
        
        sender.sendMessage(ChatColor.YELLOW + "余额信息:");
        sender.sendMessage(ChatColor.GRAY + "  - 当前余额: " + ChatColor.WHITE + account.getBalance());
        sender.sendMessage(ChatColor.GRAY + "  - 今日收入: " + ChatColor.WHITE + account.getDailyIncome());
        sender.sendMessage(ChatColor.GRAY + "  - 上次重置: " + ChatColor.WHITE + new Date(account.getLastIncomeReset()));
        
        sender.sendMessage(ChatColor.GOLD + "====================================");
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled && logToFile && logWriter == null) {
            initLogFile();
        }
        log("DebugManager", "CONFIG", "调试模式已" + (enabled ? "启用" : "禁用"));
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void shutdown() {
        if (logWriter != null) {
            logWriter.flush();
            logWriter.close();
            logWriter = null;
        }
    }
    
    public void reload() {
        shutdown();
        loadConfig();
    }
}
