package com.oolonghoo.wooeco;

import com.oolonghoo.wooeco.api.WooEcoAPI;
import com.oolonghoo.wooeco.command.*;
import com.oolonghoo.wooeco.config.ConfigLoader;
import com.oolonghoo.wooeco.config.CurrencyConfig;
import com.oolonghoo.wooeco.config.DatabaseConfig;
import com.oolonghoo.wooeco.config.MessageManager;
import com.oolonghoo.wooeco.database.DatabaseManager;
import com.oolonghoo.wooeco.hook.PlaceholderAPIHook;
import com.oolonghoo.wooeco.listener.PlayerJoinListener;
import com.oolonghoo.wooeco.manager.*;
import com.oolonghoo.wooeco.manager.NonPlayerAccountManager;
import com.oolonghoo.wooeco.sync.RedisSyncManager;
import com.oolonghoo.wooeco.util.UUIDHandler;
import com.oolonghoo.wooeco.vault.VaultHook;
import com.oolonghoo.wooeco.util.DebugManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * WooEco - 经济插件主类
 * 
 * @author oolongho
 * @since 1.0.0
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
    
    @Override
    public void onEnable() {
        instance = this;
        
        configLoader = new ConfigLoader(this, "config.yml");
        configLoader.initialize();
        
        databaseConfig = new DatabaseConfig(this);
        databaseConfig.load();
        
        currencyConfig = new CurrencyConfig(this);
        currencyConfig.load();
        
        com.oolonghoo.wooeco.util.MoneyFormat.initialize(this);
        com.oolonghoo.wooeco.util.AsyncUtils.initialize(this);
        com.oolonghoo.wooeco.util.ThreadUtils.initialize(this);
        
        messageManager = new MessageManager(this);
        messageManager.initialize();
        
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
        
        debugManager = new DebugManager(this);
        
        playerDataManager = new PlayerDataManager(this);
        logManager = new LogManager(this);
        economyManager = new EconomyManager(this);
        transactionManager = new TransactionManager(this);
        taxManager = new TaxManager(this);
        leaderboardManager = new LeaderboardManager(this);
        offlineTransferManager = new OfflineTransferManager(this);
        nonPlayerAccountManager = new NonPlayerAccountManager(this);
        globalStatsManager = new GlobalStatsManager(this);
        uuidHandler = new UUIDHandler(this);
        
        if (databaseConfig.isSyncEnabled()) {
            redisSyncManager = new RedisSyncManager(this);
            redisSyncManager.initialize();
        }
        
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            vaultHook = new VaultHook(this);
            vaultHook.hook();
            getLogger().info("Vault集成已启用");
        }
        
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIHook(this).register();
            getLogger().info("PlaceholderAPI集成已启用");
        }
        
        WooEcoAPI.initialize(this);

        registerCommands();
        registerListeners();
        startTasks();

        getLogger().info("WooEco v" + getPluginMeta().getVersion() + " 已启用!");
        getLogger().info("数据库类型: " + databaseConfig.getType());
    }
    
    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        
        if (debugManager != null) {
            debugManager.shutdown();
        }
        
        com.oolonghoo.wooeco.util.ThreadUtils.shutdown();
        
        if (redisSyncManager != null) {
            redisSyncManager.shutdown();
        }
        
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        if (vaultHook != null) {
            vaultHook.unhook();
        }
        
        getLogger().info("WooEco 已禁用!");
    }
    
    private void registerCommands() {
        MainCommand mainCommand = new MainCommand(this);
        getCommand("wooeco").setExecutor(mainCommand);
        getCommand("wooeco").setTabCompleter(mainCommand);
        
        commandAliasManager = new CommandAliasManager(this, mainCommand);
        commandAliasManager.registerAliases();
        
        PayCommand payCommand = new PayCommand(this);
        getCommand("pay").setExecutor(payCommand);
        
        IncomeCommand incomeCommand = new IncomeCommand(this);
        getCommand("income").setExecutor(incomeCommand);
    }
    
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
    }
    
    private void startTasks() {
        long autoSaveInterval = getConfig().getLong("database.auto-save", 60) * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (playerDataManager != null) {
                playerDataManager.saveAll();
            }
        }, autoSaveInterval, autoSaveInterval);
        
        long leaderboardRefresh = getConfig().getLong("leaderboard.cache-refresh", 60) * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (leaderboardManager != null) {
                leaderboardManager.refreshCache();
            }
            if (globalStatsManager != null) {
                globalStatsManager.refresh();
            }
        }, leaderboardRefresh, leaderboardRefresh);
        
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (playerDataManager != null) {
                playerDataManager.checkDailyReset();
            }
        }, 20L * 60, 20L * 60);
        
        long cleanupInterval = 20L * 60 * 60 * 24;
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            int retentionDays = getConfig().getInt("logging.retention-days", 30);
            if (retentionDays > 0) {
                try {
                    databaseManager.getLogDAO().cleanupOldLogs(retentionDays);
                    databaseManager.getTransactionDAO().cleanupOldTransactions(retentionDays);
                } catch (Exception e) {
                    getLogger().warning("清理过期日志失败: " + e.getMessage());
                }
            }
        }, cleanupInterval, cleanupInterval);
    }
    
    public void reload() {
        configLoader.reload();
        databaseConfig.load();
        currencyConfig.load();
        com.oolonghoo.wooeco.util.MoneyFormat.loadConfig();
        com.oolonghoo.wooeco.util.AsyncUtils.reload();
        com.oolonghoo.wooeco.util.ThreadUtils.reload();
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
}
