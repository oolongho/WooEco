package com.oolonghoo.wooeco.util;

import com.oolonghoo.wooeco.WooEco;
import org.bukkit.Bukkit;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 线程工具类
 * 提供智能调度和线程管理
 * 
 * @author oolongho
 */
public class ThreadUtils {
    
    private static WooEco plugin;
    private static boolean forceAsync = false;
    private static ExecutorService executorService;
    private static ScheduledExecutorService scheduledExecutorService;
    
    public static void initialize(WooEco wooEco) {
        plugin = wooEco;
        forceAsync = plugin.getConfig().getBoolean("performance.force-async", false);
        
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        executorService = Executors.newFixedThreadPool(corePoolSize);
        scheduledExecutorService = Executors.newScheduledThreadPool(2);
    }
    
    public static void reload() {
        forceAsync = plugin.getConfig().getBoolean("performance.force-async", false);
    }
    
    public static void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
            try {
                if (!scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public static boolean isMainThread() {
        return Bukkit.isPrimaryThread();
    }
    
    public static boolean shouldRunAsync() {
        return forceAsync || isMainThread();
    }
    
    public static void runSmart(Runnable task) {
        if (shouldRunAsync()) {
            runTaskAsynchronously(task);
        } else {
            task.run();
        }
    }
    
    public static void runTaskAsynchronously(Runnable task) {
        if (plugin == null) {
            task.run();
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }
    
    public static void runTask(Runnable task) {
        if (plugin == null) {
            task.run();
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
    
    public static void runTaskLater(Runnable task, long delayTicks) {
        if (plugin == null) {
            task.run();
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }
    
    public static void runTaskLaterAsync(Runnable task, long delayTicks) {
        if (plugin == null) {
            task.run();
            return;
        }
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
    }
    
    public static void runTaskTimer(Runnable task, long delay, long period) {
        if (plugin == null) {
            return;
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, task, delay, period);
    }
    
    public static void runTaskTimerAsync(Runnable task, long delay, long period) {
        if (plugin == null) {
            return;
        }
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
    }
    
    public static <T> void submitAsync(java.util.concurrent.Callable<T> task, Consumer<T> callback, Consumer<Exception> errorHandler) {
        executorService.submit(() -> {
            try {
                T result = task.call();
                if (callback != null) {
                    runTask(() -> callback.accept(result));
                }
            } catch (Exception e) {
                if (errorHandler != null) {
                    runTask(() -> errorHandler.accept(e));
                } else {
                    plugin.getLogger().severe("异步任务执行失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }
    
    public static void scheduleAsync(Runnable task, long delay, TimeUnit unit) {
        scheduledExecutorService.schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().severe("定时任务执行失败: " + e.getMessage());
            }
        }, delay, unit);
    }
    
    public static void scheduleAsyncAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().severe("定时任务执行失败: " + e.getMessage());
            }
        }, initialDelay, period, unit);
    }
    
    public static boolean isForceAsync() {
        return forceAsync;
    }
    
    public static ExecutorService getExecutorService() {
        return executorService;
    }
    
    public static ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }
}
