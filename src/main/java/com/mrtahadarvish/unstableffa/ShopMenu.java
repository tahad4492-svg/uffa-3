package com.mrtahadarvish.unstableffa;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /shop. The main menu (barrel sized, 27 slots) lists the sections; clicking a
 * section shows its items. Items are paid with coins. In an arena the shop needs
 * at least one empty inventory slot and the item goes straight into the inventory;
 * in the lobby the purchase waits and is added when the player enters an arena
 * (a kit replaces the inventory).
 */
public final class ShopMenu implements InventoryHolder {

    private static final int SIZE = 27;
    private static final int SLOT_BACK = 18;
    private static final int SLOT_COINS = 22;

    private final UnstableFFA plugin;
    private final ShopManager.Section section;   // null = main menu
    private final Map<Integer, String> slotToSection = new HashMap<>();
    private final Inventory inventory;

    /** Where the shop may be used. Returns null if allowed, otherwise the message to show. */
    public static String blockedReason(UnstableFFA plugin, Player player) {
        if (plugin.lobby().bypass(player) || plugin.lobby().isLobbyWorld(player.getWorld())) {
            return null;
        }
        if (plugin.arenas().isArenaWorld(player.getWorld())) {
            return hasFreeSlot(player) ? null : "<red>You need at least 1 empty inventory slot to use the shop.";
        }
        return "<red>You can only shop in the lobby or in an arena.";
    }

    public static boolean hasFreeSlot(Player player) {
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    public static void open(UnstableFFA plugin, Player player, String sectionId) {
        ShopManager.Section section = sectionId == null ? null : plugin.shop().get(sectionId);
        ShopMenu menu = new ShopMenu(plugin, player, section);
        player.openInventory(menu.inventory);
    }

    private ShopMenu(UnstableFFA plugin, Player player, ShopManager.Section section) {
        this.plugin = plugin;
        this.section = section;
        Component title = section == null
                ? plugin.parse(plugin.getConfig().getString("shop.title", "<dark_gray>Shop"))
                : plugin.parse("<dark_gray>" + UnstableFFA.esc(section.display()));
        this.inventory = Bukkit.createInventory(this, SIZE, title);

        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        if (section == null) {
            buildMain(filler);
        } else {
            buildSection(player, filler);
        }
    }

    // ---- building ---------------------------------------------------------------

    private void buildMain(ItemStack filler) {
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
        List<ShopManager.Section> list = plugin.shop().all();
        int n = list.size();
        int first = n <= 9 ? 9 + (9 - n) / 2 : 0;   // up to 9 sections sit centred in the middle row
        for (int i = 0; i < n && i < SIZE; i++) {
            ShopManager.Section s = list.get(i);
            int slot = first + i;
            ItemStack icon = s.icon.clone();
            icon.setAmount(1);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(plugin.parse("<!italic><white>" + UnstableFFA.esc(s.display())));
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.parse("<!italic><gray>" + s.items.size() + " item" + (s.items.size() == 1 ? "" : "s")));
            lore.add(plugin.parse("<!italic><gray>Click to open"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            icon.setItemMeta(meta);
            inventory.setItem(slot, icon);
            slotToSection.put(slot, s.id);
        }
        if (n == 0) {
            inventory.setItem(13, named(Material.BARRIER, "<gray>The shop is empty"));
        }
    }

    private void buildSection(Player player, ItemStack filler) {
        for (int i = 18; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
        for (int i = 0; i < section.items.size() && i < ShopManager.MAX_ITEMS; i++) {
            ShopManager.Entry entry = section.items.get(i);
            ItemStack shown = entry.item.clone();
            ItemMeta meta = shown.getItemMeta();
            List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            lore.add(plugin.parse("<!italic><gold>Price: <white>" + entry.price + " coins"));
            lore.add(plugin.parse("<!italic><gray>Click to buy"));
            meta.lore(lore);
            shown.setItemMeta(meta);
            inventory.setItem(i, shown);
        }
        inventory.setItem(SLOT_BACK, named(Material.ARROW, "<gray>Back"));

        UUID id = player.getUniqueId();
        ItemStack coins = named(Material.GOLD_INGOT, "<gold>Your coins: <white>" + plugin.data().getCoins(id));
        ItemMeta coinMeta = coins.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (plugin.arenas().isArenaWorld(player.getWorld())) {
            lore.add(plugin.parse("<!italic><gray>Bought items go straight into"));
            lore.add(plugin.parse("<!italic><gray>your inventory (needs a free slot)."));
        } else {
            lore.add(plugin.parse("<!italic><gray>Bought items are added to your inventory"));
            lore.add(plugin.parse("<!italic><gray>when you enter an arena."));
        }
        int queued = plugin.data().getStash(id).size();
        if (queued > 0) {
            lore.add(plugin.parse("<!italic><green>" + queued + " item" + (queued == 1 ? "" : "s") + " waiting for you"));
        }
        coinMeta.lore(lore);
        coins.setItemMeta(coinMeta);
        inventory.setItem(SLOT_COINS, coins);
    }

    private ItemStack named(Material material, String miniMessageName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.parse("<!italic>" + miniMessageName));
        item.setItemMeta(meta);
        return item;
    }

    // ---- clicking ------------------------------------------------------------------

    public void handleClick(Player player, int slot) {
        if (section == null) {
            String id = slotToSection.get(slot);
            if (id != null) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
                open(plugin, player, id);
            }
            return;
        }
        if (slot == SLOT_BACK) {
            open(plugin, player, null);
            return;
        }
        if (slot >= 0 && slot < ShopManager.MAX_ITEMS) {
            buy(player, slot);
        }
    }

    private void buy(Player player, int index) {
        ShopManager.Section live = plugin.shop().get(section.id);   // admin may have edited it meanwhile
        if (live == null || index >= live.items.size()) {
            open(plugin, player, live == null ? null : live.id);
            return;
        }
        String blocked = blockedReason(plugin, player);
        if (blocked != null) {
            plugin.send(player, blocked);
            player.closeInventory();
            return;
        }
        ShopManager.Entry entry = live.items.get(index);
        UUID id = player.getUniqueId();
        boolean direct = plugin.arenas().isArenaWorld(player.getWorld()) || plugin.lobby().bypass(player);
        if (direct && !hasFreeSlot(player)) {
            plugin.send(player, "<red>You need at least 1 empty inventory slot to buy this.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (!plugin.data().takeCoins(id, entry.price)) {
            long missing = entry.price - plugin.data().getCoins(id);
            plugin.send(player, "<red>Not enough coins - you need <yellow>" + missing + "<red> more.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        String what = entry.item.getAmount() + "x " + UnstableFFA.esc(prettyName(entry.item));
        if (direct) {
            for (ItemStack leftover : player.getInventory().addItem(entry.item.clone()).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            plugin.send(player, "<green>Bought <white>" + what + "<green> for <yellow>" + entry.price
                    + " coins<green>. It's in your inventory.");
        } else {
            plugin.data().addStash(id, entry.item.clone());
            plugin.send(player, "<green>Bought <white>" + what + "<green> for <yellow>" + entry.price
                    + " coins<green>. It will be in your inventory when you enter an arena.");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        open(plugin, player, live.id);   // refresh coins
    }

    /** Custom name if the item has one, otherwise the material name in normal words. */
    public static String prettyName(ItemStack item) {
        String[] words = item.getType().name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
