package com.oolonghoo.wooeco;

import org.bukkit.plugin.java.JavaPlugin;

import com.oolonghoo.wooeco.api.WooEcoAPI;
import com.oolonghoo.wooeco.command.CommandAliasManager;
import com.oolonghoo.wooeco.command.IncomeCommand;
import com.oolonghoo.wooeco.command.MainCommand;
import com.oolonghoo.wooeco.command.PayCommand;
import com.oolonghoo.wooeco.config.ConfigLoader;
import com.oolonghoo.wooeco.config.ConfigValidator;
import com.oolonghoo.wooeco.config.CurrencyConfig;
import com.oolonghoo.wooeco.config.DatabaseConfig;
import com.oolonghoo.wooeco.config.MessageManager;
import com.oolonghoo.wooeco.database.DatabaseManager;
import com.oolonghoo.wooeco.hook.PlaceholderAPIHook;
import com.oolonghoo.wooeco.listener.PlayerJoinListener;
import com.oolonghoo.wooeco.manager.CooldownManager;
import com.oolonghoo.wooeco.manager.EconomyManager;
import com.oolonghoo.wooeco.manager.GlobalStatsManager;
import com.oolonghoo.wooeco.manager.LeaderboardManager;
import com.oolonghoo.wooeco.manager.LogManager;
import com.oolonghoo.wooeco.manager.NonPlayerAccountManager;
import com.oolonghoo.wooeco.manager.OfflineTransferManager;
import com.oolonghoo.wooeco.manager.PayToggleManager;
import com.oolonghoo.wooeco.manager.PlayerDataManager;
import com.oolonghoo.wooeco.manager.TaxManager;
import com.oolonghoo.wooeco.manager.TransactionManager;
import com.oolonghoo.wooeco.migration.MigrationManager;
import com.oolonghoo.wooeco.sync.RedisSyncManager;
import com.oolonghoo.wooeco.util.AsyncUtils;
import com.oolonghoo.wooeco.util.DebugManager;
import com.oolonghoo.wooeco.util.SchedulerUtils;
import com.oolonghoo.wooeco.util.UUIDHandler;

import java.sql.SQLException;
import com.oolonghoo.wooeco.vault.VaultHook;

/**
 * WooEco - 经济插件主类
 * 
 */
public class WooEco extends JavaPlugin {
    
    private static WooEco instance;
    
    private ConfigLoader configLoader;
    private DatabaseConfig databaseConfig;
    private MessageManager messageManager;
    private CurrencyConfig currencyConfig;
    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;
    private EconomyManager economyManager;
    private TransactionManager transactionManager;
    private TaxManager taxManager;
    private LogManager logManager;
    private LeaderboardManager leaderboardManager;
    private OfflineTransferManager offlineTransferManager;
    private NonPlayerAccountManager nonPlayerAccountManager;
    private GlobalStatsManager globalStatsManager;
    private UUIDHandler uuidHandler;
    private RedisSyncManager redisSyncManager;
    private VaultHook vaultHook;
    private DebugManager debugManager;
    private CommandAliasManager commandAliasManager;
    private CooldownManager cooldownManager;
    private PayToggleManager payToggleManager;
    private MigrationManager migrationManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        configLoader = new ConfigLoader(this, "config.yml");
        configLoader.initialize();
        getLogger().info("[WooEco] 配置加载完成");
        
        ConfigValidator configValidator = new ConfigValidator(this);
        if (!configValidator.validate(getConfig())) {
            getLogger().warning("[WooEco] 配置文件存在错误，部分功能可能无法正常工作");
        }
        
        databaseConfig = new DatabaseConfig(this);
        databaseConfig.load();
        
        currencyConfig = new CurrencyConfig(this);
        currencyConfig.load();
        
        com.oolonghoo.wooeco.util.AsyncUtils.initialize(this);
        
        messageManager = new MessageManager(this);
        messageManager.initialize();
        
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
        getLogger().info(String.format("[WooEco] 数据库连接成功 (%s)", databaseConfig.getType()));
        
        debugManager = new DebugManager(this);
        cooldownManager = new CooldownManager(this);
        
        playerDataManager = new PlayerDataManager(this);
        logManager = new LogManager(this);
        economyManager = new EconomyManager(this);
        taxManager = new TaxManager(this);
        transactionManager = new TransactionManager(this);
        leaderboardManager = new LeaderboardManager(this);
        offlineTransferManager = new OfflineTransferManager(this);
        nonPlayerAccountManager = new NonPlayerAccountManager(this);
        globalStatsManager = new GlobalStatsManager(this);
        uuidHandler = new UUIDHandler(this);
        payToggleManager = new PayToggleManager(this);
        migrationManager = new MigrationManager(this);
        
        if (databaseConfig.isSyncEnabled()) {
            redisSyncManager = new RedisSyncManager(this);
            redisSyncManager.initialize();
            getLogger().info("[WooEco] Redis 同步已启用");
        }
        
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            boolean registerProvider = getConfig().getBoolean("vault.register-as-provider", true);
            if (registerProvider) {
                vaultHook = new VaultHook(this);
                vaultHook.hook();
                getLogger().info("[WooEco] Vault 集成已启用");
            } else {
                getLogger().info("[WooEco] Vault 注册已禁用（迁移模式）");
            }
        }
        
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIHook(this).register();
            getLogger().info("[WooEco] PlaceholderAPI 集成已启用");
        }
        
        WooEcoAPI.initialize(this);

        registerCommands();
        registerListeners();
        startTasks();

        getLogger().info(String.format("[WooEco] WooEco v%s 已启用!", getPluginMeta().getVersion()));
    }
    
    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        
        if (nonPlayerAccountManager != null) {
            nonPlayerAccountManager.saveAll();
        }
        
        if (debugManager != null) {
            debugManager.shutdown();
        }
        
        AsyncUtils.shutdown();
        
        if (redisSyncManager != null) {
            redisSyncManager.shutdown();
        }
        
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        if (vaultHook != null) {
            vaultHook.unhook();
        }
        
        getLogger().info("[WooEco] 插件已禁用");
    }
    
    private void registerCommands() {
        MainCommand mainCommand = new MainCommand(this);
        getCommand("wooeco").setExecutor(mainCommand);
        getCommand("wooeco").setTabCompleter(mainCommand);
        
        commandAliasManager = new CommandAliasManager(this, mainCommand);
        commandAliasManager.registerAliases();
        
        PayCommand payCommand = new PayCommand(this);
        getCommand("pay").setExecutor(payCommand);
        getCommand("pay").setTabCompleter(payCommand);
        
        // 延迟1tick重新绑定，防止被其他插件（如 GlobalMarketPlus）覆盖
        SchedulerUtils.runGlobalDelayed(this, () -> {
            org.bukkit.command.PluginCommand payCmd = getServer().getPluginCommand("pay");
            if (payCmd != null) {
                payCmd.setExecutor(payCommand);
                payCmd.setTabCompleter(payCommand);
                getLogger().info("[WooEco] /pay 命令已强制绑定");
            }
        }, 1L);
        
        IncomeCommand incomeCommand = new IncomeCommand(this);
        getCommand("income").setExecutor(incomeCommand);
    }
    
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
    }
    
    private void startTasks() {
        long autoSaveInterval = getConfig().getLong("database.auto-save", 60) * 20L;
        SchedulerUtils.runAsyncTimer(this, () -> {
            if (playerDataManager != null) {
                playerDataManager.saveAll();
            }
        }, SchedulerUtils.ticksToMs(autoSaveInterval), SchedulerUtils.ticksToMs(autoSaveInterval));

        long leaderboardRefresh = getConfig().getLong("leaderboard.cache-refresh", 60) * 20L;
        SchedulerUtils.runAsyncTimer(this, () -> {
            if (leaderboardManager != null) {
                leaderboardManager.refreshCache();
            }
            if (globalStatsManager != null) {
                globalStatsManager.refreshAsync();
            }
        }, SchedulerUtils.ticksToMs(leaderboardRefresh), SchedulerUtils.ticksToMs(leaderboardRefresh));

        long dailyCheckInterval = 20L * 60; // 1 minute in ticks
        SchedulerUtils.runAsyncTimer(this, () -> {
            if (playerDataManager != null) {
                playerDataManager.checkDailyReset();
            }
        }, SchedulerUtils.ticksToMs(dailyCheckInterval), SchedulerUtils.ticksToMs(dailyCheckInterval));

        scheduleMidnightReset();

        long cleanupInterval = 20L * 60 * 60 * 24; // 1 day in ticks
        SchedulerUtils.runAsyncTimer(this, () -> {
            int retentionDays = getConfig().getInt("logging.retention-days", 30);
            if (retentionDays > 0) {
                try {
                    databaseManager.getLogDAO().cleanupOldLogs(retentionDays);
                    databaseManager.getTransactionDAO().cleanupOldTransactions(retentionDays);
                } catch (SQLException e) {
                    getLogger().warning(String.format("[WooEco] 清理过期日志失败：%s", e.getMessage()));
                }
            }
        }, SchedulerUtils.ticksToMs(cleanupInterval), SchedulerUtils.ticksToMs(cleanupInterval));
    }

    private void scheduleMidnightReset() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay();
        long ticksUntilMidnight = java.time.Duration.between(now, nextMidnight).getSeconds() * 20L;
        long oneDayTicks = 20L * 60 * 60 * 24;

        SchedulerUtils.runAsyncTimer(this, () -> {
            if (playerDataManager != null) {
                playerDataManager.resetAllDailyIncome();
                getLogger().info("[WooEco] 已重置所有在线玩家的每日收入统计");
            }
        }, SchedulerUtils.ticksToMs(ticksUntilMidnight), SchedulerUtils.ticksToMs(oneDayTicks));
    }
    
    public void reload() {
        configLoader.reload();
        databaseConfig.load();
        currencyConfig.load();
        com.oolonghoo.wooeco.util.AsyncUtils.reload();
        messageManager.reload();
        if (debugManager != null) {
            debugManager.reload();
        }
        if (redisSyncManager != null) {
            redisSyncManager.reload();
        }
        if (commandAliasManager != null) {
            commandAliasManager.reloadAliases();
        }
        if (cooldownManager != null) {
            cooldownManager.reload();
        }
        if (leaderboardManager != null) {
            leaderboardManager.reloadBlacklist();
        }
        if (nonPlayerAccountManager != null) {
            nonPlayerAccountManager.reload();
        }
    }
    
    public static WooEco getInstance() {
        return instance;
    }
    
    public ConfigLoader getConfigLoader() {
        return configLoader;
    }
    
    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }
    
    public MessageManager getMessageManager() {
        return messageManager;
    }
    
    public CurrencyConfig getCurrencyConfig() {
        return currencyConfig;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
    
    public EconomyManager getEconomyManager() {
        return economyManager;
    }
    
    public TransactionManager getTransactionManager() {
        return transactionManager;
    }
    
    public TaxManager getTaxManager() {
        return taxManager;
    }
    
    public LogManager getLogManager() {
        return logManager;
    }
    
    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }
    
    public OfflineTransferManager getOfflineTransferManager() {
        return offlineTransferManager;
    }
    
    public NonPlayerAccountManager getNonPlayerAccountManager() {
        return nonPlayerAccountManager;
    }
    
    public GlobalStatsManager getGlobalStatsManager() {
        return globalStatsManager;
    }
    
    public UUIDHandler getUuidHandler() {
        return uuidHandler;
    }
    
    public RedisSyncManager getRedisSyncManager() {
        return redisSyncManager;
    }
    
    public VaultHook getVaultHook() {
        return vaultHook;
    }
    
    public DebugManager getDebugManager() {
        return debugManager;
    }
    
    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }
    
    public PayToggleManager getPayToggleManager() {
        return payToggleManager;
    }
    
    public MigrationManager getMigrationManager() {
        return migrationManager;
    }
}
