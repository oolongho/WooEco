package com.oolonghoo.wooeco.util;

import com.oolonghoo.wooeco.WooEco;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * 异步操作工具类
 * 提供带超时控制的异步执行
 * 
 * @author oolongho
 */
public class AsyncUtils {
    
    private static WooEco plugin;
    private static int defaultTimeout = 3;
    
    public static void initialize(WooEco wooEco) {
        plugin = wooEco;
        defaultTimeout = plugin.getConfig().getInt("performance.async-timeout", 3);
        if (defaultTimeout < 1) {
            defaultTimeout = 3;
        }
    }
    
    public static void reload() {
        defaultTimeout = plugin.getConfig().getInt("performance.async-timeout", 3);
        if (defaultTimeout < 1) {
            defaultTimeout = 3;
        }
    }
    
    public static int getDefaultTimeout() {
        return defaultTimeout;
    }
    
    public static <T> T supplyAsyncWithTimeout(Supplier<T> supplier, T defaultValue) {
        return supplyAsyncWithTimeout(supplier, defaultValue, defaultTimeout);
    }
    
    public static <T> T supplyAsyncWithTimeout(Supplier<T> supplier, T defaultValue, int timeoutSeconds) {
        try {
            return CompletableFuture.supplyAsync(supplier)
                    .get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("异步操作被中断: " + e.getMessage());
            return defaultValue;
        } catch (ExecutionException e) {
            plugin.getLogger().log(Level.WARNING, "异步操作执行异常: " + e.getMessage(), e.getCause());
            return defaultValue;
        } catch (TimeoutException e) {
            plugin.getLogger().warning("异步操作超时 (" + timeoutSeconds + "秒)，使用默认值");
            return defaultValue;
        }
    }
    
    public static void runAsyncWithTimeout(Runnable task, int timeoutSeconds) {
        try {
            CompletableFuture.runAsync(task)
                    .get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("异步任务被中断: " + e.getMessage());
        } catch (ExecutionException e) {
            plugin.getLogger().log(Level.WARNING, "异步任务执行异常: " + e.getMessage(), e.getCause());
        } catch (TimeoutException e) {
            plugin.getLogger().warning("异步任务超时 (" + timeoutSeconds + "秒)");
        }
    }
    
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier);
    }
    
    public static CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task);
    }
    
    public static boolean isMainThread() {
        return plugin != null && plugin.getServer().isPrimaryThread();
    }
    
    public static void runOnMainThread(Runnable task) {
        if (plugin == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
    
    public static void runOnMainThreadLater(Runnable task, long delayTicks) {
        if (plugin == null) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }
}
