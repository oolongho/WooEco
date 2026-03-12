package com.oolonghoo.wooeco.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 日志工具类
 * 提供统一的日志记录方法
 * 
 * @author oolongho
 */
public class LoggerUtil {
    
    private static final String PREFIX = "[WooEco] ";
    
    /**
     * 记录严重错误日志
     * 
     * @param logger 日志记录器
     * @param message 消息模板
     * @param args 参数
     */
    public static void severe(Logger logger, String message, Object... args) {
        logger.severe(new StringBuilder(PREFIX).append(String.format(message, (Object[]) args)).toString());
    }
    
    /**
     * 记录严重错误日志（带异常）
     * 
     * @param logger 日志记录器
     * @param message 消息模板
     * @param throwable 异常
     */
    public static void severe(Logger logger, String message, Throwable throwable) {
        logger.log(Level.SEVERE, PREFIX + message, throwable);
    }
    
    /**
     * 记录警告日志
     * 
     * @param logger 日志记录器
     * @param message 消息模板
     * @param args 参数
     */
    public static void warning(Logger logger, String message, Object... args) {
        logger.warning(new StringBuilder(PREFIX).append(String.format(message, (Object[]) args)).toString());
    }
    
    /**
     * 记录信息日志
     * 
     * @param logger 日志记录器
     * @param message 消息模板
     * @param args 参数
     */
    public static void info(Logger logger, String message, Object... args) {
        logger.info(new StringBuilder(PREFIX).append(String.format(message, (Object[]) args)).toString());
    }
    
    /**
     * 记录调试日志
     * 
     * @param logger 日志记录器
     * @param message 消息模板
     * @param args 参数
     */
    public static void debug(Logger logger, String message, Object... args) {
        logger.fine(new StringBuilder(PREFIX).append(String.format(message, (Object[]) args)).toString());
    }
    
    /**
     * 记录数据库错误日志
     * 
     * @param logger 日志记录器
     * @param operation 操作名称
     * @param e 异常
     */
    public static void databaseError(Logger logger, String operation, Exception e) {
        severe(logger, "%s 失败：%s", operation, e.getMessage());
    }
}
