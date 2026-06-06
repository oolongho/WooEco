package com.oolonghoo.wooeco.util;

import com.oolonghoo.wooeco.WooEco;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * 异步操作工具类
 * 提供带超时控制的异步执行
 * 
 */
public class AsyncUtils {
    
    private static WooEco plugin;
    private static int defaultTimeout = 3;
    private static ExecutorService executor;
    
    public static void initialize(WooEco wooEco) {
        plugin = wooEco;
        defaultTimeout = plugin.getConfig().getInt("performance.async-timeout", 3);
        if (defaultTimeout < 1) {
            defaultTimeout = 3;
        }
        int poolSize = plugin.getConfig().getInt("performance.async-pool-size", 4);
        if (poolSize < 1) {
            poolSize = 4;
        }
        executor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "WooEco-Async");
            t.setDaemon(true);
            return t;
        });
    }
    
    public static void reload() {
        defaultTimeout = plugin.getConfig().getInt("performance.async-timeout", 3);
        if (defaultTimeout < 1) {
            defaultTimeout = 3;
        }
    }
    
    public static void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("[WooEco] 异步线程池未在10秒内关闭，强制终止");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
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
            return CompletableFuture.supplyAsync(supplier, executor)
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
            CompletableFuture.runAsync(task, executor)
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
        return CompletableFuture.supplyAsync(supplier, executor);
    }
    
    public static CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, executor);
    }
}
