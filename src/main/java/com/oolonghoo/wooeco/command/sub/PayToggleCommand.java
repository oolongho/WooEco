package com.oolonghoo.wooeco.command.sub;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.command.AbstractSubCommandHandler;
import com.oolonghoo.wooeco.manager.PlayerDataManager;
import com.oolonghoo.wooeco.model.PlayerAccount;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PayToggleCommand extends AbstractSubCommandHandler {
    private final PlayerDataManager playerDataManager;

    public PayToggleCommand(WooEco plugin) {
        super(plugin);
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    @Override
    public String getName() {
        return "paytoggle";
    }

    @Override
    public String getDescription() {
        return "切换收款功能";
    }

    @Override
    public String getPermission() {
        return "wooeco.paytoggle";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return showSelfStatus(sender);
        }

        String first = args[0].toLowerCase();

        if (sender.hasPermission("wooeco.paytoggle.other") && args.length >= 2) {
            return handleOtherToggle(sender, args);
        }

        if ("on".equals(first) || "off".equals(first)) {
            return setSelfToggle(sender, first);
        }

        if (sender.hasPermission("wooeco.paytoggle.other")) {
            return handleOtherToggle(sender, args);
        }

        return setSelfToggle(sender, first);
    }

    private boolean showSelfStatus(CommandSender sender) {
        if (!requirePlayer(sender)) return true;
        Player player = (Player) sender;
        boolean current = plugin.getPayToggleManager().isPayEnabled(player.getUniqueId());
        messages.send(sender, current ? "paytoggle.status-enabled" : "paytoggle.status-disabled");
        return true;
    }

    private boolean setSelfToggle(CommandSender sender, String action) {
        if (!requirePlayer(sender)) return true;
        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();
        boolean current = plugin.getPayToggleManager().isPayEnabled(uuid);

        if ("on".equals(action)) {
            if (current) {
                messages.send(sender, "paytoggle.already-enabled");
            } else {
                plugin.getPayToggleManager().setPayEnabled(uuid, true);
                messages.send(sender, "paytoggle.enabled");
            }
        } else if ("off".equals(action)) {
            if (!current) {
                messages.send(sender, "paytoggle.already-disabled");
            } else {
                plugin.getPayToggleManager().setPayEnabled(uuid, false);
                messages.send(sender, "paytoggle.disabled");
            }
        } else {
            plugin.getPayToggleManager().toggle(uuid);
            messages.send(sender, current ? "paytoggle.disabled" : "paytoggle.enabled");
        }
        return true;
    }

    private boolean handleOtherToggle(CommandSender sender, String[] args) {
        String targetName = args[0];
        PlayerAccount account = playerDataManager.getAccount(targetName);
        if (account == null) {
            messages.send(sender, "player-not-found", Map.of("player", targetName));
            return true;
        }
        UUID targetUuid = account.getUuid();
        boolean current = plugin.getPayToggleManager().isPayEnabled(targetUuid);

        if (args.length >= 2) {
            String action = args[1].toLowerCase();
            if ("on".equals(action)) {
                plugin.getPayToggleManager().setPayEnabled(targetUuid, true);
                messages.send(sender, "paytoggle.target-enabled", Map.of("player", targetName));
            } else if ("off".equals(action)) {
                plugin.getPayToggleManager().setPayEnabled(targetUuid, false);
                messages.send(sender, "paytoggle.target-disabled", Map.of("player", targetName));
            } else {
                plugin.getPayToggleManager().toggle(targetUuid);
                String key = current ? "paytoggle.target-disabled" : "paytoggle.target-enabled";
                messages.send(sender, key, Map.of("player", targetName));
            }
        } else {
            plugin.getPayToggleManager().toggle(targetUuid);
            String key = current ? "paytoggle.target-disabled" : "paytoggle.target-enabled";
            messages.send(sender, key, Map.of("player", targetName));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(Arrays.asList("on", "off"));
            if (sender.hasPermission("wooeco.paytoggle.other")) {
                completions.addAll(getOnlinePlayerNames());
            }
        } else if (args.length == 2 && sender.hasPermission("wooeco.paytoggle.other")) {
            completions.addAll(Arrays.asList("on", "off"));
        }
        return completions;
    }
}
