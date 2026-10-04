package com.mrtahadarvish.unstableffa;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Kits are stored in kits.yml. A kit is a snapshot of an admin's inventory
 * (storage, armor, offhand) plus a price and an icon.
 */
public final class KitManager {

    public static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_\\-]{1,24}$");

    public static final class Kit {
        public final String id;        // lower-case key used in commands and data files
        public String name;            // display name exactly as the admin typed it (upper-case allowed)
        public long price;
        public ItemStack icon;
        public ItemStack[] contents;   // 36 storage slots
        public ItemStack[] armor;      // boots, leggings, chestplate, helmet
        public ItemStack offhand;

        Kit(String id, String name, long price, ItemStack icon, ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
            this.id = id;
            this.name = (name == null || name.isBlank()) ? id : name;
            this.price = price;
            this.icon = icon;
            this.contents = contents;
            this.armor = armor;
            this.offhand = offhand;
        }

        /** Name shown to players: keeps capital letters, underscores become spaces. */
        public String display() {
            return name.replace('_', ' ');
        }
    }

    private final UnstableFFA plugin;
    private final File file;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitManager(UnstableFFA plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
        load();
        if (kits.isEmpty()) {
            createDefault();
        }
    }

    // ---- lookup --------------------------------------------------------------

    public Kit get(String id) {
        return id == null ? null : kits.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Kit> all() {
        List<Kit> list = new ArrayList<>(kits.values());
        list.sort(Comparator.<Kit>comparingLong(k -> k.price).thenComparing(k -> k.id));
        return list;
    }

    public List<String> ids() {
        return new ArrayList<>(kits.keySet());
    }

    // ---- editing -------------------------------------------------------------

    /** Creates a kit from the player's current inventory. */
    public Kit create(String rawName, long price, Player p) {
        String id = rawName.toLowerCase(Locale.ROOT);
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = copy(inv.getStorageContents(), 36);
        ItemStack[] armor = copy(inv.getArmorContents(), 4);
        ItemStack off = inv.getItemInOffHand();
        off = off.getType().isAir() ? null : off.clone();

        ItemStack icon = inv.getItemInMainHand();
        if (icon.getType().isAir()) {
            icon = firstItem(contents, armor);
        }
        icon = icon == null ? new ItemStack(Material.CHEST) : icon.clone();
        icon.setAmount(1);

        Kit kit = new Kit(id, rawName, price, icon, contents, armor, off);
        kits.put(id, kit);
        save();
        return kit;
    }

    /** Re-snapshots the player's inventory into an existing kit (price and icon stay). */
    public void update(Kit kit, Player p) {
        PlayerInventory inv = p.getInventory();
        kit.contents = copy(inv.getStorageContents(), 36);
        kit.armor = copy(inv.getArmorContents(), 4);
        ItemStack off = inv.getItemInOffHand();
        kit.offhand = off.getType().isAir() ? null : off.clone();
        save();
    }

    public boolean delete(String id) {
        boolean removed = kits.remove(id.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public void setPrice(Kit kit, long price) {
        kit.price = Math.max(0L, price);
        save();
    }

    public void setName(Kit kit, String name) {
        kit.name = name;
        save();
    }

    public void setIcon(Kit kit, ItemStack item) {
        ItemStack icon = item.clone();
        icon.setAmount(1);
        kit.icon = icon;
        save();
    }

    // ---- giving a kit --------------------------------------------------------

    /** Replaces the player's inventory with the kit and resets health/food/effects. */
    public void apply(Player p, Kit kit) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setStorageContents(copy(kit.contents, 36));
        inv.setArmorContents(copy(kit.armor, 4));
        inv.setItemInOffHand(kit.offhand == null ? null : kit.offhand.clone());

        for (PotionEffect effect : new ArrayList<>(p.getActivePotionEffects())) {
            p.removePotionEffect(effect.getType());
        }
        AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setLevel(0);
        p.setExp(0f);
    }

    // ---- io ------------------------------------------------------------------

    public void load() {
        kits.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("kits");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            ItemStack[] contents = new ItemStack[36];
            ItemStack[] armor = new ItemStack[4];
            readSlots(s.getConfigurationSection("contents"), contents);
            readSlots(s.getConfigurationSection("armor"), armor);
            kits.put(id, new Kit(id, s.getString("name"), s.getLong("price"), s.getItemStack("icon"),
                    contents, armor, s.getItemStack("offhand")));
        }
    }

    public void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Kit kit : kits.values()) {
            String base = "kits." + kit.id;
            out.set(base + ".name", kit.name);
            out.set(base + ".price", kit.price);
            out.set(base + ".icon", kit.icon);
            out.set(base + ".offhand", kit.offhand);
            for (int i = 0; i < kit.contents.length; i++) {
                if (kit.contents[i] != null) {
                    out.set(base + ".contents." + i, kit.contents[i]);
                }
            }
            for (int i = 0; i < kit.armor.length; i++) {
                if (kit.armor[i] != null) {
                    out.set(base + ".armor." + i, kit.armor[i]);
                }
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save kits.yml: " + ex.getMessage());
        }
    }

    private void createDefault() {
        ItemStack[] contents = new ItemStack[36];
        contents[0] = new ItemStack(Material.STONE_SWORD);
        contents[1] = new ItemStack(Material.COOKED_BEEF, 16);
        ItemStack[] armor = new ItemStack[4];
        armor[0] = new ItemStack(Material.LEATHER_BOOTS);
        armor[1] = new ItemStack(Material.LEATHER_LEGGINGS);
        armor[2] = new ItemStack(Material.LEATHER_CHESTPLATE);
        armor[3] = new ItemStack(Material.LEATHER_HELMET);
        kits.put("starter", new Kit("starter", "Starter", 0L, new ItemStack(Material.STONE_SWORD), contents, armor, null));
        save();
    }

    // ---- helpers -------------------------------------------------------------

    private static void readSlots(ConfigurationSection section, ItemStack[] target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                if (slot >= 0 && slot < target.length) {
                    target[slot] = section.getItemStack(key);
                }
            } catch (NumberFormatException ignored) {
                // skip junk keys
            }
        }
    }

    private static ItemStack[] copy(ItemStack[] in, int size) {
        ItemStack[] out = new ItemStack[size];
        for (int i = 0; i < size && i < in.length; i++) {
            ItemStack item = in[i];
            out[i] = (item == null || item.getType().isAir()) ? null : item.clone();
        }
        return out;
    }

    private static ItemStack firstItem(ItemStack[] contents, ItemStack[] armor) {
        for (ItemStack item : contents) {
            if (item != null) {
                return item;
            }
        }
        for (ItemStack item : armor) {
            if (item != null) {
                return item;
            }
        }
        return null;
    }
}
