package com.oolonghoo.wooeco.sync;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.config.DatabaseConfig;
import com.oolonghoo.wooeco.model.PlayerAccount;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;

/**
 * Redis 跨服同步管理器
 * 
 */
public class RedisSyncManager {
    
    private final WooEco plugin;
    private final DatabaseConfig config;
    
    private JedisPool jedisPool;
    private JedisPubSub subscriber;
    private Thread subscribeThread;
    private String serverId;
    private String channel;
    
    private volatile boolean running = false;
    
    public RedisSyncManager(WooEco plugin) {
        this.plugin = plugin;
        this.config = plugin.getDatabaseConfig();
    }
    
    public void initialize() {
        if (!config.isSyncEnabled()) {
            return;
        }
        
        this.serverId = config.getServerId();
        this.channel = config.getRedisChannel();
        
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(1);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestWhileIdle(true);
            
            String password = config.getRedisPassword();
            if (password != null && password.isEmpty()) {
                password = null;
            }
            
            if (password != null) {
                jedisPool = new JedisPool(poolConfig, config.getRedisHost(), config.getRedisPort(), 2000, password);
            } else {
                jedisPool = new JedisPool(poolConfig, config.getRedisHost(), config.getRedisPort(), 2000);
            }
            
            running = true;
            startSubscriber();
            
            plugin.getLogger().info("Redis 同步已启用: " + config.getRedisHost() + ":" + config.getRedisPort());
        } catch (Exception e) {
            plugin.getLogger().severe("Redis 连接失败: " + e.getMessage());
        }
    }
    
    private void startSubscriber() {
        subscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                try {
                    processMessage(message);
                } catch (Exception e) {
                    plugin.getLogger().warning("处理同步消息失败: " + e.getMessage());
                }
            }
        };
        
        subscribeThread = new Thread(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try (Jedis jedis = jedisPool.getResource()) {
                    String password = config.getRedisPassword();
                    if (password != null && !password.isEmpty()) {
                        jedis.auth(password);
                    }
                    jedis.subscribe(subscriber, channel);
                } catch (Exception e) {
                    if (running) {
                        plugin.getLogger().warning("Redis 订阅断开，正在重连...");
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }, "WooEco-Redis-Subscriber");
        subscribeThread.setDaemon(true);
        subscribeThread.start();
    }
    
    public void publishBalanceUpdate(UUID uuid, String playerName, double newBalance) {
        if (!running || jedisPool == null) return;
        
        SyncMessage message = new SyncMessage(
            SyncType.BALANCE_UPDATE,
            serverId,
            uuid,
            playerName,
            newBalance,
            0,
            0
        );
        
        publish(message);
    }
    
    public void publishDailyIncomeReset(UUID uuid) {
        if (!running || jedisPool == null) return;
        
        SyncMessage message = new SyncMessage(
            SyncType.DAILY_INCOME_RESET,
            serverId,
            uuid,
            null,
            0,
            0,
            0
        );
        
        publish(message);
    }
    
    private void publish(SyncMessage message) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String password = config.getRedisPassword();
                if (password != null && !password.isEmpty()) {
                    jedis.auth(password);
                }
                jedis.publish(channel, serialize(message));
            } catch (Exception e) {
                plugin.getLogger().warning("发布同步消息失败: " + e.getMessage());
            }
        });
    }
    
    private void processMessage(String message) {
        try {
            SyncMessage sync = deserialize(message);
            
            if (sync.getServerId().equals(serverId)) {
                return;
            }
            
            switch (sync.getType()) {
                case BALANCE_UPDATE:
                    handleBalanceUpdate(sync);
                    break;
                case DAILY_INCOME_RESET:
                    handleDailyIncomeReset(sync);
                    break;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("解析同步消息失败: " + e.getMessage());
        }
    }
    
    private void handleBalanceUpdate(SyncMessage sync) {
        UUID uuid = sync.getUuid();
        PlayerAccount account = plugin.getPlayerDataManager().getOnlineAccount(uuid);
        
        if (account != null) {
            account.setBalance(sync.getBalance());
            plugin.getLogger().fine("从 Redis 同步余额: " + sync.getPlayerName() + " -> " + sync.getBalance());
        }
    }
    
    private void handleDailyIncomeReset(SyncMessage sync) {
        UUID uuid = sync.getUuid();
        PlayerAccount account = plugin.getPlayerDataManager().getOnlineAccount(uuid);
        
        if (account != null) {
            account.setDailyIncome(0);
        }
    }
    
    /**
     * 使用安全的分隔符格式序列化，避免 Java 反序列化漏洞 (RCE)
     * 格式: type|serverId|uuid|playerName|balance|dailyIncome|timestamp
     */
    private String serialize(SyncMessage message) {
        String playerName = message.getPlayerName() != null ? message.getPlayerName().replace("|", "_") : "";
        return message.getType().name() + "|" +
                message.getServerId().replace("|", "_") + "|" +
                message.getUuid() + "|" +
                playerName + "|" +
                message.getBalance() + "|" +
                message.getDailyIncome() + "|" +
                message.getTimestamp();
    }
    
    private SyncMessage deserialize(String data) {
        String[] parts = data.split("\\|", -1);
        if (parts.length < 7) {
            throw new IllegalArgumentException("无效的同步消息格式");
        }
        SyncType type;
        try {
            type = SyncType.valueOf(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知的消息类型: " + parts[0]);
        }
        String serverId = parts[1];
        UUID uuid;
        try {
            uuid = UUID.fromString(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的 UUID: " + parts[2]);
        }
        String playerName = parts[3].isEmpty() ? null : parts[3];
        double balance = parseSafeDouble(parts[4]);
        double dailyIncome = parseSafeDouble(parts[5]);
        long timestamp = Long.parseLong(parts[6]);
        return new SyncMessage(type, serverId, uuid, playerName, balance, dailyIncome, timestamp);
    }
    
    private static double parseSafeDouble(String s) {
        double d = Double.parseDouble(s);
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("无效的数值: " + s);
        }
        return d;
    }
    
    public void reload() {
        shutdown();
        initialize();
    }
    
    public void shutdown() {
        running = false;
        
        if (subscriber != null) {
            subscriber.unsubscribe();
        }
        
        if (subscribeThread != null) {
            subscribeThread.interrupt();
        }
        
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
        
        plugin.getLogger().info("Redis 同步已关闭");
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public enum SyncType {
        BALANCE_UPDATE,
        DAILY_INCOME_RESET
    }
    
    public static class SyncMessage {
        private final SyncType type;
        private final String serverId;
        private final UUID uuid;
        private final String playerName;
        private final double balance;
        private final double dailyIncome;
        private final long timestamp;
        
        public SyncMessage(SyncType type, String serverId, UUID uuid, String playerName,
                          double balance, double dailyIncome, long timestamp) {
            this.type = type;
            this.serverId = serverId;
            this.uuid = uuid;
            this.playerName = playerName;
            this.balance = balance;
            this.dailyIncome = dailyIncome;
            this.timestamp = timestamp;
        }
        
        public SyncType getType() { return type; }
        public String getServerId() { return serverId; }
        public UUID getUuid() { return uuid; }
        public String getPlayerName() { return playerName; }
        public double getBalance() { return balance; }
        public double getDailyIncome() { return dailyIncome; }
        public long getTimestamp() { return timestamp; }
    }
}
