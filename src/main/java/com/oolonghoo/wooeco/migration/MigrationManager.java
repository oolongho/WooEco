package com.oolonghoo.wooeco.migration;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.util.SchedulerUtils;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MigrationManager {
    private final WooEco plugin;
    private final AtomicBoolean migrating = new AtomicBoolean(false);
    private volatile MigrationResult lastResult;

    public MigrationManager(WooEco plugin) {
        this.plugin = plugin;
    }

    public boolean isMigrating() {
        return migrating.get();
    }

    public MigrationResult getLastResult() {
        return lastResult;
    }

    public void migrateFromVault(Consumer<String> progressCallback, boolean dryRun) {
        if (!migrating.compareAndSet(false, true)) {
            progressCallback.accept("already-running");
            return;
        }
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                VaultMigrator migrator = new VaultMigrator(plugin, progressCallback, dryRun);
                lastResult = migrator.migrate();
                String msgKey = lastResult.isSuccess() ? "migration.completed" : "migration.failed";
                progressCallback.accept(msgKey);
            } catch (Exception e) {
                lastResult = MigrationResult.failure("Vault", e.getMessage());
                progressCallback.accept("migration.failed");
            } finally {
                migrating.set(false);
            }
        });
    }

    public void migrateFromXConomy(Consumer<String> progressCallback, boolean dryRun) {
        if (!migrating.compareAndSet(false, true)) {
            progressCallback.accept("already-running");
            return;
        }
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                XConomyMigrator migrator = new XConomyMigrator(plugin, progressCallback, dryRun);
                lastResult = migrator.migrate();
                String msgKey = lastResult.isSuccess() ? "migration.completed" : "migration.failed";
                progressCallback.accept(msgKey);
            } catch (Exception e) {
                lastResult = MigrationResult.failure("XConomy", e.getMessage());
                progressCallback.accept("migration.failed");
            } finally {
                migrating.set(false);
            }
        });
    }
}
