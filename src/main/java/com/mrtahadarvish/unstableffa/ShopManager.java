package com.mrtahadarvish.unstableffa;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The /shop: sections (titles) that each hold items with a coin price.
 * Stored in shop.yml, managed in-game by admins with /shop ... commands.
 */
public final class ShopManager {

    public static final int MAX_SECTIONS = 27;   // main menu is barrel sized
    public static final int MAX_ITEMS = 18;      // top two rows of a section menu

    public static final class Entry {
        public final ItemStack item;
        public long price;

        Entry(ItemStack item, long price) {
            this.item = item;
            this.price = price;
        }
    }

    public static final class Section {
        public final String id;
        public String title;
        public ItemStack icon;
        public final List<Entry> items = new ArrayList<>();

        Section(String id, String title, ItemStack icon) {
            this.id = id;
            this.title = title;
            this.icon = icon;
        }

        public String display() {
            return title.replace('_', ' ');
        }
    }

    private final UnstableFFA plugin;
    private final File file;
    private final Map<String, Section> sections = new LinkedHashMap<>();

    public ShopManager(UnstableFFA plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shop.yml");
        load();
    }

    // ---- lookup --------------------------------------------------------------

    public Section get(String id) {
        return id == null ? null : sections.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Section> all() {
        return new ArrayList<>(sections.values());
    }

    public List<String> ids() {
        return new ArrayList<>(sections.keySet());
    }

    // ---- editing -------------------------------------------------------------

    /** @return the new section, or null if the id exists or the shop is full */
    public Section createSection(String rawName, ItemStack icon) {
        String id = rawName.toLowerCase(Locale.ROOT);
        if (sections.containsKey(id) || sections.size() >= MAX_SECTIONS) {
            return null;
        }
        ItemStack shown = (icon == null || icon.getType().isAir()) ? new ItemStack(Material.CHEST) : icon.clone();
        shown.setAmount(1);
        Section section = new Section(id, rawName, shown);
        sections.put(id, section);
        save();
        return section;
    }

    public boolean deleteSection(String id) {
        boolean removed = sections.remove(id.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public void setTitle(Section section, String title) {
        section.title = title;
        save();
    }

    public void setIcon(Section section, ItemStack icon) {
        ItemStack shown = icon.clone();
        shown.setAmount(1);
        section.icon = shown;
        save();
    }

    /** @return false if the section is full */
    public boolean addItem(Section section, ItemStack item, long price) {
        if (section.items.size() >= MAX_ITEMS) {
            return false;
        }
        section.items.add(new Entry(item.clone(), Math.max(0L, price)));
        save();
        return true;
    }

    /** @param number 1-based position shown in /shop list */
    public boolean removeItem(Section section, int number) {
        if (number < 1 || number > section.items.size()) {
            return false;
        }
        section.items.remove(number - 1);
        save();
        return true;
    }

    public boolean setPrice(Section section, int number, long price) {
        if (number < 1 || number > section.items.size()) {
            return false;
        }
        section.items.get(number - 1).price = Math.max(0L, price);
        save();
        return true;
    }

    // ---- io ------------------------------------------------------------------

    public void load() {
        sections.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("sections");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            ItemStack icon = s.getItemStack("icon");
            Section section = new Section(id, s.getString("title", id), icon == null ? new ItemStack(Material.CHEST) : icon);
            ConfigurationSection items = s.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    ItemStack item = items.getItemStack(key + ".item");
                    if (item != null && section.items.size() < MAX_ITEMS) {
                        section.items.add(new Entry(item, items.getLong(key + ".price")));
                    }
                }
            }
            sections.put(id, section);
        }
    }

    public void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Section section : sections.values()) {
            String base = "sections." + section.id;
            out.set(base + ".title", section.title);
            out.set(base + ".icon", section.icon);
            for (int i = 0; i < section.items.size(); i++) {
                out.set(base + ".items." + i + ".item", section.items.get(i).item);
                out.set(base + ".items." + i + ".price", section.items.get(i).price);
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save shop.yml: " + ex.getMessage());
        }
    }
}
