package com.oolonghoo.wooeco.api;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.manager.EconomyManager;
import com.oolonghoo.wooeco.manager.TransactionManager;
import com.oolonghoo.wooeco.model.PlayerAccount;

import java.util.List;
import java.util.UUID;

/**
 * WooEco API接口
 * 
 * 提供静态方法访问WooEco插件的经济功能。
 * 所有方法都是线程安全的，可以从任何线程调用。
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 检查插件是否已加载
 * if (WooEcoAPI.isLoaded()) {
 *     // 获取玩家余额
 *     double balance = WooEcoAPI.getBalance(player.getUniqueId());
 *     
 *     // 存款
 *     EconomyManager.EconomyResult result = WooEcoAPI.deposit(player.getUniqueId(), 100.0);
 *     if (result.isSuccess()) {
 *         player.sendMessage("存款成功！新余额: " + result.getBalance());
 *     }
 *     
 *     // 转账
 *     TransactionManager.TransactionResult txResult = WooEcoAPI.transfer(
 *         player1.getUniqueId(), 
 *         player2.getUniqueId(), 
 *         50.0
 *     );
 * }
 * }</pre>
 * 
 * <h2>依赖说明</h2>
 * <p>在plugin.yml中添加软依赖:</p>
 * <pre>
 * softdepend: [WooEco]
 * </pre>
 * 
 * <h2>线程安全</h2>
 * <p>所有API方法都是线程安全的，可以异步调用。
 * 数据库操作会自动在异步线程中执行，不会阻塞主线程。</p>
 * 
 * @since 1.0.0
 */
public class WooEcoAPI {
    
    private static WooEco instance;
    
    /**
     * 初始化API（内部使用）
     * 
     * @param plugin WooEco插件实例
     */
    public static void initialize(WooEco plugin) {
        instance = plugin;
    }
    
    /**
     * 获取WooEco插件实例
     * 
     * @return WooEco插件实例，如果插件未加载则返回null
     */
    public static WooEco getInstance() {
        return instance;
    }
    
    /**
     * 检查WooEco插件是否已加载且启用
     * 
     * @return 如果插件已加载且启用则返回true
     */
    public static boolean isLoaded() {
        return instance != null && instance.isEnabled();
    }
    
    /**
     * 获取玩家余额
     * 
     * @param uuid 玩家UUID
     * @return 玩家余额，如果玩家不存在则返回0
     * @throws IllegalStateException 如果插件未加载
     */
    public static double getBalance(UUID uuid) {
        checkLoaded();
        return instance.getEconomyManager().getBalance(uuid);
    }
    
    /**
     * 检查玩家是否有足够的余额
     * 
     * @param uuid 玩家UUID
     * @param amount 需要的金额
     * @return 如果玩家余额足够则返回true
     * @throws IllegalStateException 如果插件未加载
     */
    public static boolean has(UUID uuid, double amount) {
        checkLoaded();
        return instance.getEconomyManager().has(uuid, amount);
    }
    
    /**
     * 从玩家账户扣除金额
     * 
     * @param uuid 玩家UUID
     * @param amount 扣除金额（必须大于0）
     * @return 操作结果，包含是否成功、新余额等信息
     * @throws IllegalStateException 如果插件未加载
     */
    public static EconomyManager.EconomyResult withdraw(UUID uuid, double amount) {
        checkLoaded();
        return instance.getEconomyManager().withdraw(uuid, amount);
    }
    
    /**
     * 向玩家账户存入金额
     * 
     * @param uuid 玩家UUID
     * @param amount 存入金额（必须大于0）
     * @return 操作结果，包含是否成功、新余额等信息
     * @throws IllegalStateException 如果插件未加载
     */
    public static EconomyManager.EconomyResult deposit(UUID uuid, double amount) {
        checkLoaded();
        return instance.getEconomyManager().deposit(uuid, amount);
    }
    
    /**
     * 设置玩家账户余额
     * 
     * @param uuid 玩家UUID
     * @param amount 新余额（不能为负数）
     * @return 操作结果，包含是否成功、新余额等信息
     * @throws IllegalStateException 如果插件未加载
     */
    public static EconomyManager.EconomyResult set(UUID uuid, double amount) {
        checkLoaded();
        return instance.getEconomyManager().set(uuid, amount);
    }
    
    /**
     * 获取玩家账户信息
     * 
     * @param uuid 玩家UUID
     * @return 玩家账户信息，如果玩家不存在则返回null
     * @throws IllegalStateException 如果插件未加载
     */
    public static PlayerAccount getAccount(UUID uuid) {
        checkLoaded();
        return instance.getPlayerDataManager().getAccount(uuid);
    }
    
    /**
     * 检查玩家是否有账户
     * 
     * @param uuid 玩家UUID
     * @return 如果玩家有账户则返回true
     * @throws IllegalStateException 如果插件未加载
     */
    public static boolean hasAccount(UUID uuid) {
        checkLoaded();
        return instance.getPlayerDataManager().getAccount(uuid) != null;
    }
    
    /**
     * 为玩家创建新账户
     * 
     * @param uuid 玩家UUID
     * @param name 玩家名称
     * @throws IllegalStateException 如果插件未加载
     */
    public static void createAccount(UUID uuid, String name) {
        checkLoaded();
        instance.getPlayerDataManager().createNewAccount(uuid, name);
    }
    
    /**
     * 在两个玩家之间转账
     * 
     * @param from 发送者UUID
     * @param to 接收者UUID
     * @param amount 转账金额（必须大于0）
     * @return 操作结果，包含是否成功、实际转账金额、税费等信息
     * @throws IllegalStateException 如果插件未加载
     */
    public static TransactionManager.TransactionResult transfer(UUID from, UUID to, double amount) {
        checkLoaded();
        return instance.getTransactionManager().transfer(from, to, amount);
    }
    
    /**
     * 获取玩家今日收入
     * 
     * @param uuid 玩家UUID
     * @return 玩家今日收入
     * @throws IllegalStateException 如果插件未加载
     */
    public static double getDailyIncome(UUID uuid) {
        checkLoaded();
        return instance.getEconomyManager().getDailyIncome(uuid);
    }
    
    /**
     * 获取货币名称
     * 
     * @return 货币名称
     * @throws IllegalStateException 如果插件未加载
     */
    public static String getCurrencyName() {
        checkLoaded();
        return instance.getCurrencyConfig().getName();
    }
    
    /**
     * 获取货币符号
     * 
     * @return 货币符号（可能包含颜色代码）
     * @throws IllegalStateException 如果插件未加载
     */
    public static String getCurrencySymbol() {
        checkLoaded();
        return instance.getCurrencyConfig().getSymbol();
    }
    
    /**
     * 格式化金额显示
     * 
     * @param amount 金额
     * @return 格式化后的金额字符串
     * @throws IllegalStateException 如果插件未加载
     */
    public static String format(double amount) {
        checkLoaded();
        return instance.getCurrencyConfig().format(amount);
    }
    
    /**
     * 格式化金额显示（带颜色）
     * 
     * @param amount 金额
     * @return 格式化后的金额字符串（带颜色代码）
     * @throws IllegalStateException 如果插件未加载
     */
    public static String formatWithColor(double amount) {
        checkLoaded();
        return instance.getCurrencyConfig().formatWithColor(amount);
    }
    
    /**
     * 获取余额排行榜
     * 
     * @param limit 返回数量限制
     * @return 余额排行榜玩家账户列表（按余额降序）
     * @throws IllegalStateException 如果插件未加载
     */
    public static List<PlayerAccount> getTopBalances(int limit) {
        checkLoaded();
        return instance.getLeaderboardManager().getBalanceTop(1, limit);
    }
    
    /**
     * 获取收入排行榜
     * 
     * @param limit 返回数量限制
     * @return 收入排行榜玩家账户列表（按收入降序）
     * @throws IllegalStateException 如果插件未加载
     */
    public static List<PlayerAccount> getTopIncomes(int limit) {
        checkLoaded();
        return instance.getLeaderboardManager().getIncomeTop(1, limit);
    }
    
    private static void checkLoaded() {
        if (!isLoaded()) {
            throw new IllegalStateException("WooEco is not loaded!");
        }
    }
}
