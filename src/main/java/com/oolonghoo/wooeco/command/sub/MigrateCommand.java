package com.oolonghoo.wooeco.command.sub;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.command.AbstractSubCommandHandler;
import com.oolonghoo.wooeco.migration.MigrationResult;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MigrateCommand extends AbstractSubCommandHandler {

    public MigrateCommand(WooEco plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "migrate";
    }

    @Override
    public String getDescription() {
        return "数据迁移";
    }

    @Override
    public String getPermission() {
        return "wooeco.admin.migrate";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        boolean dryRun = Arrays.asList(args).contains("--dry-run");

        switch (subCommand) {
            case "vault":
                messages.send(sender, "migration.start", Map.of("source", "Vault"));
                plugin.getMigrationManager().migrateFromVault(key -> handleCallback(sender, key), dryRun);
                break;

            case "xconomy":
                messages.send(sender, "migration.start", Map.of("source", "XConomy"));
                plugin.getMigrationManager().migrateFromXConomy(key -> handleCallback(sender, key), dryRun);
                break;

            case "status":
                MigrationResult last = plugin.getMigrationManager().getLastResult();
                if (last == null) {
                    messages.send(sender, "migration.status-none");
                } else {
                    messages.send(sender, "migration.status-result", Map.of(
                        "source", last.getSourceName() != null ? last.getSourceName() : "Unknown",
                        "players", String.valueOf(last.getMigratedPlayers()),
                        "accounts", String.valueOf(last.getMigratedNonPlayerAccounts()),
                        "time", String.valueOf(last.getDurationMs()),
                        "success", String.valueOf(last.isSuccess())
                    ));
                }
                break;

            default:
                sendUsage(sender);
                break;
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        messages.send(sender, "migration.usage");
    }

    private void handleCallback(CommandSender sender, String key) {
        if (key.startsWith("migration.progress:")) {
            String[] parts = key.split(":");
            if (parts.length == 4) {
                messages.send(sender, "migration.progress", Map.of(
                    "current", parts[1], "total", parts[2], "percent", parts[3]
                ));
            }
            return;
        }
        MigrationResult r = plugin.getMigrationManager().getLastResult();
        if (key.equals("migration.completed") && r != null) {
            messages.send(sender, "migration.completed", Map.of(
                "players", String.valueOf(r.getMigratedPlayers()),
                "accounts", String.valueOf(r.getMigratedNonPlayerAccounts()),
                "time", String.valueOf(r.getDurationMs())
            ));
        } else if (key.equals("migration.failed") && r != null) {
            String firstError = r.getErrors().isEmpty() ? "" : r.getErrors().get(0);
            if ("no-source".equals(firstError)) {
                messages.send(sender, "migration.no-source");
            } else if ("self-migrate".equals(firstError)) {
                messages.send(sender, "migration.self-migrate");
            } else {
                messages.send(sender, "migration.failed", Map.of("error", String.join(", ", r.getErrors())));
            }
        } else if (key.equals("already-running")) {
            messages.send(sender, "migration.already-running");
        } else {
            messages.send(sender, key);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("vault", "xconomy", "status");
        }
        if (args.length == 2) {
            return Arrays.asList("--dry-run");
        }
        return List.of();
    }
}
