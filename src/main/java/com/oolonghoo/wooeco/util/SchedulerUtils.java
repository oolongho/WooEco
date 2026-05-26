package com.oolonghoo.wooeco.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Folia 兼容的调度工具类。
 * <p>
 * 使用 Folia 新版调度器 API（异步调度器、全局区域调度器、实体调度器），
 * 替代旧版 Bukkit.getScheduler() API，确保插件在 Folia 环境下正常运行。
 * <p>
 * 注意：{@link #callEvent(Plugin, Event)} 方法是阻塞式的，
 * 会在全局区域线程上同步触发事件并等待完成，与旧版 CountDownLatch 行为一致。
 */
public final class SchedulerUtils {

    private SchedulerUtils() {
    }

    /**
     * 在异步线程上立即执行任务。
     *
     * @param plugin 插件实例
     * @param task   要执行的任务
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    /**
     * 在异步线程上延迟执行任务。
     *
     * @param plugin  插件实例
     * @param task    要执行的任务
     * @param delayMs 延迟时间（毫秒）
     */
    public static void runAsyncDelayed(Plugin plugin, Runnable task, long delayMs) {
        Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 在异步线程上以固定频率重复执行任务。
     *
     * @param plugin  插件实例
     * @param task    要执行的任务
     * @param delayMs 首次执行延迟（毫秒）
     * @param periodMs 执行间隔（毫秒）
     * @return 调度任务句柄，可用于取消任务
     */
    public static ScheduledTask runAsyncTimer(Plugin plugin, Runnable task, long delayMs, long periodMs) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delayMs, periodMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 在全局区域线程上立即执行任务。
     *
     * @param plugin 插件实例
     * @param task   要执行的任务
     */
    public static void runGlobal(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
    }

    /**
     * 在全局区域线程上延迟执行任务。
     *
     * @param plugin 插件实例
     * @param task   要执行的任务
     * @param ticks  延迟时间（tick）
     */
    public static void runGlobalDelayed(Plugin plugin, Runnable task, long ticks) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), ticks);
    }

    /**
     * 在实体所属的区域线程上立即执行任务。
     * <p>
     * 若实体已卸载或无效，任务可能不会执行。
     *
     * @param plugin 插件实例
     * @param entity 目标实体
     * @param task   要执行的任务
     */
    public static void runForEntity(Plugin plugin, Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }

    /**
     * 在实体所属的区域线程上延迟执行任务。
     * <p>
     * 若实体已卸载或无效，任务可能不会执行。
     *
     * @param plugin 插件实例
     * @param entity 目标实体
     * @param task   要执行的任务
     * @param ticks  延迟时间（tick）
     */
    public static void runForEntityDelayed(Plugin plugin, Entity entity, Runnable task, long ticks) {
        entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, ticks);
    }

    /**
     * 在全局区域线程上触发事件，并阻塞等待事件完成。
     * <p>
     * 使用 CompletableFuture 实现阻塞等待，确保事件触发后
     * 调用方可以立即检查事件状态（如 {@code event.isCancelled()}）。
     * <p>
     * 此方法在以下场景中是线程安全的：
     * <ul>
     *   <li>从异步线程调用：阻塞异步线程等待全局区域线程执行，无死锁风险</li>
     *   <li>从全局区域线程调用：{@code run()} 会立即同步执行，无死锁风险</li>
     *   <li>从 Folia 区域线程调用：全局区域线程与区域线程独立，无循环依赖</li>
     * </ul>
     *
     * @param plugin 插件实例
     * @param event  要触发的事件
     */
    public static void callEvent(Plugin plugin, Event event) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> {
            try {
                Bukkit.getPluginManager().callEvent(event);
            } finally {
                future.complete(null);
            }
        });
        future.join();
    }

    /**
     * 将 tick 转换为毫秒。
     * <p>
     * 1 tick = 50ms（Minecraft 标准tick时长）。
     *
     * @param ticks tick 数量
     * @return 对应的毫秒数
     */
    public static long ticksToMs(long ticks) {
        return ticks * 50L;
    }
}
