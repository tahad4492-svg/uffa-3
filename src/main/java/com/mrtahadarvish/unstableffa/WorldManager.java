package com.mrtahadarvish.unstableffa;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * A small Multiverse-Core style world manager. Worlds created or imported with
 * /ufa world create are remembered in worlds.yml and loaded on every startup.
 */
public final class WorldManager {

    public enum Kind {
        NORMAL, FLAT, VOID, NETHER, END;

        public static Kind parse(String text) {
            try {
                return valueOf(text.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    /** Generates completely empty chunks (a void world). */
    public static final class VoidGenerator extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(org.bukkit.World world, Random random) {
            return new Location(world, 0.5, 64, 0.5);
        }
    }

    private final UnstableFFA plugin;
    private final File file;
    private final Map<String, Kind> known = new LinkedHashMap<>();

    public WorldManager(UnstableFFA plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "worlds.yml");
        load();
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("worlds");
        if (section == null) {
            return;
        }
        for (String name : section.getKeys(false)) {
            Kind kind = Kind.parse(section.getString(name, "NORMAL"));
            if (kind == null) {
                kind = Kind.NORMAL;
            }
            boolean exists = Bukkit.getWorld(name) != null
                    || new File(Bukkit.getWorldContainer(), name).isDirectory();
            if (!exists) {
                plugin.getLogger().warning("World folder '" + name + "' is missing - skipping it.");
                continue;
            }
            try {
                create(name, kind);
            } catch (Exception ex) {
                plugin.getLogger().severe("Could not load world '" + name + "': " + ex.getMessage());
            }
        }
    }

    /**
     * Creates the world, or loads it if the folder already exists (this is how
     * you "import" a map: drop the folder in the server directory, then run
     * /ufa world create &lt;folder-name&gt;).
     *
     * @return the world, or null if the server refused to create it
     */
    public World create(String name, Kind kind) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            known.putIfAbsent(name, kind);
            save();
            return existing;
        }

        boolean fresh = !new File(Bukkit.getWorldContainer(), name).exists();

        WorldCreator creator = new WorldCreator(name);
        switch (kind) {
            case NORMAL -> creator.environment(World.Environment.NORMAL);
            case FLAT -> {
                creator.environment(World.Environment.NORMAL);
                creator.type(WorldType.FLAT);
            }
            case VOID -> {
                creator.environment(World.Environment.NORMAL);
                creator.generator(new VoidGenerator());
            }
            case NETHER -> creator.environment(World.Environment.NETHER);
            case END -> creator.environment(World.Environment.THE_END);
        }

        World world = creator.createWorld();
        if (world == null) {
            return null;
        }

        if (kind == Kind.VOID && fresh) {
            // small platform so there is somewhere to stand
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    world.getBlockAt(x, 63, z).setType(Material.STONE, false);
                }
            }
            world.setSpawnLocation(0, 64, 0);
        }

        known.put(name, kind);
        save();
        return world;
    }

    public List<String> savedNames() {
        return new ArrayList<>(known.keySet());
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Kind> entry : known.entrySet()) {
            yaml.set("worlds." + entry.getKey(), entry.getValue().name());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save worlds.yml: " + ex.getMessage());
        }
    }
}
