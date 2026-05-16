package com.oolonghoo.wooeco.migration;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.database.dao.NonPlayerAccountDAO;
import com.oolonghoo.wooeco.database.dao.PlayerDAO;
import com.oolonghoo.wooeco.model.NonPlayerAccount;
import com.oolonghoo.wooeco.model.PlayerAccount;
import com.oolonghoo.wooeco.vault.VaultHook;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class VaultMigrator {
    private final WooEco plugin;
    private final Consumer<String> progressCallback;
    private final boolean dryRun;

    public VaultMigrator(WooEco plugin, Consumer<String> progressCallback, boolean dryRun) {
        this.plugin = plugin;
        this.progressCallback = progressCallback;
        this.dryRun = dryRun;
    }

    public MigrationResult migrate() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return MigrationResult.failure("Vault", "no-source");
        }

        Economy sourceEcon = rsp.getProvider();
        if (sourceEcon instanceof VaultHook) {
            return MigrationResult.failure("Vault", "self-migrate");
        }

        String sourceName = sourceEcon.getName();
        MigrationResult result = MigrationResult.success(sourceName);
        long startTime = System.currentTimeMillis();

        OfflinePlayer[] players = Bukkit.getOfflinePlayers();
        result.setTotalPlayers(players.length);

        PlayerDAO playerDAO = plugin.getDatabaseManager().getPlayerDAO();
        int lastPercent = -1;

        for (int i = 0; i < players.length; i++) {
            OfflinePlayer player = players[i];
            try {
                if (player.getName() != null && sourceEcon.hasAccount(player)) {
                    double balance = sourceEcon.getBalance(player);
                    BigDecimal balanceDecimal = BigDecimal.valueOf(balance);

                    if (!dryRun) {
                        PlayerAccount account = plugin.getPlayerDataManager().getOrCreateAccount(
                            player.getUniqueId(), player.getName());
                        if (account != null) {
                            account.setBalance(balanceDecimal);
                            playerDAO.saveOrUpdateAccount(account);
                        }
                    }
                    result.incrementMigratedPlayer();
                } else {
                    result.incrementSkippedPlayer();
                }
            } catch (Exception e) {
                result.incrementFailedPlayer();
                result.addError(player.getName() + ": " + e.getMessage());
            }

            int percent = (i + 1) * 100 / players.length / 10 * 10;
            if (percent != lastPercent) {
                lastPercent = percent;
                progressCallback.accept("migration.progress:" + (i + 1) + ":" + players.length + ":" + percent);
            }
        }

        List<String> banks = sourceEcon.getBanks();
        result.setTotalNonPlayerAccounts(banks.size());

        if (!banks.isEmpty()) {
            NonPlayerAccountDAO npDAO = plugin.getDatabaseManager().getNonPlayerAccountDAO();
            for (String bank : banks) {
                try {
                    EconomyResponse resp = sourceEcon.bankBalance(bank);
                    if (resp.type == EconomyResponse.ResponseType.SUCCESS) {
                        if (!dryRun) {
                            long now = System.currentTimeMillis();
                            NonPlayerAccount account = new NonPlayerAccount(bank, BigDecimal.valueOf(resp.balance), now, now);
                            npDAO.createAccount(account);
                        }
                        result.incrementMigratedNonPlayerAccount();
                    }
                } catch (Exception e) {
                    result.addError("Bank " + bank + ": " + e.getMessage());
                }
            }
        }

        result.setDurationMs(System.currentTimeMillis() - startTime);
        return result;
    }
}
