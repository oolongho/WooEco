package com.oolonghoo.wooeco.manager;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.database.dao.LogDAO;
import com.oolonghoo.wooeco.model.EconomyLog;
import com.oolonghoo.wooeco.util.SchedulerUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 日志管理器
 * 同时记录到数据库和日志文件
 * 
 */
public class LogManager {
    
    private final WooEco plugin;
    private final LogDAO logDAO;
    private final File logFolder;
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DateTimeFormatter fileDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    public LogManager(WooEco plugin) {
        this.plugin = plugin;
        this.logDAO = plugin.getDatabaseManager().getLogDAO();
        this.logFolder = new File(plugin.getDataFolder(), "logs");
        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }
    }
    
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
        
        saveLogAsync(log);
        writeToFile(log);
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
    
    private void saveLogAsync(EconomyLog log) {
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                logDAO.saveLog(log);
            } catch (SQLException e) {
                plugin.getLogger().severe(String.format("保存日志失败：%s", e.getMessage()));
            }
        });
    }
    
    private void writeToFile(EconomyLog log) {
        String logType = getLogType(log.getAction());
        File logFile = new File(logFolder, logType + "-" + fileDateFormat.format(LocalDateTime.now()) + ".log");
        
        SchedulerUtils.runAsync(plugin, () -> {
            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                String timestamp = dateFormat.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(log.getTimestamp()), ZoneId.systemDefault()));
                String logLine = formatLogLine(log, timestamp);
                writer.println(logLine);
            } catch (IOException e) {
                plugin.getLogger().warning(String.format("写入日志文件失败：%s", e.getMessage()));
            }
        });
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
                    if (file.delete()) {
                        plugin.getLogger().info(String.format("删除过期日志文件：%s", file.getName()));
                    }
                }
            }
        }
    }
}
