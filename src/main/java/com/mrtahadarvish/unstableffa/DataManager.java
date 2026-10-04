package com.mrtahadarvish.unstableffa;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stores coins, owned kits and the selected kit for every player (data.yml).
 */
public final class DataManager {

    private final UnstableFFA plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private boolean dirty;

    public DataManager(UnstableFFA plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        // save every 30 seconds if something changed
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                saveNow();
            }
        }, 600L, 600L);
    }

    private String path(UUID id, String key) {
        return "players." + id + "." + key;
    }

    // ---- coins -------------------------------------------------------------

    public long getCoins(UUID id) {
        return yaml.getLong(path(id, "coins"), plugin.getConfig().getLong("coins.starting", 0L));
    }

    public void setCoins(UUID id, long amount) {
        yaml.set(path(id, "coins"), Math.max(0L, amount));
        dirty = true;
    }

    public void addCoins(UUID id, long amount) {
        setCoins(id, getCoins(id) + amount);
    }

    /** @return false (and changes nothing) if the player can't afford it */
    public boolean takeCoins(UUID id, long amount) {
        long current = getCoins(id);
        if (current < amount) {
            return false;
        }
        setCoins(id, current - amount);
        return true;
    }

    // ---- kits --------------------------------------------------------------

    public boolean ownsKit(UUID id, String kitId) {
        return yaml.getStringList(path(id, "kits")).contains(kitId);
    }

    public void grantKit(UUID id, String kitId) {
        List<String> owned = new ArrayList<>(yaml.getStringList(path(id, "kits")));
        if (!owned.contains(kitId)) {
            owned.add(kitId);
            yaml.set(path(id, "kits"), owned);
            dirty = true;
        }
    }

    public void revokeKit(UUID id, String kitId) {
        List<String> owned = new ArrayList<>(yaml.getStringList(path(id, "kits")));
        if (owned.remove(kitId)) {
            yaml.set(path(id, "kits"), owned);
            dirty = true;
        }
    }

    public String getSelectedKit(UUID id) {
        return yaml.getString(path(id, "selected"));
    }

    public void setSelectedKit(UUID id, String kitId) {
        yaml.set(path(id, "selected"), kitId);
        dirty = true;
    }

    // ---- stats / leaderboard ----------------------------------------------------

    public long getStat(UUID id, String stat) {
        return yaml.getLong(path(id, stat), 0L);
    }

    public void addStat(UUID id, String stat) {
        yaml.set(path(id, stat), getStat(id, stat) + 1L);
        dirty = true;
    }

    public void setName(UUID id, String name) {
        if (!name.equals(yaml.getString(path(id, "name")))) {
            yaml.set(path(id, "name"), name);
            dirty = true;
        }
    }

    public String getName(UUID id) {
        String stored = yaml.getString(path(id, "name"));
        if (stored != null) {
            return stored;
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(id);
        return off.getName() != null ? off.getName() : id.toString().substring(0, 8);
    }

    /** Players with the highest value of "kills", "deaths" or "coins" (zero values are left out). */
    public List<Map.Entry<UUID, Long>> top(String stat, int limit) {
        List<Map.Entry<UUID, Long>> list = new ArrayList<>();
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    long value = stat.equals("coins") ? getCoins(id) : getStat(id, stat);
                    if (value > 0) {
                        list.add(Map.entry(id, value));
                    }
                } catch (IllegalArgumentException ignored) {
                    // not a player entry
                }
            }
        }
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }

    // ---- shop purchases waiting to be delivered -----------------------------------

    public List<ItemStack> getStash(UUID id) {
        List<ItemStack> out = new ArrayList<>();
        List<?> raw = yaml.getList(path(id, "stash"));
        if (raw != null) {
            for (Object o : raw) {
                if (o instanceof ItemStack item) {
                    out.add(item);
                }
            }
        }
        return out;
    }

    public void addStash(UUID id, ItemStack item) {
        List<ItemStack> stash = getStash(id);
        stash.add(item);
        yaml.set(path(id, "stash"), stash);
        dirty = true;
    }

    /** Returns everything the player bought in the shop and empties their stash. */
    public List<ItemStack> takeStash(UUID id) {
        List<ItemStack> stash = getStash(id);
        if (!stash.isEmpty()) {
            yaml.set(path(id, "stash"), null);
            dirty = true;
        }
        return stash;
    }

    // ---- io ----------------------------------------------------------------

    public void saveNow() {
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }
}
