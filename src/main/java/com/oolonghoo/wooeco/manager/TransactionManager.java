package com.oolonghoo.wooeco.manager;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.api.events.BalanceChangeReason;
import com.oolonghoo.wooeco.api.events.TransactionEvent;
import com.oolonghoo.wooeco.database.dao.TransactionDAO;
import com.oolonghoo.wooeco.model.PlayerAccount;
import com.oolonghoo.wooeco.model.Transaction;
import org.bukkit.Bukkit;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 交易管理器
 * 
 * @author oolongho
 */
public class TransactionManager {
    
    private final WooEco plugin;
    private final PlayerDataManager playerDataManager;
    private final EconomyManager economyManager;
    private final TaxManager taxManager;
    private final LogManager logManager;
    private final TransactionDAO transactionDAO;
    
    public TransactionManager(WooEco plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
        this.economyManager = plugin.getEconomyManager();
        this.taxManager = plugin.getTaxManager();
        this.logManager = plugin.getLogManager();
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
            amount.doubleValue(), tax.doubleValue()
        );
        Bukkit.getPluginManager().callEvent(event);
        
        if (event.isCancelled()) {
            return new TransactionResult(false, "交易被取消", BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        amount = BigDecimal.valueOf(event.getAmount());
        tax = BigDecimal.valueOf(event.getTax());
        totalCost = amount.add(tax);
        
        EconomyManager.EconomyResult withdrawResult = economyManager.withdraw(
            senderUuid, totalCost, BalanceChangeReason.PAYMENT, null, null
        );
        
        if (!withdrawResult.isSuccess()) {
            return new TransactionResult(false, withdrawResult.getErrorMessage(), BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        EconomyManager.EconomyResult depositResult = economyManager.deposit(
            receiverUuid, amount, BalanceChangeReason.PAYMENT_RECEIVED, null, null
        );
        
        if (!depositResult.isSuccess()) {
            economyManager.deposit(senderUuid, totalCost, BalanceChangeReason.ADMIN, null, null);
            return new TransactionResult(false, depositResult.getErrorMessage(), BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        Transaction transaction = new Transaction(
            senderUuid, senderAccount.getPlayerName(),
            receiverUuid, receiverAccount.getPlayerName(),
            amount.doubleValue(), tax.doubleValue()
        );
        
        saveTransactionAsync(transaction);
        
        if (!plugin.getPlayerDataManager().isOnline(receiverUuid)) {
            plugin.getOfflineTransferManager().recordOfflineTransfer(
                receiverUuid, senderAccount.getPlayerName(), amount.doubleValue()
            );
        }
        
        return new TransactionResult(true, null, amount, tax);
    }
    
    private void saveTransactionAsync(Transaction transaction) {
        if (plugin.getConfig().getBoolean("logging.transaction", true)) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    transactionDAO.saveTransaction(transaction);
                } catch (Exception e) {
                    plugin.getLogger().severe("保存交易记录失败: " + e.getMessage());
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
