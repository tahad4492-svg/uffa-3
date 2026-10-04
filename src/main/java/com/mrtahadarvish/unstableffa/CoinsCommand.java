package com.mrtahadarvish.unstableffa;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /coins                       - your balance
 * /coins <player>              - someone else's balance
 * /coins give|take|set <player> <amount>   - admin
 */
public final class CoinsCommand implements TabExecutor {

    private final UnstableFFA plugin;

    public CoinsCommand(UnstableFFA plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) {
                plugin.send(p, "<gold>You have <yellow>" + plugin.data().getCoins(p.getUniqueId()) + " coins<gold>.");
            } else {
                plugin.send(sender, "<red>/coins <player>");
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("give") || sub.equals("take") || sub.equals("set")) {
            if (!sender.hasPermission("ufa.admin")) {
                plugin.send(sender, "<red>You don't have permission to do that.");
                return true;
            }
            admin(sender, sub, args);
            return true;
        }

        OfflinePlayer target = plugin.findPlayer(args[0]);
        if (target == null) {
            plugin.send(sender, "<red>Player not found.");
            return true;
        }
        plugin.send(sender, "<white>" + UnstableFFA.esc(args[0]) + "<gold> has <yellow>"
                + plugin.data().getCoins(target.getUniqueId()) + " coins<gold>.");
        return true;
    }

    private void admin(CommandSender sender, String sub, String[] args) {
        if (args.length < 3) {
            plugin.send(sender, "<red>/coins " + sub + " <player> <amount>");
            return;
        }
        OfflinePlayer target = plugin.findPlayer(args[1]);
        if (target == null) {
            plugin.send(sender, "<red>Player not found.");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException ex) {
            plugin.send(sender, "<red>Amount must be a whole number.");
            return;
        }
        if (amount < 0) {
            plugin.send(sender, "<red>Amount can't be negative.");
            return;
        }
        switch (sub) {
            case "give" -> plugin.data().addCoins(target.getUniqueId(), amount);
            case "take" -> plugin.data().setCoins(target.getUniqueId(),
                    plugin.data().getCoins(target.getUniqueId()) - amount);
            default -> plugin.data().setCoins(target.getUniqueId(), amount);
        }
        plugin.send(sender, "<green>" + UnstableFFA.esc(args[1]) + " now has <yellow>"
                + plugin.data().getCoins(target.getUniqueId()) + " coins<green>.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("ufa.admin");
        if (args.length == 1) {
            if (admin) {
                out.add("give");
                out.add("take");
                out.add("set");
            }
            Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        } else if (args.length == 2 && admin) {
            Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : out) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(option);
            }
        }
        return result;
    }
}
