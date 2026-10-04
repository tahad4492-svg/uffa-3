package com.mrtahadarvish.unstableffa;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /playerhead &lt;name&gt;           gives the head with that player's real skin
 * /playerhead texture &lt;value&gt;  gives a head from a raw skin texture value
 * Hold the head and use /kit seticon or /shop seticon to make it an icon.
 */
public final class PlayerHeadCommand implements TabExecutor {

    private final UnstableFFA plugin;

    public PlayerHeadCommand(UnstableFFA plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.send(sender, "<red>Run this as a player.");
            return true;
        }
        if (args.length == 0) {
            plugin.send(p, "<red>/playerhead <minecraft name> <gray>- e.g. /playerhead Wemmbu");
            return true;
        }

        if (args[0].equalsIgnoreCase("texture")) {
            if (args.length < 2 || args[1].length() < 40) {
                plugin.send(p, "<red>/playerhead texture <texture value> <gray>(the long base64 text from minecraft-heads.com)");
                return true;
            }
            give(p, HeadUtil.fromTexture(args[1]));
            plugin.send(p, "<green>Gave you a head from that texture.");
            return true;
        }

        String name = args[0];
        if (!HeadUtil.validName(name)) {
            plugin.send(p, "<red>That is not a valid Minecraft username (letters, numbers or _, max 16).");
            return true;
        }
        plugin.send(p, "<gray>Fetching the skin of <white>" + UnstableFFA.esc(name) + "<gray>...");
        HeadUtil.fetch(plugin, name, head -> {
            label(head, name);
            give(p, head);
            plugin.send(p, "<green>Gave you the head of <white>" + UnstableFFA.esc(name)
                    + "<green>. Hold it and use <white>/kit seticon <kit><green> or <white>/shop seticon <section><green> to use it as an icon.");
        }, reason -> {
            // hand out a name-only head as a last resort and say exactly what went wrong
            ItemStack fallback = HeadUtil.byOwnerName(name);
            label(fallback, name);
            give(p, fallback);
            plugin.send(p, "<red>Could not get the skin of <white>" + UnstableFFA.esc(name) + "<red>: <gray>" + UnstableFFA.esc(reason));
            plugin.send(p, "<yellow>You got a name-only head that may show as Steve. You can also use <white>/playerhead texture <value>"
                    + "<yellow> with a texture value from minecraft-heads.com.");
        });
        return true;
    }

    private void label(ItemStack head, String name) {
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.displayName(plugin.parse("<!italic><white>" + UnstableFFA.esc(name) + "'s Head"));
        head.setItemMeta(meta);
    }

    private void give(Player p, ItemStack head) {
        for (ItemStack leftover : p.getInventory().addItem(head).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), leftover);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("texture");
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
