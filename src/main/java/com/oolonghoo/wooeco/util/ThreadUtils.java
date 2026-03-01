package com.oolonghoo.wooeco.util;

import com.oolonghoo.wooeco.WooEco;
import org.bukkit.Bukkit;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 线程工具类
 * 提供智能调度和线程管理
 * 包含操作队列和限流机制
 * 
 * @author oolongho
 */
public class ThreadUtils {
    
    private static WooEco plugin;
    private static boolean forceAsync = false;
    private static ExecutorService executorService;
    private static ScheduledExecutorService scheduledExecutorService;
    private static ThreadPoolExecutor threadPoolExecutor;
    private static Semaphore operationSemaphore;
    private static int maxConcurrentOperations = 10;
    private static AtomicInteger activeOperations = new AtomicInteger(0);
    private static AtomicInteger queuedOperations = new AtomicInteger(0);
    private static int maxQueueSize = 100;
    
    public static void initialize(WooEco wooEco) {
        plugin = wooEco;
        forceAsync = plugin.getConfig().getBoolean("performance.force-async", false);
        maxConcurrentOperations = plugin.getConfig().getInt("performance.max-concurrent-operations", 10);
        maxQueueSize = plugin.getConfig().getInt("performance.max-queue-size", 100);
        
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = Math.max(corePoolSize * 2, maxConcurrentOperations);
        
        threadPoolExecutor = new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(maxQueueSize),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);
                
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "WooEco-Worker-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.DiscardOldestPolicy()
        );
        
        executorService = threadPoolExecutor;
        scheduledExecutorService = Executors.newScheduledThreadPool(2);
        operationSemaphore = new Semaphore(maxConcurrentOperations);
    }
    
    public static void reload() {
        forceAsync = plugin.getConfig().getBoolean("performance.force-async", false);
        int newMaxConcurrent = plugin.getConfig().getInt("performance.max-concurrent-operations", 10);
        int newMaxQueue = plugin.getConfig().getInt("performance.max-queue-size", 100);
        
        if (newMaxConcurrent != maxConcurrentOperations) {
            maxConcurrentOperations = newMaxConcurrent;
            operationSemaphore = new Semaphore(maxConcurrentOperations);
        }
        
        if (newMaxQueue != maxQueueSize) {
            maxQueueSize = newMaxQueue;
        }
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
    
    public static boolean tryAcquireOperation() {
        try {
            if (queuedOperations.get() >= maxQueueSize) {
                return false;
            }
            queuedOperations.incrementAndGet();
            boolean acquired = operationSemaphore.tryAcquire(5, TimeUnit.SECONDS);
            if (acquired) {
                activeOperations.incrementAndGet();
            }
            queuedOperations.decrementAndGet();
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    public static void releaseOperation() {
        activeOperations.decrementAndGet();
        operationSemaphore.release();
    }
    
    public static void runWithThrottle(Runnable task) {
        runWithThrottle(task, () -> {
            plugin.getLogger().warning("操作队列已满，请稍后重试");
        });
    }
    
    public static void runWithThrottle(Runnable task, Runnable onRejected) {
        if (tryAcquireOperation()) {
            executorService.submit(() -> {
                try {
                    task.run();
                } finally {
                    releaseOperation();
                }
            });
        } else {
            if (onRejected != null) {
                onRejected.run();
            }
        }
    }
    
    public static <T> void submitWithThrottle(java.util.concurrent.Callable<T> task, Consumer<T> callback, Consumer<Exception> errorHandler) {
        if (tryAcquireOperation()) {
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
                    }
                } finally {
                    releaseOperation();
                }
            });
        } else {
            if (errorHandler != null) {
                runTask(() -> errorHandler.accept(new RejectedExecutionException("操作队列已满")));
            }
        }
    }
    
    public static int getActiveOperations() {
        return activeOperations.get();
    }
    
    public static int getQueuedOperations() {
        return queuedOperations.get();
    }
    
    public static int getMaxConcurrentOperations() {
        return maxConcurrentOperations;
    }
    
    public static int getMaxQueueSize() {
        return maxQueueSize;
    }
    
    public static int getPoolSize() {
        return threadPoolExecutor != null ? threadPoolExecutor.getPoolSize() : 0;
    }
    
    public static int getActiveCount() {
        return threadPoolExecutor != null ? threadPoolExecutor.getActiveCount() : 0;
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
