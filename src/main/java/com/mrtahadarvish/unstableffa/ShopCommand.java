package com.mrtahadarvish.unstableffa;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /shop            -> opens the shop (everyone; in arenas needs a free inventory slot)
 * /shop addsection ... -> admin tools to build sections and items
 */
public final class ShopCommand implements TabExecutor {

    private static final List<String> ADMIN_SUBS = List.of(
            "addsection", "removesection", "settitle", "seticon", "additem", "removeitem", "setprice", "list", "help");
    private static final String TITLE_PATTERN = "[A-Za-z0-9_\\-]{1,24}";

    private final UnstableFFA plugin;

    public ShopCommand(UnstableFFA plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                plugin.send(sender, "<red>Only players can open the shop.");
                return true;
            }
            String blocked = ShopMenu.blockedReason(plugin, p);
            if (blocked != null) {
                plugin.send(p, blocked);
                return true;
            }
            ShopMenu.open(plugin, p, null);
            return true;
        }
        if (!sender.hasPermission("ufa.admin")) {
            plugin.send(sender, "<gray>Use <white>/shop<gray> to open the shop.");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "addsection" -> addSection(sender, args);
            case "removesection" -> removeSection(sender, args);
            case "settitle" -> setTitle(sender, args);
            case "seticon" -> setIcon(sender, args);
            case "additem" -> addItem(sender, args);
            case "removeitem" -> removeItem(sender, args);
            case "setprice" -> setPrice(sender, args);
            case "list" -> list(sender);
            default -> usage(sender);
        }
        return true;
    }

    private void usage(CommandSender s) {
        plugin.send(s, "<gold>Shop admin commands:");
        String[] lines = {
                "/shop addsection <id> [Title_Words] <dark_gray>- new section, icon = item in your hand",
                "/shop settitle <section> <Title_Words> <dark_gray>- _ shows as a space",
                "/shop seticon <section> [head <player>] <dark_gray>- hand item or a player's head",
                "/shop removesection <section>",
                "/shop additem <section> <price> <dark_gray>- sell the stack in your hand",
                "/shop removeitem <section> <number>",
                "/shop setprice <section> <number> <price>",
                "/shop list <dark_gray>- all sections and items with their numbers"
        };
        for (String line : lines) {
            s.sendMessage(plugin.parse("<gray>" + line));
        }
    }

    private ShopManager.Section needSection(CommandSender s, String[] a, int index) {
        ShopManager.Section section = a.length > index ? plugin.shop().get(a[index]) : null;
        if (section == null) {
            plugin.send(s, "<red>Unknown section. Use <white>/shop list<red>.");
        }
        return section;
    }

    private void addSection(CommandSender s, String[] a) {
        if (a.length < 2 || !a[1].matches(TITLE_PATTERN)) {
            plugin.send(s, "<red>/shop addsection <id> [Title_Words] <gray>(letters, numbers, _ and -)");
            return;
        }
        String title = a.length >= 3 ? a[2] : a[1];
        if (!title.matches(TITLE_PATTERN)) {
            plugin.send(s, "<red>Titles can use letters, numbers, _ and - (use _ for spaces).");
            return;
        }
        ItemStack icon = s instanceof Player p ? p.getInventory().getItemInMainHand() : null;
        ShopManager.Section section = plugin.shop().createSection(a[1], icon);
        if (section == null) {
            plugin.send(s, "<red>That section already exists, or the shop is full (" + ShopManager.MAX_SECTIONS + " sections).");
            return;
        }
        plugin.shop().setTitle(section, title);
        plugin.send(s, "<green>Section <white>" + UnstableFFA.esc(section.display()) + "<green> created. Add items with <white>/shop additem "
                + UnstableFFA.esc(section.id) + " <price>");
    }

    private void removeSection(CommandSender s, String[] a) {
        if (a.length < 2 || !plugin.shop().deleteSection(a[1])) {
            plugin.send(s, "<red>/shop removesection <existing section>");
            return;
        }
        plugin.send(s, "<green>Section removed.");
    }

    private void setTitle(CommandSender s, String[] a) {
        ShopManager.Section section = needSection(s, a, 1);
        if (section == null) {
            return;
        }
        if (a.length < 3 || !a[2].matches(TITLE_PATTERN)) {
            plugin.send(s, "<red>/shop settitle <section> <Title_Words> <gray>(letters, numbers, _ and -)");
            return;
        }
        plugin.shop().setTitle(section, a[2]);
        plugin.send(s, "<green>Title is now <white>" + UnstableFFA.esc(section.display()) + "<green>.");
    }

    private void setIcon(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            plugin.send(s, "<red>Run this as a player.");
            return;
        }
        ShopManager.Section section = needSection(s, a, 1);
        if (section == null) {
            return;
        }
        if (a.length >= 4 && a[2].equalsIgnoreCase("head")) {
            String name = a[3];
            if (!HeadUtil.validName(name)) {
                plugin.send(s, "<red>That is not a valid Minecraft username.");
                return;
            }
            plugin.send(s, "<gray>Fetching the skin of <white>" + UnstableFFA.esc(name) + "<gray>...");
            HeadUtil.fetch(plugin, name, head -> {
                plugin.shop().setIcon(section, head);
                plugin.send(s, "<green>Icon of <white>" + UnstableFFA.esc(section.display()) + "<green> is now the head of <white>"
                        + UnstableFFA.esc(name) + "<green>.");
            }, reason -> plugin.send(s, "<red>Could not get the skin of <white>" + UnstableFFA.esc(name) + "<red>: <gray>" + UnstableFFA.esc(reason)));
            return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            plugin.send(s, "<red>Hold an item, or use <white>/shop seticon <section> head <player name>");
            return;
        }
        plugin.shop().setIcon(section, held);
        plugin.send(s, "<green>Icon updated.");
    }

    private void addItem(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            plugin.send(s, "<red>Run this as a player - it sells the item in your hand.");
            return;
        }
        ShopManager.Section section = needSection(s, a, 1);
        if (section == null) {
            return;
        }
        Long price = a.length >= 3 ? parsePrice(a[2]) : null;
        ItemStack held = p.getInventory().getItemInMainHand();
        if (price == null || held.getType().isAir()) {
            plugin.send(s, "<red>Hold the item (with the amount to sell) and run <white>/shop additem <section> <price>");
            return;
        }
        if (!plugin.shop().addItem(section, held, price)) {
            plugin.send(s, "<red>That section is full (" + ShopManager.MAX_ITEMS + " items max).");
            return;
        }
        plugin.send(s, "<green>Added <white>" + held.getAmount() + "x " + UnstableFFA.esc(ShopMenu.prettyName(held))
                + "<green> to <white>" + UnstableFFA.esc(section.display()) + "<green> for <yellow>" + price + " coins<green>.");
    }

    private void removeItem(CommandSender s, String[] a) {
        ShopManager.Section section = needSection(s, a, 1);
        if (section == null) {
            return;
        }
        Integer number = a.length >= 3 ? parseInt(a[2]) : null;
        if (number == null || !plugin.shop().removeItem(section, number)) {
            plugin.send(s, "<red>/shop removeitem <section> <number> <gray>(numbers are in /shop list)");
            return;
        }
        plugin.send(s, "<green>Item removed.");
    }

    private void setPrice(CommandSender s, String[] a) {
        ShopManager.Section section = needSection(s, a, 1);
        if (section == null) {
            return;
        }
        Integer number = a.length >= 3 ? parseInt(a[2]) : null;
        Long price = a.length >= 4 ? parsePrice(a[3]) : null;
        if (number == null || price == null || !plugin.shop().setPrice(section, number, price)) {
            plugin.send(s, "<red>/shop setprice <section> <number> <price>");
            return;
        }
        plugin.send(s, "<green>Price set to <yellow>" + price + " coins<green>.");
    }

    private void list(CommandSender s) {
        plugin.send(s, "<gold>Shop sections:");
        for (ShopManager.Section section : plugin.shop().all()) {
            s.sendMessage(plugin.parse("<gray>- <white>" + UnstableFFA.esc(section.display()) + " <dark_gray>[" + UnstableFFA.esc(section.id) + "]"));
            int n = 1;
            for (ShopManager.Entry entry : section.items) {
                s.sendMessage(plugin.parse("<dark_gray>   " + n + ". <gray>" + entry.item.getAmount() + "x "
                        + UnstableFFA.esc(ShopMenu.prettyName(entry.item)) + " <dark_gray>- <yellow>" + entry.price + " coins"));
                n++;
            }
        }
    }

    private static Long parsePrice(String text) {
        try {
            long value = Long.parseLong(text);
            return value < 0 ? null : value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("ufa.admin")) {
            return out;
        }
        if (args.length == 1) {
            out.addAll(ADMIN_SUBS);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "settitle", "seticon", "removesection", "additem", "removeitem", "setprice" -> out.addAll(plugin.shop().ids());
                default -> { }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("seticon")) {
            out.add("head");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("seticon")) {
            Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : out) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(option);
            }
        }
        return result;
    }
}
