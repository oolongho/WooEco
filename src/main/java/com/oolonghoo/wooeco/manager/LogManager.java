package com.oolonghoo.wooeco.manager;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.database.dao.LogDAO;
import com.oolonghoo.wooeco.model.EconomyLog;
import com.oolonghoo.wooeco.util.SchedulerUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 日志管理器
 * 使用队列 + 定时批量写入，减少 DB 连接和文件 IO 开销
 */
public class LogManager {

    private static final long FLUSH_INTERVAL_MS = 5000; // 5秒刷新一次

    private final WooEco plugin;
    private final LogDAO logDAO;
    private final File logFolder;
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DateTimeFormatter fileDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 日志队列，logBalanceChange 入队，定时任务消费 */
    private final ConcurrentLinkedQueue<EconomyLog> logQueue = new ConcurrentLinkedQueue<>();

    /** 按日期+类型缓存的 BufferedWriter，避免每次写入都打开关闭文件 */
    private final Map<String, BufferedWriter> writerCache = new HashMap<>();

    /** 插件禁用时置为 true，停止递归调度 */
    private volatile boolean shutdown = false;

    public LogManager(WooEco plugin) {
        this.plugin = plugin;
        this.logDAO = plugin.getDatabaseManager().getLogDAO();
        this.logFolder = new File(plugin.getDataFolder(), "logs");
        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }
        scheduleNextFlush();
    }

    /**
     * 递归调度下一次刷新，确保上一次执行完毕后再调度下一次
     */
    private void scheduleNextFlush() {
        if (shutdown) return;
        SchedulerUtils.runAsyncDelayed(plugin, () -> {
            flush();
            scheduleNextFlush();
        }, FLUSH_INTERVAL_MS);
    }

    /**
     * 记录余额变动日志（入队操作，不直接写入）
     */
    public void logBalanceChange(UUID uuid, String playerName, String action,
                                  BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                                  String operator, String operatorName, String reason) {
        if (!shouldLog(action)) {
            return;
        }

        EconomyLog log = new EconomyLog(
            uuid, playerName, action, amount,
            balanceBefore, balanceAfter,
            operator, operatorName, reason
        );

        logQueue.add(log);
    }

    /**
     * 刷新：从队列中取出所有日志，批量写入 DB 和文件
     * 插件禁用时也会调用此方法确保日志不丢失
     */
    public void flush() {
        List<EconomyLog> batch = new ArrayList<>();
        EconomyLog log;
        while ((log = logQueue.poll()) != null) {
            batch.add(log);
        }

        if (batch.isEmpty()) {
            return;
        }

        // 批量写入 DB
        try {
            logDAO.saveAllBatch(batch);
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("批量保存日志失败：%s", e.getMessage()));
        }

        // 批量写入文件
        writeToFileBatch(batch);
    }

    /**
     * 插件禁用时调用：停止定时任务、刷新剩余日志并关闭所有 BufferedWriter
     */
    public void shutdown() {
        shutdown = true;
        // 刷新剩余日志
        flush();
        // 关闭所有 BufferedWriter
        closeAllWriters();
    }

    private boolean shouldLog(String action) {
        if ("TRANSACTION".equals(action) || "PAYMENT".equals(action) || "PAYMENT_RECEIVED".equals(action)) {
            return plugin.getConfig().getBoolean("logging.transaction", true);
        }

        if ("GIVE".equals(action) || "TAKE".equals(action) || "SET".equals(action)) {
            return plugin.getConfig().getBoolean("logging.admin", true);
        }

        return true;
    }

    /**
     * 批量写入文件，按日期+类型分组，使用缓存的 BufferedWriter
     */
    private void writeToFileBatch(List<EconomyLog> logs) {
        for (EconomyLog log : logs) {
            String logType = getLogType(log.getAction());
            LocalDateTime logTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(log.getTimestamp()), ZoneId.systemDefault());
            String dateKey = fileDateFormat.format(logTime);
            String writerKey = logType + "-" + dateKey;

            try {
                BufferedWriter writer = writerCache.get(writerKey);
                if (writer == null) {
                    File logFile = new File(logFolder, writerKey + ".log");
                    writer = new BufferedWriter(new FileWriter(logFile, true));
                    writerCache.put(writerKey, writer);
                }

                String timestamp = dateFormat.format(logTime);
                String logLine = formatLogLine(log, timestamp);
                writer.write(logLine);
                writer.newLine();
            } catch (IOException e) {
                plugin.getLogger().warning(String.format("写入日志文件失败：%s", e.getMessage()));
            }
        }

        // flush 所有已打开的 writer
        for (BufferedWriter writer : writerCache.values()) {
            try {
                writer.flush();
            } catch (IOException e) {
                plugin.getLogger().warning(String.format("刷新日志文件缓冲失败：%s", e.getMessage()));
            }
        }
    }

    /**
     * 关闭所有缓存的 BufferedWriter
     */
    private void closeAllWriters() {
        for (Map.Entry<String, BufferedWriter> entry : writerCache.entrySet()) {
            try {
                entry.getValue().close();
            } catch (IOException e) {
                plugin.getLogger().warning(String.format("关闭日志文件写入器失败 [%s]：%s", entry.getKey(), e.getMessage()));
            }
        }
        writerCache.clear();
    }

    private String getLogType(String action) {
        if ("DEPOSIT".equals(action) || "WITHDRAW".equals(action) || "SET".equals(action)) {
            return "admin";
        } else if ("TRANSACTION".equals(action) || "PAYMENT".equals(action) || "PAYMENT_RECEIVED".equals(action)) {
            return "transaction";
        }
        return "economy";
    }

    private String formatLogLine(EconomyLog log, String timestamp) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timestamp).append("] ");
        sb.append("[").append(log.getAction()).append("] ");
        sb.append("玩家: ").append(log.getPlayerName());
        sb.append(" | 金额: ").append(plugin.getCurrencyConfig().format(log.getAmount()));
        sb.append(" | 余额: ").append(plugin.getCurrencyConfig().format(log.getBalanceBefore()));
        sb.append(" -> ").append(plugin.getCurrencyConfig().format(log.getBalanceAfter()));

        if (log.getOperatorName() != null && !log.getOperatorName().isEmpty()) {
            sb.append(" | 操作者: ").append(log.getOperatorName());
        }

        if (log.getReason() != null && !log.getReason().isEmpty()) {
            sb.append(" | 原因: ").append(log.getReason());
        }

        return sb.toString();
    }

    public void cleanupOldLogs() {
        int retentionDays = plugin.getConfig().getInt("logging.retention-days", 30);
        if (retentionDays <= 0) {
            return;
        }

        SchedulerUtils.runAsync(plugin, () -> {
            try {
                logDAO.cleanupOldLogs(retentionDays);
                plugin.getDatabaseManager().getTransactionDAO().cleanupOldTransactions(retentionDays);
                cleanupOldLogFiles(retentionDays);
            } catch (SQLException e) {
                plugin.getLogger().severe(String.format("清理过期日志失败：%s", e.getMessage()));
            }
        });
    }

    private void cleanupOldLogFiles(int retentionDays) {
        long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60 * 60 * 1000);
        File[] files = logFolder.listFiles((dir, name) -> name.endsWith(".log"));
        if (files != null) {
            for (File file : files) {
                if (file.lastModified() < cutoffTime) {
                    // 先从缓存中移除对应的 writer（如果存在）
                    String fileName = file.getName().replace(".log", "");
                    BufferedWriter writer = writerCache.remove(fileName);
                    if (writer != null) {
                        try {
                            writer.close();
                        } catch (IOException ignored) {
                        }
                    }
                    if (file.delete()) {
                        plugin.getLogger().info(String.format("删除过期日志文件：%s", file.getName()));
                    }
                }
            }
        }
    }
}
