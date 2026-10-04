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
import java.util.Map;
import java.util.UUID;

/**
 * The /kit menu. Click an owned kit to select it, click a locked kit to buy it
 * (bought kits are yours forever).
 */
public final class KitMenu implements InventoryHolder {

    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_COINS = 49;
    private static final int SLOT_NEXT = 53;

    private final UnstableFFA plugin;
    private final int page;
    private final int pages;
    private final Map<Integer, String> slotToKit = new HashMap<>();
    private final Inventory inventory;

    public static void open(UnstableFFA plugin, Player player, int page) {
        KitMenu menu = new KitMenu(plugin, player, page);
        player.openInventory(menu.inventory);
    }

    private KitMenu(UnstableFFA plugin, Player player, int requestedPage) {
        this.plugin = plugin;
        List<KitManager.Kit> kits = new ArrayList<>(plugin.kits().all());
        this.pages = Math.max(1, (kits.size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.max(0, Math.min(requestedPage, pages - 1));
        this.inventory = Bukkit.createInventory(this, 54, plugin.parse("<dark_gray>Kits"));

        UUID id = player.getUniqueId();
        String selected = plugin.data().getSelectedKit(id);

        int start = page * PER_PAGE;
        for (int i = start; i < Math.min(kits.size(), start + PER_PAGE); i++) {
            KitManager.Kit kit = kits.get(i);
            int slot = i - start;
            slotToKit.put(slot, kit.id);
            inventory.setItem(slot, kitIcon(kit, id, selected));
        }

        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, filler);
        }
        if (page > 0) {
            inventory.setItem(SLOT_PREV, named(Material.ARROW, "<gray>Previous page"));
        }
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, named(Material.ARROW, "<gray>Next page"));
        }
        ItemStack coins = named(Material.GOLD_INGOT, "<gold>Your coins: <white>" + plugin.data().getCoins(id));
        ItemMeta coinMeta = coins.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.parse("<!italic><gray>Earn <yellow>" + plugin.getConfig().getLong("coins.per-kill", 2)
                + " coins<gray> for every kill."));
        coinMeta.lore(lore);
        coins.setItemMeta(coinMeta);
        inventory.setItem(SLOT_COINS, coins);
    }

    private ItemStack kitIcon(KitManager.Kit kit, UUID playerId, String selected) {
        ItemStack item = kit.icon != null ? kit.icon.clone() : new ItemStack(Material.CHEST);
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.parse("<!italic><white>" + UnstableFFA.esc(kit.display())));

        boolean owned = kit.price <= 0 || plugin.data().ownsKit(playerId, kit.id);
        boolean isSelected = kit.id.equals(selected) && owned;

        List<Component> lore = new ArrayList<>();
        if (isSelected) {
            lore.add(plugin.parse("<!italic><green>Selected"));
        } else if (owned) {
            lore.add(plugin.parse("<!italic><green>Owned <gray>- click to select"));
        } else {
            lore.add(plugin.parse("<!italic><gold>Price: <white>" + kit.price + " coins"));
            lore.add(plugin.parse("<!italic><gray>Click to buy (yours forever)"));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (isSelected) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack named(Material material, String miniMessageName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.parse("<!italic>" + miniMessageName));
        item.setItemMeta(meta);
        return item;
    }

    /** Called by the listener when a slot in the top inventory is clicked. */
    public void handleClick(Player player, int slot) {
        if (slot == SLOT_PREV && page > 0) {
            open(plugin, player, page - 1);
            return;
        }
        if (slot == SLOT_NEXT && page < pages - 1) {
            open(plugin, player, page + 1);
            return;
        }
        String kitId = slotToKit.get(slot);
        if (kitId == null) {
            return;
        }
        KitManager.Kit kit = plugin.kits().get(kitId);
        if (kit == null) {
            return;
        }

        UUID id = player.getUniqueId();
        boolean owned = kit.price <= 0 || plugin.data().ownsKit(id, kit.id);

        if (!owned) {
            if (!plugin.data().takeCoins(id, kit.price)) {
                long missing = kit.price - plugin.data().getCoins(id);
                plugin.send(player, "<red>Not enough coins - you need <yellow>" + missing + "<red> more for <white>"
                        + UnstableFFA.esc(kit.display()) + "<red>.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            plugin.data().grantKit(id, kit.id);
            plugin.send(player, "<green>You bought the kit <white>" + UnstableFFA.esc(kit.display())
                    + "<green> for <yellow>" + kit.price + " coins<green>. It's yours forever!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        }

        plugin.data().setSelectedKit(id, kit.id);
        plugin.send(player, "<gray>Selected kit: <white>" + UnstableFFA.esc(kit.display())
                + "<gray>. Jump into a portal to fight with it.");
        open(plugin, player, page);   // refresh
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
