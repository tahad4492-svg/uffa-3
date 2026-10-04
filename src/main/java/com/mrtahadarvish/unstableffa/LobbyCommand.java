package com.mrtahadarvish.unstableffa;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/** /lobby - go back to the lobby. */
public final class LobbyCommand implements TabExecutor {

    private final UnstableFFA plugin;

    public LobbyCommand(UnstableFFA plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.send(sender, "<red>Only players can do that.");
            return true;
        }
        plugin.lobby().sendToLobby(p);
        plugin.send(p, "<gray>Welcome back to the lobby.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
