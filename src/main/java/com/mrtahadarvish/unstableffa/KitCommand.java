package com.mrtahadarvish.unstableffa;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /kit              -> opens the kit menu (everyone)
 * /kit create ...   -> admin tools to add kits, set prices, icons, etc.
 */
public final class KitCommand implements TabExecutor {

    private static final List<String> ADMIN_SUBS = List.of(
            "menu", "create", "update", "delete", "rename", "setprice", "seticon", "list", "give", "revoke");

    private final UnstableFFA plugin;

    public KitCommand(UnstableFFA plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (sender instanceof Player p) {
                KitMenu.open(plugin, p, 0);
            } else {
                plugin.send(sender, "<red>Only players can open the kit menu.");
            }
            return true;
        }

        if (!sender.hasPermission("ufa.admin")) {
            plugin.send(sender, "<gray>Use <white>/kit<gray> to open the kit menu.");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, args);
            case "update" -> update(sender, args);
            case "delete" -> delete(sender, args);
            case "setprice" -> setPrice(sender, args);
            case "rename" -> rename(sender, args);
            case "seticon" -> setIcon(sender, args);
            case "list" -> list(sender);
            case "give" -> give(sender, args, true);
            case "revoke" -> give(sender, args, false);
            default -> usage(sender);
        }
        return true;
    }

    private void usage(CommandSender s) {
        plugin.send(s, "<gold>Kit admin commands:");
        s.sendMessage(plugin.parse("<gray>/kit create <name> [price] [head-player] <dark_gray>- save your inventory as a kit"));
        s.sendMessage(plugin.parse("<gray>/kit update <name> <dark_gray>- overwrite a kit with your inventory"));
        s.sendMessage(plugin.parse("<gray>/kit setprice <name> <price>"));
        s.sendMessage(plugin.parse("<gray>/kit rename <name> <Display_Name> <dark_gray>- capital letters allowed, _ = space"));
        s.sendMessage(plugin.parse("<gray>/kit seticon <name> <dark_gray>- use the item in your hand as the icon"));
        s.sendMessage(plugin.parse("<gray>/kit seticon <name> head <player> <dark_gray>- use a player's head (their skin)"));
        s.sendMessage(plugin.parse("<gray>/kit delete <name>"));
        s.sendMessage(plugin.parse("<gray>/kit list"));
        s.sendMessage(plugin.parse("<gray>/kit give <player> <name> <dark_gray>- unlock a kit for a player"));
        s.sendMessage(plugin.parse("<gray>/kit revoke <player> <name>"));
    }

    private void create(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            plugin.send(s, "<red>Run this as a player - it copies your inventory.");
            return;
        }
        if (a.length < 2) {
            plugin.send(s, "<red>/kit create <name> [price]");
            return;
        }
        String id = a[1].toLowerCase(Locale.ROOT);
        if (!KitManager.ID_PATTERN.matcher(id).matches()) {
            plugin.send(s, "<red>Kit names: 1-24 characters, letters, numbers, - and _ only.");
            return;
        }
        if (plugin.kits().get(id) != null) {
            plugin.send(s, "<red>That kit already exists. Use <white>/kit update " + UnstableFFA.esc(id)
                    + "<red> to overwrite it.");
            return;
        }
        long price = 0L;
        if (a.length >= 3) {
            Long parsed = parsePrice(a[2]);
            if (parsed == null) {
                plugin.send(s, "<red>Price must be a whole number (0 = free).");
                return;
            }
            price = parsed;
        }
        KitManager.Kit kit = plugin.kits().create(a[1], price, p);
        plugin.send(s, "<green>Created kit <white>" + UnstableFFA.esc(kit.display()) + "<green> for <yellow>" + kit.price
                + " coins<green> from your inventory.");
        if (a.length >= 4) {
            applyHead(s, kit, a[3]);
        } else {
            plugin.send(s, "<gray>Icon = item in your hand. Change it with <white>/kit seticon " + UnstableFFA.esc(kit.id)
                    + " [head <player>]");
        }
    }

    private void update(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            plugin.send(s, "<red>Run this as a player.");
            return;
        }
        KitManager.Kit kit = a.length >= 2 ? plugin.kits().get(a[1]) : null;
        if (kit == null) {
            plugin.send(s, "<red>/kit update <existing kit>");
            return;
        }
        plugin.kits().update(kit, p);
        plugin.send(s, "<green>Kit <white>" + UnstableFFA.esc(kit.id) + "<green> now matches your inventory.");
    }

    private void delete(CommandSender s, String[] a) {
        if (a.length < 2 || !plugin.kits().delete(a[1])) {
            plugin.send(s, "<red>/kit delete <existing kit>");
            return;
        }
        plugin.send(s, "<green>Deleted kit <white>" + UnstableFFA.esc(a[1].toLowerCase(Locale.ROOT)) + "<green>.");
    }

    private void setPrice(CommandSender s, String[] a) {
        KitManager.Kit kit = a.length >= 2 ? plugin.kits().get(a[1]) : null;
        Long price = a.length >= 3 ? parsePrice(a[2]) : null;
        if (kit == null || price == null) {
            plugin.send(s, "<red>/kit setprice <existing kit> <price>");
            return;
        }
        plugin.kits().setPrice(kit, price);
        plugin.send(s, "<green>Kit <white>" + UnstableFFA.esc(kit.id) + "<green> now costs <yellow>" + kit.price
                + " coins<green>. Players who already own it keep it.");
    }

    private void setIcon(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            plugin.send(s, "<red>Run this as a player.");
            return;
        }
        KitManager.Kit kit = a.length >= 2 ? plugin.kits().get(a[1]) : null;
        if (kit == null) {
            plugin.send(s, "<red>/kit seticon <existing kit> [head <player name>]");
            return;
        }
        if (a.length >= 4 && a[2].equalsIgnoreCase("head")) {
            applyHead(s, kit, a[3]);
            return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            plugin.send(s, "<red>Hold an item, or use <white>/kit seticon <kit> head <player name>");
            return;
        }
        plugin.kits().setIcon(kit, held);
        plugin.send(s, "<green>Icon of <white>" + UnstableFFA.esc(kit.id) + "<green> updated.");
    }

    /** Sets the kit icon to the head of the named player (skin is fetched from Mojang). */
    private void applyHead(CommandSender s, KitManager.Kit kit, String name) {
        if (!HeadUtil.validName(name)) {
            plugin.send(s, "<red>That is not a valid Minecraft username.");
            return;
        }
        plugin.send(s, "<gray>Fetching the skin of <white>" + UnstableFFA.esc(name) + "<gray>...");
        HeadUtil.fetch(plugin, name, head -> {
            plugin.kits().setIcon(kit, head);
            plugin.send(s, "<green>Icon of <white>" + UnstableFFA.esc(kit.display()) + "<green> is now the head of <white>"
                    + UnstableFFA.esc(name) + "<green>.");
        }, reason -> plugin.send(s, "<red>Could not get the skin of <white>" + UnstableFFA.esc(name) + "<red>: <gray>" + UnstableFFA.esc(reason)));
    }

    private void rename(CommandSender s, String[] a) {
        KitManager.Kit kit = a.length >= 2 ? plugin.kits().get(a[1]) : null;
        if (kit == null || a.length < 3 || !a[2].matches("[A-Za-z0-9_\\-]{1,24}")) {
            plugin.send(s, "<red>/kit rename <existing kit> <New_Name> <gray>(letters, numbers, _ and -; _ shows as a space)");
            return;
        }
        plugin.kits().setName(kit, a[2]);
        plugin.send(s, "<green>Kit <white>" + UnstableFFA.esc(kit.id) + "<green> is now shown as <white>"
                + UnstableFFA.esc(kit.display()) + "<green>.");
    }

    private void list(CommandSender s) {
        plugin.send(s, "<gold>Kits:");
        for (KitManager.Kit kit : plugin.kits().all()) {
            s.sendMessage(plugin.parse("<gray>- <white>" + UnstableFFA.esc(kit.display()) + " <dark_gray>[" + UnstableFFA.esc(kit.id) + "] (<yellow>"
                    + kit.price + " coins<dark_gray>)"));
        }
    }

    private void give(CommandSender s, String[] a, boolean grant) {
        if (a.length < 3) {
            plugin.send(s, "<red>/kit " + (grant ? "give" : "revoke") + " <player> <kit>");
            return;
        }
        OfflinePlayer target = plugin.findPlayer(a[1]);
        KitManager.Kit kit = plugin.kits().get(a[2]);
        if (target == null || kit == null) {
            plugin.send(s, "<red>Unknown player or kit.");
            return;
        }
        if (grant) {
            plugin.data().grantKit(target.getUniqueId(), kit.id);
        } else {
            plugin.data().revokeKit(target.getUniqueId(), kit.id);
        }
        plugin.send(s, "<green>" + (grant ? "Unlocked" : "Removed") + " kit <white>" + UnstableFFA.esc(kit.id)
                + "<green> " + (grant ? "for " : "from ") + "<white>" + UnstableFFA.esc(a[1]) + "<green>.");
    }

    private static Long parsePrice(String text) {
        try {
            long value = Long.parseLong(text);
            return value < 0 ? null : value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("ufa.admin")) {
            if (args.length == 1) {
                out.add("menu");
            }
            return filter(out, args);
        }
        if (args.length == 1) {
            out.addAll(ADMIN_SUBS);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "update", "delete", "rename", "setprice", "seticon" -> out.addAll(plugin.kits().ids());
                case "give", "revoke" -> Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
                default -> { }
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("seticon")) {
                out.add("head");
            }
            if (sub.equals("give") || sub.equals("revoke")) {
                out.addAll(plugin.kits().ids());
            }
        }
        return filter(out, args);
    }

    private static List<String> filter(List<String> options, String[] args) {
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(option);
            }
        }
        return result;
    }
}
