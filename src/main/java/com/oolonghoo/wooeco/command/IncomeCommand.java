package com.oolonghoo.wooeco.command;

import com.oolonghoo.wooeco.WooEco;
import com.oolonghoo.wooeco.command.sub.IncomeCommandHandler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class IncomeCommand implements CommandExecutor {

    private final IncomeCommandHandler handler;

    public IncomeCommand(WooEco plugin) {
        this.handler = new IncomeCommandHandler(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return handler.execute(sender, args);
    }
}
