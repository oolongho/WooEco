package me.yic.xconomy.data.syncdata;

import com.oolonghoo.wooeco.api.WooEcoAPI;
import com.oolonghoo.wooeco.model.PlayerAccount;

import java.math.BigDecimal;
import java.util.UUID;

public class PlayerData extends SyncData {

    private final UUID uuid;
    private final String name;
    private BigDecimal balance;

    public PlayerData(UUID uuid, String name, BigDecimal balance) {
        super(uuid);
        this.uuid = uuid;
        this.name = name;
        this.balance = balance;
    }

    public static PlayerData fromAccount(PlayerAccount account) {
        if (account == null) return null;
        return new PlayerData(account.getUuid(), account.getPlayerName(), account.getBalance());
    }

    public String getName() {
        return name;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        if (WooEcoAPI.isLoaded()) {
            WooEcoAPI.set(uuid, balance.doubleValue());
        }
        this.balance = balance;
    }

    public void setVerifyBalance(BigDecimal balance) {
        this.balance = balance;
    }

    @Override
    public UUID getUniqueId() {
        return uuid;
    }
}
