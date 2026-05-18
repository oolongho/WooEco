package com.oolonghoo.wooeco.migration;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.database.dao.LogDAO;
import com.oolonghoo.wooeco.database.dao.NonPlayerAccountDAO;
import com.oolonghoo.wooeco.database.dao.PlayerDAO;
import com.oolonghoo.wooeco.model.EconomyLog;
import com.oolonghoo.wooeco.model.NonPlayerAccount;
import com.oolonghoo.wooeco.model.PlayerAccount;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;
import java.util.function.Consumer;

public class XConomyMigrator {
    private final WooEco plugin;
    private final Consumer<String> progressCallback;
    private final boolean dryRun;

    public XConomyMigrator(WooEco plugin, Consumer<String> progressCallback, boolean dryRun) {
        this.plugin = plugin;
        this.progressCallback = progressCallback;
        this.dryRun = dryRun;
    }

    public MigrationResult migrate() {
        String type = plugin.getConfig().getString("migration.xconomy.type", "MySQL");
        MigrationResult result;
        if ("SQLite".equalsIgnoreCase(type)) {
            result = migrateFromSQLite();
        } else {
            result = migrateFromMySQL();
        }
        return result;
    }

    private MigrationResult migrateFromMySQL() {
        MigrationResult result = MigrationResult.success("XConomy-MySQL");
        long startTime = System.currentTimeMillis();

        String host = plugin.getConfig().getString("migration.xconomy.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("migration.xconomy.mysql.port", 3306);
        String database = plugin.getConfig().getString("migration.xconomy.mysql.database", "");
        String user = plugin.getConfig().getString("migration.xconomy.mysql.user", "");
        String password = plugin.getConfig().getString("migration.xconomy.mysql.password", "");
        String tablePrefix = plugin.getConfig().getString("migration.xconomy.table-prefix", "");
        String tableSuffix = plugin.getConfig().getString("migration.xconomy.table-suffix", "");

        if (database.isEmpty()) {
            return MigrationResult.failure("XConomy-MySQL", "Database name not configured");
        }

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&characterEncoding=utf8&autoReconnect=true";

        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            migratePlayerData(conn, tablePrefix + "xconomy" + tableSuffix, result);
            migrateNonPlayerData(conn, tablePrefix + "xconomynon" + tableSuffix, result);
            if (plugin.getConfig().getBoolean("migration.xconomy.migrate-records", true)) {
                migrateRecords(conn, tablePrefix + "xconomyrecord" + tableSuffix, result);
            }
        } catch (SQLException e) {
            return MigrationResult.failure("XConomy-MySQL", e.getMessage());
        }

        result.setDurationMs(System.currentTimeMillis() - startTime);
        return result;
    }

    private MigrationResult migrateFromSQLite() {
        MigrationResult result = MigrationResult.success("XConomy-SQLite");
        long startTime = System.currentTimeMillis();

        String sqliteFile = plugin.getConfig().getString("migration.xconomy.sqlite-file", "plugins/XConomy/data.db");
        File dbFile = new File(sqliteFile);
        if (!dbFile.isAbsolute()) {
            dbFile = new File(plugin.getDataFolder().getParentFile(), sqliteFile);
        }

        if (!dbFile.exists()) {
            return MigrationResult.failure("XConomy-SQLite", "SQLite file not found: " + dbFile.getAbsolutePath());
        }

        String tablePrefix = plugin.getConfig().getString("migration.xconomy.table-prefix", "");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath())) {
            migratePlayerData(conn, tablePrefix + "xconomy", result);
            migrateNonPlayerData(conn, tablePrefix + "xconomynon", result);
        } catch (SQLException e) {
            return MigrationResult.failure("XConomy-SQLite", e.getMessage());
        }

        result.setDurationMs(System.currentTimeMillis() - startTime);
        return result;
    }

    private void migratePlayerData(Connection sourceConn, String tableName, MigrationResult result) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM " + tableName;
        try (Statement stmt = sourceConn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next()) {
                result.setTotalPlayers(rs.getInt(1));
            }
        }

        PlayerDAO playerDAO = plugin.getDatabaseManager().getPlayerDAO();
        String sql = "SELECT UID, player, balance, hidden FROM " + tableName;
        int lastPercent = -1;
        int processed = 0;

        try (Statement stmt = sourceConn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    String uidStr = rs.getString("UID");
                    String playerName = rs.getString("player");
                    BigDecimal balance = rs.getBigDecimal("balance");
                    int hidden = rs.getInt("hidden");

                    UUID uuid;
                    try {
                        uuid = UUID.fromString(uidStr);
                    } catch (IllegalArgumentException e) {
                        result.incrementFailedPlayer();
                        result.addError("Invalid UUID: " + uidStr);
                        continue;
                    }

                    if (!dryRun) {
                        PlayerAccount account = plugin.getPlayerDataManager().getOrCreateAccount(uuid, playerName);
                        if (account != null) {
                            account.setBalance(plugin.getCurrencyConfig().formatInput(balance != null ? balance : BigDecimal.ZERO));
                            playerDAO.saveOrUpdateAccount(account);
                        }
                    }
                    result.incrementMigratedPlayer();

                    if (hidden == 1) {
                        plugin.getLogger().info("[Migration] Player " + playerName + " is hidden in XConomy (consider adding to blacklist)");
                    }
                } catch (Exception e) {
                    result.incrementFailedPlayer();
                    result.addError(e.getMessage());
                }

                processed++;
                int percent = processed * 100 / result.getTotalPlayers() / 10 * 10;
                if (percent != lastPercent) {
                    lastPercent = percent;
                    progressCallback.accept("migration.progress:" + processed + ":" + result.getTotalPlayers() + ":" + percent);
                }
            }
        }
    }

    private void migrateNonPlayerData(Connection sourceConn, String tableName, MigrationResult result) throws SQLException {
        if (!plugin.getConfig().getBoolean("migration.xconomy.migrate-non-player", true)) return;

        String countSql = "SELECT COUNT(*) FROM " + tableName;
        try (Statement stmt = sourceConn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next()) {
                result.setTotalNonPlayerAccounts(rs.getInt(1));
            }
        }

        NonPlayerAccountDAO npDAO = plugin.getDatabaseManager().getNonPlayerAccountDAO();
        String sql = "SELECT account, balance FROM " + tableName;

        try (Statement stmt = sourceConn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    String accountName = rs.getString("account");
                    BigDecimal balance = rs.getBigDecimal("balance");

                    if (!dryRun) {
                        long now = System.currentTimeMillis();
                        NonPlayerAccount account = new NonPlayerAccount(accountName, plugin.getCurrencyConfig().formatInput(balance != null ? balance : BigDecimal.ZERO), now, now);
                        npDAO.createAccount(account);
                    }
                    result.incrementMigratedNonPlayerAccount();
                } catch (Exception e) {
                    result.addError("NonPlayer: " + e.getMessage());
                }
            }
        }
    }

    private void migrateRecords(Connection sourceConn, String tableName, MigrationResult result) throws SQLException {
        try (Statement stmt = sourceConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            if (rs.next()) {
                result.setTotalRecords(rs.getInt(1));
            }
        }

        String sql = "SELECT type, uid, player, amount, operation, datetime FROM " + tableName;
        try (Statement stmt = sourceConn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    String uid = rs.getString("uid");
                    String playerName = rs.getString("player");
                    BigDecimal amount = rs.getBigDecimal("amount");
                    String action = rs.getString("operation");
                    if (action == null) action = rs.getString("type");
                    if (action == null) action = "MIGRATED";

                    if (!dryRun) {
                        long timestamp = System.currentTimeMillis();
                        try {
                            Timestamp ts = rs.getTimestamp("datetime");
                            if (ts != null) timestamp = ts.getTime();
                        } catch (Exception ignored) {}

                        UUID logUuid;
                        try {
                            logUuid = UUID.fromString(uid);
                        } catch (IllegalArgumentException e) {
                            logUuid = new UUID(0, 0);
                        }

                        EconomyLog log = new EconomyLog(
                            -1, logUuid, playerName != null ? playerName : "Unknown",
                            action, amount != null ? amount : BigDecimal.ZERO,
                            BigDecimal.ZERO, BigDecimal.ZERO,
                            null, null, "XCONOMY_MIGRATION", timestamp
                        );
                        try {
                            plugin.getDatabaseManager().getLogDAO().saveLog(log);
                        } catch (SQLException e) {
                            result.addError("Record save failed: " + e.getMessage());
                        }
                    }
                    result.incrementMigratedRecord();
                } catch (Exception e) {
                    result.addError("Record: " + e.getMessage());
                }
            }
        }
    }
}
