package com.oolonghoo.wooeco.model;

import java.util.UUID;

/**
 * 经济日志模型
 * 
 * @author oolongho
 */
public class EconomyLog {
    
    private final long id;
    private final UUID uuid;
    private final String playerName;
    private final String action;
    private final double amount;
    private final double balanceBefore;
    private final double balanceAfter;
    private final String operator;
    private final String operatorName;
    private final String reason;
    private final long timestamp;
    
    public EconomyLog(long id, UUID uuid, String playerName, String action, double amount,
                      double balanceBefore, double balanceAfter, String operator, 
                      String operatorName, String reason, long timestamp) {
        this.id = id;
        this.uuid = uuid;
        this.playerName = playerName;
        this.action = action;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.operator = operator;
        this.operatorName = operatorName;
        this.reason = reason;
        this.timestamp = timestamp;
    }
    
    public EconomyLog(UUID uuid, String playerName, String action, double amount,
                      double balanceBefore, double balanceAfter, String operator, 
                      String operatorName, String reason) {
        this.id = -1;
        this.uuid = uuid;
        this.playerName = playerName;
        this.action = action;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.operator = operator;
        this.operatorName = operatorName;
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
    }
    
    public long getId() {
        return id;
    }
    
    public UUID getUuid() {
        return uuid;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public String getAction() {
        return action;
    }
    
    public double getAmount() {
        return amount;
    }
    
    public double getBalanceBefore() {
        return balanceBefore;
    }
    
    public double getBalanceAfter() {
        return balanceAfter;
    }
    
    public String getOperator() {
        return operator;
    }
    
    public String getOperatorName() {
        return operatorName;
    }
    
    public String getReason() {
        return reason;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
}
