package com.mrtahadarvish.unstableffa;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * The lobby: where players spawn, pick a kit and find the arena portals.
 * Set it with /ufa setlobby. Players can do nothing in the lobby.
 */
public final class LobbyManager {

    private final UnstableFFA plugin;
    private final File file;
    private YamlConfiguration yaml;
    private final NamespacedKey selectorKey;

    public LobbyManager(UnstableFFA plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "lobby.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        this.selectorKey = new NamespacedKey(plugin, "kit_selector");
    }

    // ---- location ------------------------------------------------------------

    public Location getLobby() {
        String worldName = yaml.getString("world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return new Location(world,
                yaml.getDouble("x"), yaml.getDouble("y"), yaml.getDouble("z"),
                (float) yaml.getDouble("yaw"), (float) yaml.getDouble("pitch"));
    }

    public void setLobby(Location loc) {
        yaml.set("world", loc.getWorld().getName());
        yaml.set("x", loc.getX());
        yaml.set("y", loc.getY());
        yaml.set("z", loc.getZ());
        yaml.set("yaw", (double) loc.getYaw());
        yaml.set("pitch", (double) loc.getPitch());
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save lobby.yml: " + ex.getMessage());
        }
    }

    public boolean isLobbyWorld(World world) {
        String lobbyWorld = yaml.getString("world", Bukkit.getWorlds().get(0).getName());
        return world.getName().equals(lobbyWorld);
    }

    // ---- rules ---------------------------------------------------------------

    /** Admins in creative mode may build/break/etc. in the lobby. */
    public boolean bypass(Player p) {
        return p.hasPermission("ufa.admin") && p.getGameMode() == GameMode.CREATIVE;
    }

    /** True if the player is in the lobby world and the lobby rules apply to them. */
    public boolean restricted(Player p) {
        return isLobbyWorld(p.getWorld()) && !bypass(p);
    }

    // ---- sending players to the lobby ----------------------------------------

    public void sendToLobby(Player p) {
        if (plugin.arenas().isArenaWorld(p.getWorld())) {
            Pets.removeTamed(p.getWorld(), p.getUniqueId());   // pets don't follow you out of the arena
        }
        p.teleport(getLobby());
        if (!bypass(p)) {
            prepare(p);
        }
    }

    /** Clean slate: empty inventory, full health, adventure mode, kit selector item. */
    public void prepare(Player p) {
        p.closeInventory();
        p.getInventory().clear();
        for (PotionEffect effect : new ArrayList<>(p.getActivePotionEffects())) {
            p.removePotionEffect(effect.getType());
        }
        p.setGameMode(GameMode.ADVENTURE);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setLevel(0);
        p.setExp(0f);
        if (plugin.getConfig().getBoolean("lobby.give-kit-selector", true)) {
            p.getInventory().setItem(4, selectorItem());
        }
    }

    // ---- kit selector item ---------------------------------------------------

    public ItemStack selectorItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        Component name = plugin.parse("<!italic><gold><bold>Kit Selector <gray>(Right Click)");
        meta.displayName(name);
        meta.getPersistentDataContainer().set(selectorKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSelector(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(selectorKey, PersistentDataType.BYTE);
    }
}
