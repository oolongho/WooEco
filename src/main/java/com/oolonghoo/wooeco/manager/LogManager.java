package com.oolonghoo.wooeco.manager;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.database.dao.LogDAO;
import com.oolonghoo.wooeco.model.EconomyLog;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * 日志管理器
 * 同时记录到数据库和日志文件
 * 
 * @author oolongho
 */
public class LogManager {
    
    private final WooEco plugin;
    private final LogDAO logDAO;
    private final File logFolder;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    
    public LogManager(WooEco plugin) {
        this.plugin = plugin;
        this.logDAO = plugin.getDatabaseManager().getLogDAO();
        this.logFolder = new File(plugin.getDataFolder(), "logs");
        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }
    }
    
    public void logBalanceChange(UUID uuid, String playerName, String action, 
                                  double amount, double balanceBefore, double balanceAfter,
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
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                logDAO.saveLog(log);
            } catch (Exception e) {
                plugin.getLogger().severe("保存日志失败: " + e.getMessage());
            }
        });
    }
    
    private void writeToFile(EconomyLog log) {
        String logType = getLogType(log.getAction());
        File logFile = new File(logFolder, logType + "-" + fileDateFormat.format(new Date()) + ".log");
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                String timestamp = dateFormat.format(new Date(log.getTimestamp()));
                String logLine = formatLogLine(log, timestamp);
                writer.println(logLine);
            } catch (IOException e) {
                plugin.getLogger().warning("写入日志文件失败: " + e.getMessage());
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
        sb.append(" | 金额: ").append(String.format("%.2f", log.getAmount()));
        sb.append(" | 余额: ").append(String.format("%.2f", log.getBalanceBefore()));
        sb.append(" -> ").append(String.format("%.2f", log.getBalanceAfter()));
        
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
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                logDAO.cleanupOldLogs(retentionDays);
                plugin.getDatabaseManager().getTransactionDAO().cleanupOldTransactions(retentionDays);
                cleanupOldLogFiles(retentionDays);
            } catch (Exception e) {
                plugin.getLogger().severe("清理过期日志失败: " + e.getMessage());
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
                        plugin.getLogger().info("删除过期日志文件: " + file.getName());
                    }
                }
            }
        }
    }
}
