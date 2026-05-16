package com.oolonghoo.wooeco.util;

import com.oolonghoo.wooeco.WooEco;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.*;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
        initConfig();
    }
    
    private void initConfig() {
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
    
    public void loadConfig() {
        shutdown();
        initConfig();
    }
    
    private void initLogFile() {
        if (logWriter != null) {
            logWriter.flush();
            logWriter.close();
            logWriter = null;
        }
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
            plugin.getLogger().warning(String.format("无法创建调试日志文件：%s", e.getMessage()));
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
            ((Audience) Bukkit.getConsoleSender()).sendMessage(
                Component.text("[WooEco-Debug] ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("[", NamedTextColor.GRAY))
                    .append(Component.text(category, NamedTextColor.YELLOW))
                    .append(Component.text("] ", NamedTextColor.GRAY))
                    .append(Component.text(message, NamedTextColor.WHITE)));
        }
        
        if (logToFile && logWriter != null) {
            logWriter.println(formattedMessage);
        }
        
        if (logToOnlineAdmins) {
            Component adminMsg = Component.text("[WooEco-Debug] ", NamedTextColor.DARK_GRAY)
                .append(Component.text("[", NamedTextColor.GRAY))
                .append(Component.text(category, NamedTextColor.YELLOW))
                .append(Component.text("] ", NamedTextColor.GRAY))
                .append(Component.text(message, NamedTextColor.DARK_GRAY));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("wooeco.admin.debug")) {
                    ((Audience) player).sendMessage(adminMsg);
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
        Audience audience = (Audience) sender;
        
        audience.sendMessage(Component.text("========== WooEco 状态诊断 ==========", NamedTextColor.GOLD));
        
        audience.sendMessage(Component.text("基本信息:", NamedTextColor.YELLOW));
        audience.sendMessage(Component.text("  - 版本: ", NamedTextColor.GRAY).append(Component.text(plugin.getDescription().getVersion(), NamedTextColor.WHITE)));
        audience.sendMessage(Component.text("  - 数据库类型: ", NamedTextColor.GRAY).append(Component.text(plugin.getDatabaseConfig().getType().name(), NamedTextColor.WHITE)));
        audience.sendMessage(Component.text("  - UUID模式: ", NamedTextColor.GRAY).append(Component.text(plugin.getDatabaseConfig().getUuidMode().name(), NamedTextColor.WHITE)));
        audience.sendMessage(Component.text("  - 调试模式: ", NamedTextColor.GRAY).append(Component.text(enabled ? "启用" : "禁用", NamedTextColor.WHITE)));
        
        audience.sendMessage(Component.text("缓存状态:", NamedTextColor.YELLOW));
        audience.sendMessage(Component.text("  - 在线玩家缓存: ", NamedTextColor.GRAY).append(Component.text(String.valueOf(plugin.getPlayerDataManager().getOnlineAccounts().size()), NamedTextColor.WHITE)));
        audience.sendMessage(Component.text("  - 总账户数: ", NamedTextColor.GRAY).append(Component.text(String.valueOf(plugin.getPlayerDataManager().getAccountCount()), NamedTextColor.WHITE)));
        audience.sendMessage(Component.text("  - 缓存命中: ", NamedTextColor.GRAY).append(Component.text(String.valueOf(getCounter("cache_hit")), NamedTextColor.GREEN)));
        audience.sendMessage(Component.text("  - 缓存未命中: ", NamedTextColor.GRAY).append(Component.text(String.valueOf(getCounter("cache_miss")), NamedTextColor.RED)));
        if (getCounter("cache_hit") + getCounter("cache_miss") > 0) {
            double hitRate = (double) getCounter("cache_hit") / (getCounter("cache_hit") + getCounter("cache_miss")) * 100;
            audience.sendMessage(Component.text("  - 命中率: ", NamedTextColor.GRAY).append(Component.text(String.format("%.2f%%", hitRate), NamedTextColor.WHITE)));
        }
        
        audience.sendMessage(Component.text("同步状态:", NamedTextColor.YELLOW));
        boolean syncEnabled = plugin.getDatabaseConfig().isSyncEnabled();
        audience.sendMessage(Component.text("  - 跨服同步: ", NamedTextColor.GRAY).append(Component.text(syncEnabled ? "启用" : "禁用", NamedTextColor.WHITE)));
        if (syncEnabled && plugin.getRedisSyncManager() != null) {
            audience.sendMessage(Component.text("  - Redis连接: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.getRedisSyncManager().isRunning() ? "正常" : "断开", NamedTextColor.WHITE)));
            audience.sendMessage(Component.text("  - 服务器ID: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.getDatabaseConfig().getServerId(), NamedTextColor.WHITE)));
        }
        
        audience.sendMessage(Component.text("货币配置:", NamedTextColor.YELLOW));
        audience.sendMessage(Component.text("  - 货币名称: ", NamedTextColor.GRAY).append(Component.text(plugin.getCurrencyConfig().getSingularName(), NamedTextColor.WHITE)));
        audience.sendMessage(Component.text("  - 整数余额: ", NamedTextColor.GRAY).append(Component.text(String.valueOf(plugin.getCurrencyConfig().isIntegerBalance()), NamedTextColor.WHITE)));
        audience.sendMessage(Component.text("  - 小数位数: ", NamedTextColor.GRAY).append(Component.text(String.valueOf(plugin.getCurrencyConfig().getDecimalPlaces()), NamedTextColor.WHITE)));
        audience.sendMessage(Component.text("  - 最大余额: ", NamedTextColor.GRAY).append(Component.text(String.valueOf(plugin.getCurrencyConfig().getMaxBalance()), NamedTextColor.WHITE)));
        
        audience.sendMessage(Component.text("非玩家账户:", NamedTextColor.YELLOW));
        boolean npEnabled = plugin.getNonPlayerAccountManager().isEnabled();
        audience.sendMessage(Component.text("  - 启用状态: ", NamedTextColor.GRAY).append(Component.text(npEnabled ? "启用" : "禁用", NamedTextColor.WHITE)));
        if (npEnabled) {
            audience.sendMessage(Component.text("  - 白名单: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.getNonPlayerAccountManager().isWhitelistEnabled() ? "启用" : "禁用", NamedTextColor.WHITE)));
        }
        
        audience.sendMessage(Component.text("====================================", NamedTextColor.GOLD));
    }
    
    public void dumpPlayerState(CommandSender sender, UUID uuid) {
        Audience audience = (Audience) sender;
        var account = plugin.getPlayerDataManager().getAccount(uuid);
        if (account == null) {
            audience.sendMessage(Component.text("玩家账户不存在: " + uuid, NamedTextColor.RED));
            return;
        }
        
        audience.sendMessage(Component.text("========== 玩家账户诊断 ==========", NamedTextColor.GOLD));
        audience.sendMessage(Component.text("基本信息:", NamedTextColor.YELLOW));
        audience.sendMessage(Component.text("  - UUID: ", NamedTextColor.GRAY).append(Component.text(account.getUuid().toString(), NamedTextColor.WHITE)));
        audience.sendMessage(Component.text("  - 名称: ", NamedTextColor.GRAY).append(Component.text(account.getPlayerName(), NamedTextColor.WHITE)));
        audience.sendMessage(Component.text("  - 创建时间: ", NamedTextColor.GRAY).append(Component.text(new Date(account.getCreatedAt()).toString(), NamedTextColor.WHITE)));
        audience.sendMessage(Component.text("  - 更新时间: ", NamedTextColor.GRAY).append(Component.text(new Date(account.getUpdatedAt()).toString(), NamedTextColor.WHITE)));
        audience.sendMessage(Component.text("  - 数据变更: ", NamedTextColor.GRAY).append(Component.text(account.isDirty() ? "是" : "否", NamedTextColor.WHITE)));
        
        audience.sendMessage(Component.text("余额信息:", NamedTextColor.YELLOW));
        audience.sendMessage(Component.text("  - 当前余额: ", NamedTextColor.GRAY).append(Component.text(account.getBalance().toString(), NamedTextColor.WHITE)));
        audience.sendMessage(Component.text("  - 今日收入: ", NamedTextColor.GRAY).append(Component.text(account.getDailyIncome().toString(), NamedTextColor.WHITE)));
        audience.sendMessage(Component.text("  - 上次重置: ", NamedTextColor.GRAY).append(Component.text(new Date(account.getLastIncomeReset()).toString(), NamedTextColor.WHITE)));
        
        audience.sendMessage(Component.text("====================================", NamedTextColor.GOLD));
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
