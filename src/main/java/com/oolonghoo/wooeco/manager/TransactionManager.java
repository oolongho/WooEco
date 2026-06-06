package com.oolonghoo.wooeco.manager;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.api.events.BalanceChangeEvent;
import com.oolonghoo.wooeco.api.events.BalanceChangeReason;
import com.oolonghoo.wooeco.api.events.TransactionEvent;
import com.oolonghoo.wooeco.database.dao.PlayerDAO;
import com.oolonghoo.wooeco.database.dao.TransactionDAO;
import com.oolonghoo.wooeco.model.PlayerAccount;
import com.oolonghoo.wooeco.model.Transaction;
import com.oolonghoo.wooeco.util.SchedulerUtils;

/**
 * 交易管理器
 * 
 */
public class TransactionManager {
    
    private final WooEco plugin;
    private final PlayerDataManager playerDataManager;
    private final EconomyManager economyManager;
    private final TaxManager taxManager;
    private final TransactionDAO transactionDAO;
    
    public TransactionManager(WooEco plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
        this.economyManager = plugin.getEconomyManager();
        this.taxManager = plugin.getTaxManager();
        this.transactionDAO = plugin.getDatabaseManager().getTransactionDAO();
    }
    
    public TransactionResult transfer(UUID senderUuid, UUID receiverUuid, double amount) {
        return transfer(senderUuid, receiverUuid, BigDecimal.valueOf(amount));
    }
    
    public TransactionResult transfer(UUID senderUuid, UUID receiverUuid, BigDecimal amount) {
        if (senderUuid.equals(receiverUuid)) {
            return new TransactionResult(false, "不能给自己转账", BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        PlayerAccount senderAccount = playerDataManager.getAccount(senderUuid);
        if (senderAccount == null) {
            return new TransactionResult(false, "发送方账户不存在", BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        PlayerAccount receiverAccount = playerDataManager.getAccount(receiverUuid);
        if (receiverAccount == null) {
            return new TransactionResult(false, "接收方账户不存在", BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        if (!plugin.getPayToggleManager().isPayEnabled(receiverUuid)) {
            return new TransactionResult(false, "paytoggle.cannot-pay", BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        double minAmount = plugin.getConfig().getDouble("transaction.min-amount", 1);
        double maxAmount = plugin.getConfig().getDouble("transaction.max-amount", 1000000);
        
        if (amount.compareTo(BigDecimal.valueOf(minAmount)) < 0) {
            return new TransactionResult(false, "金额小于最小转账限制", BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        if (amount.compareTo(BigDecimal.valueOf(maxAmount)) > 0) {
            return new TransactionResult(false, "金额超过最大转账限制", BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        BigDecimal tax = taxManager.calculateTaxDecimal(senderUuid, amount);
        BigDecimal totalCost = amount.add(tax);
        
        if (!economyManager.has(senderUuid, totalCost)) {
            return new TransactionResult(false, "余额不足", BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        TransactionEvent event = new TransactionEvent(
            senderUuid, senderAccount.getPlayerName(),
            receiverUuid, receiverAccount.getPlayerName(),
            amount, tax
        );
        SchedulerUtils.callEvent(plugin, event);
        
        if (event.isCancelled()) {
            return new TransactionResult(false, "交易被取消", BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        amount = event.getAmountDecimal();
        tax = event.getTaxDecimal();
        totalCost = amount.add(tax);
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new TransactionResult(false, "转账金额必须大于0", BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        if (!economyManager.has(senderUuid, totalCost)) {
            return new TransactionResult(false, "余额不足", BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        // 1. 先确定税收接收者（taxManager 的缓存是线程安全的，可在锁外读取）
        UUID taxReceiverUuid = null;
        PlayerAccount taxReceiverAccount = null;
        if (tax.compareTo(BigDecimal.ZERO) > 0 && !taxManager.isTaxDestroyed()) {
            taxReceiverUuid = taxManager.getTaxReceiverUUID();
            if (taxReceiverUuid != null) {
                String taxReceiverName = taxManager.getTaxReceiverName();
                if (taxReceiverName == null) {
                    taxReceiverName = "TaxReceiver";
                }
                taxReceiverAccount = playerDataManager.getOrCreateAccount(taxReceiverUuid, taxReceiverName);
            }
        }

        // 2. 收集所有需要锁的 UUID，去重后按排序确定锁获取顺序，防止死锁
        List<UUID> lockUuids = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (UUID uuid : Arrays.asList(senderUuid, receiverUuid, taxReceiverUuid)) {
            if (uuid != null && seen.add(uuid)) {
                lockUuids.add(uuid);
            }
        }
        Collections.sort(lockUuids);

        // 3. 按排序顺序获取对应的 PlayerAccount
        Map<UUID, PlayerAccount> accountMap = new HashMap<>();
        accountMap.put(senderUuid, senderAccount);
        accountMap.put(receiverUuid, receiverAccount);
        if (taxReceiverUuid != null && taxReceiverAccount != null) {
            accountMap.put(taxReceiverUuid, taxReceiverAccount);
        }
        List<PlayerAccount> orderedAccounts = new ArrayList<>();
        for (UUID uuid : lockUuids) {
            orderedAccounts.add(accountMap.get(uuid));
        }

        // 事务提交后需要触发的后置操作数据（使用数组作为可变持有者，以便在 lambda 中赋值）
        final BigDecimal[] senderOldBalance = new BigDecimal[1];
        final BigDecimal[] receiverOldBalance = new BigDecimal[1];
        final BigDecimal[] senderNewBalance = new BigDecimal[1];
        final BigDecimal[] receiverNewBalance = new BigDecimal[1];
        final BigDecimal[] taxReceiverOldBalance = new BigDecimal[1];
        final BigDecimal[] taxReceiverNewBalance = new BigDecimal[1];
        final AtomicReference<TransactionResult> failureResult = new AtomicReference<>();

        final BigDecimal fAmount = amount;
        final BigDecimal fTax = tax;
        final BigDecimal fTotalCost = totalCost;
        final UUID fTaxReceiverUuid = taxReceiverUuid;
        final PlayerAccount fTaxReceiverAccount = taxReceiverAccount;

        // 4. 按排序顺序依次获取锁，在最内层执行所有操作
        executeWithOrderedLocks(orderedAccounts, 0, () -> {
            // 事件可能修改了金额，需在锁内重新校验余额
            if (!economyManager.has(senderUuid, fTotalCost)) {
                failureResult.set(new TransactionResult(false, "余额不足", BigDecimal.ZERO, BigDecimal.ZERO));
                return;
            }

            // 计算新余额
            senderOldBalance[0] = senderAccount.getBalance();
            receiverOldBalance[0] = receiverAccount.getBalance();
            senderNewBalance[0] = senderOldBalance[0].subtract(fTotalCost);
            receiverNewBalance[0] = receiverOldBalance[0].add(fAmount);

            // 税收接收者余额
            if (fTaxReceiverAccount != null) {
                taxReceiverOldBalance[0] = fTaxReceiverAccount.getBalance();
                taxReceiverNewBalance[0] = taxReceiverOldBalance[0].add(fTax);
            }

            // 在单一 DB 事务中原子更新所有余额
            final BigDecimal fTaxReceiverNewBal = taxReceiverNewBalance[0];
            try {
                PlayerDAO playerDAO = plugin.getDatabaseManager().getPlayerDAO();
                plugin.getDatabaseManager().executeInTransaction(conn -> {
                    playerDAO.updateBalanceInTransaction(conn, senderUuid, senderNewBalance[0]);
                    playerDAO.updateBalanceInTransaction(conn, receiverUuid, receiverNewBalance[0]);
                    if (fTaxReceiverUuid != null && fTaxReceiverNewBal != null) {
                        playerDAO.updateBalanceInTransaction(conn, fTaxReceiverUuid, fTaxReceiverNewBal);
                    }
                    return null;
                });
            } catch (SQLException e) {
                plugin.getLogger().severe(String.format("转账事务执行失败：%s", e.getMessage()));
                failureResult.set(new TransactionResult(false, "转账失败，请稍后重试", BigDecimal.ZERO, BigDecimal.ZERO));
                return;
            }

            // DB 事务提交成功，更新内存中的 PlayerAccount
            senderAccount.setBalance(senderNewBalance[0]);
            receiverAccount.setBalance(receiverNewBalance[0]);
            receiverAccount.addDailyIncome(fAmount);
            if (fTaxReceiverAccount != null && taxReceiverNewBalance[0] != null) {
                fTaxReceiverAccount.setBalance(taxReceiverNewBalance[0]);
            }
        });

        // 检查锁内操作是否失败
        TransactionResult earlyFailure = failureResult.get();
        if (earlyFailure != null) {
            return earlyFailure;
        }

        // ---- 事务已提交，以下为后置操作（事件、日志、同步等） ----

        // 触发 BalanceChangeEvent
        SchedulerUtils.callEvent(plugin, new BalanceChangeEvent(
            senderUuid, senderOldBalance[0], senderNewBalance[0], totalCost.negate(), BalanceChangeReason.PAYMENT));
        SchedulerUtils.callEvent(plugin, new BalanceChangeEvent(
            receiverUuid, receiverOldBalance[0], receiverNewBalance[0], amount, BalanceChangeReason.PAYMENT_RECEIVED));
        if (taxReceiverAccount != null && tax.compareTo(BigDecimal.ZERO) > 0) {
            SchedulerUtils.callEvent(plugin, new BalanceChangeEvent(
                taxReceiverUuid, taxReceiverOldBalance[0], taxReceiverNewBalance[0], tax, BalanceChangeReason.TAX));
        }

        // 记录余额变动日志
        plugin.getLogManager().logBalanceChange(senderUuid, senderAccount.getPlayerName(), "WITHDRAW",
            totalCost, senderOldBalance[0], senderNewBalance[0], null, null, BalanceChangeReason.PAYMENT.name());
        plugin.getLogManager().logBalanceChange(receiverUuid, receiverAccount.getPlayerName(), "DEPOSIT",
            amount, receiverOldBalance[0], receiverNewBalance[0], null, null, BalanceChangeReason.PAYMENT_RECEIVED.name());
        if (taxReceiverAccount != null && tax.compareTo(BigDecimal.ZERO) > 0) {
            plugin.getLogManager().logBalanceChange(taxReceiverUuid, taxReceiverAccount.getPlayerName(), "DEPOSIT",
                tax, taxReceiverOldBalance[0], taxReceiverNewBalance[0], null, "TAX_SYSTEM", BalanceChangeReason.TAX.name());
        }

        // Redis 同步
        if (plugin.getRedisSyncManager() != null) {
            plugin.getRedisSyncManager().publishBalanceUpdate(senderUuid, senderAccount.getPlayerName(), senderNewBalance[0]);
            plugin.getRedisSyncManager().publishBalanceUpdate(receiverUuid, receiverAccount.getPlayerName(), receiverNewBalance[0]);
            if (taxReceiverAccount != null) {
                plugin.getRedisSyncManager().publishBalanceUpdate(taxReceiverUuid, taxReceiverAccount.getPlayerName(), taxReceiverNewBalance[0]);
            }
        }

        // 触发 XConomy 兼容事件
        fireXConomyEvent(senderUuid, senderAccount.getPlayerName(), senderOldBalance[0], totalCost, "WITHDRAW", BalanceChangeReason.PAYMENT);
        fireXConomyEvent(receiverUuid, receiverAccount.getPlayerName(), receiverOldBalance[0], amount, "DEPOSIT", BalanceChangeReason.PAYMENT_RECEIVED);
        
        Transaction transaction = new Transaction(
            senderUuid, senderAccount.getPlayerName(),
            receiverUuid, receiverAccount.getPlayerName(),
            amount, tax
        );
        
        saveTransactionAsync(transaction);
        
        if (!plugin.getPlayerDataManager().isOnline(receiverUuid)) {
            plugin.getOfflineTransferManager().recordOfflineTransfer(
                receiverUuid, senderAccount.getPlayerName(), amount
            );
        }
        
        return new TransactionResult(true, null, amount, tax);
    }
    
    /**
     * 按排序顺序依次获取锁，防止死锁。
     * 递归地在每个账户上 synchronized，最内层执行 action。
     */
    private void executeWithOrderedLocks(List<PlayerAccount> accounts, int index, Runnable action) {
        if (index >= accounts.size()) {
            action.run();
            return;
        }
        synchronized (accounts.get(index)) {
            executeWithOrderedLocks(accounts, index + 1, action);
        }
    }

    private void fireXConomyEvent(UUID uuid, String playerName, BigDecimal oldBalance,
                                   BigDecimal amount, String operationType, BalanceChangeReason reason) {
        if (!me.yic.xconomy.api.XConomyAPI.isCompatEnabled()) return;
        Boolean isAdd = switch (operationType) {
            case "DEPOSIT" -> true;
            case "WITHDRAW" -> false;
            default -> null;
        };
        String method = reason != null ? reason.name() : operationType;
        me.yic.xconomy.api.event.PlayerAccountEvent xconEvent =
            new me.yic.xconomy.api.event.PlayerAccountEvent(
                uuid, playerName, oldBalance, amount, isAdd, method, method);
        SchedulerUtils.callEvent(plugin, xconEvent);
    }

    private void saveTransactionAsync(Transaction transaction) {
        if (plugin.getConfig().getBoolean("logging.transaction", true)) {
            SchedulerUtils.runAsync(plugin, () -> {
                try {
                    transactionDAO.saveTransaction(transaction);
                } catch (SQLException e) {
                    plugin.getLogger().severe(String.format("保存交易记录失败：%s", e.getMessage()));
                }
            });
        }
    }
    
    public static class TransactionResult {
        private final boolean success;
        private final String errorMessage;
        private final BigDecimal amount;
        private final BigDecimal tax;
        
        public TransactionResult(boolean success, String errorMessage, BigDecimal amount, BigDecimal tax) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.amount = amount;
            this.tax = tax;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public double getAmount() {
            return amount.doubleValue();
        }
        
        public BigDecimal getAmountDecimal() {
            return amount;
        }
        
        public double getTax() {
            return tax.doubleValue();
        }
        
        public BigDecimal getTaxDecimal() {
            return tax;
        }
        
        public double getTotalCost() {
            return amount.add(tax).doubleValue();
        }
        
        public BigDecimal getTotalCostDecimal() {
            return amount.add(tax);
        }
    }
}
