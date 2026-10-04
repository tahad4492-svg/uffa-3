package com.mrtahadarvish.unstableffa;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Arenas = FFA maps. Once an arena is "locked" every block that changes in its
 * world is recorded (original state, first change only). Every N minutes (30 by
 * default) all recorded blocks are put back, so the map itself is never touched
 * but anything players added / destroyed is undone.
 */
public final class ArenaManager {

    public static final class Arena {
        public final String name;
        public String worldName;
        public boolean locked;      // false = edit mode (no tracking, no resets)
        public boolean resetting;
        public long nextReset;
        public final List<double[]> spawns = new ArrayList<>();             // x, y, z, yaw, pitch
        public final Map<Long, BlockState> changes = new HashMap<>();       // packed pos -> original state
        public final Deque<BlockState> restoring = new ArrayDeque<>();      // blocks still waiting to be put back by a running reset

        Arena(String name, String worldName) {
            this.name = name;
            this.worldName = worldName;
        }
    }

    private final UnstableFFA plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<String, Arena> byWorld = new HashMap<>();
    private final Set<UUID> spawnProtected = new HashSet<>();
    private final Random random = new Random();
    private Set<EntityType> removable = new HashSet<>();

    public ArenaManager(UnstableFFA plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        reloadSettings();
        load();
    }

    // ---- lookup --------------------------------------------------------------

    public Arena get(String name) {
        return name == null ? null : arenas.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Arena> all() {
        return arenas.values();
    }

    public List<String> names() {
        return new ArrayList<>(arenas.keySet());
    }

    public boolean isArenaWorld(World world) {
        return world != null && byWorld.containsKey(world.getName());
    }

    /** True if blocks in this world are currently being recorded. */
    public boolean isTracking(World world) {
        Arena a = world == null ? null : byWorld.get(world.getName());
        return a != null && a.locked;
    }

    public int pending(Arena arena) {
        return arena.changes.size() + arena.restoring.size();
    }

    // ---- create / delete -------------------------------------------------------

    public Arena create(String name, World world) {
        String key = name.toLowerCase(Locale.ROOT);
        if (arenas.containsKey(key) || byWorld.containsKey(world.getName())) {
            return null;
        }
        Arena arena = new Arena(key, world.getName());
        arenas.put(key, arena);
        byWorld.put(world.getName(), arena);
        save();
        return arena;
    }

    public boolean delete(String name) {
        Arena arena = arenas.remove(name.toLowerCase(Locale.ROOT));
        if (arena == null) {
            return false;
        }
        if (arena.locked) {
            restoreNow(arena);
        }
        byWorld.remove(arena.worldName);
        save();
        return true;
    }

    public void addSpawn(Arena arena, Location loc) {
        arena.spawns.add(new double[]{loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()});
        save();
    }

    public void clearSpawns(Arena arena) {
        arena.spawns.clear();
        save();
    }

    public Location randomSpawn(Arena arena) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world == null || arena.spawns.isEmpty()) {
            return null;
        }
        double[] s = arena.spawns.get(random.nextInt(arena.spawns.size()));
        return new Location(world, s[0], s[1], s[2], (float) s[3], (float) s[4]);
    }

    // ---- lock (play mode) / edit mode -------------------------------------------

    /** Starts recording changes and the reset timer. The current map becomes "the map". */
    public void lock(Arena arena) {
        arena.changes.clear();
        arena.locked = true;
        arena.resetting = false;
        arena.nextReset = System.currentTimeMillis() + intervalMillis();
        save();
    }

    /** Undoes any recorded changes, then stops recording so the map can be edited. */
    public void edit(Arena arena) {
        restoreNow(arena);
        arena.locked = false;
        arena.resetting = false;
        save();
    }

    // ---- change tracking ----------------------------------------------------------

    public void track(Block block) {
        Arena arena = byWorld.get(block.getWorld().getName());
        if (arena == null || !arena.locked) {
            return;
        }
        long key = pack(block.getX(), block.getY(), block.getZ());
        if (!arena.changes.containsKey(key)) {
            arena.changes.put(key, block.getState());
        }
    }

    public void track(BlockState state) {
        Arena arena = byWorld.get(state.getWorld().getName());
        if (arena == null || !arena.locked) {
            return;
        }
        arena.changes.putIfAbsent(pack(state.getX(), state.getY(), state.getZ()), state);
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) ((y + 2048) & 0xFFF);
    }

    // ---- resetting ------------------------------------------------------------------

    /** Full reset: empties the arena, removes leftover entities, restores blocks over a few ticks. */
    public void reset(Arena arena) {
        World world = Bukkit.getWorld(arena.worldName);
        if (world == null || arena.resetting) {
            return;
        }
        arena.resetting = true;
        arena.nextReset = System.currentTimeMillis() + intervalMillis();

        if (plugin.getConfig().getBoolean("arena.send-players-to-lobby-on-reset", true)
                && !plugin.lobby().isLobbyWorld(world)) {
            for (Player p : new ArrayList<>(world.getPlayers())) {
                if (!plugin.lobby().bypass(p)) {
                    plugin.lobby().sendToLobby(p);
                    plugin.send(p, "<yellow>The map is resetting - you were sent back to the lobby.");
                }
            }
        }

        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player) && removable.contains(entity.getType())) {
                entity.remove();
            }
        }

        arena.restoring.addAll(arena.changes.values());
        arena.changes.clear();
        int perTick = Math.max(100, plugin.getConfig().getInt("arena.blocks-restored-per-tick", 4000));

        new BukkitRunnable() {
            @Override
            public void run() {
                for (int n = 0; n < perTick && !arena.restoring.isEmpty(); n++) {
                    restoreState(arena.restoring.poll());
                }
                if (arena.restoring.isEmpty()) {
                    arena.resetting = false;
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Puts every recorded block back right now (used on edit mode, delete and shutdown). */
    public int restoreNow(Arena arena) {
        List<BlockState> states = new ArrayList<>(arena.changes.values());
        arena.changes.clear();
        for (BlockState state : states) {
            restoreState(state);
        }
        // blocks a running reset has not reached yet (older originals, so restored last)
        int queued = arena.restoring.size();
        while (!arena.restoring.isEmpty()) {
            restoreState(arena.restoring.poll());
        }
        return states.size() + queued;
    }

    public void restoreAllNow() {
        for (Arena arena : arenas.values()) {
            if (arena.locked && (!arena.changes.isEmpty() || !arena.restoring.isEmpty())) {
                restoreNow(arena);
            }
        }
    }

    private void restoreState(BlockState state) {
        World world = state.getWorld();
        world.getChunkAt(state.getX() >> 4, state.getZ() >> 4); // make sure the chunk is loaded
        state.update(true, false);
    }

    // ---- timer ------------------------------------------------------------------------

    public void startTimer() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        List<Integer> warnings = plugin.getConfig().getIntegerList("arena.reset-warnings");
        for (Arena arena : arenas.values()) {
            if (!arena.locked || arena.resetting) {
                continue;
            }
            if (plugin.portals().isIdleRotationArena(arena.name)) {
                continue;   // waiting for its turn in a portal rotation
            }
            long left = (arena.nextReset - now + 999L) / 1000L;
            if (left <= 0) {
                reset(arena);
                plugin.portals().onArenaReset(arena);   // rotating portals move on to the next arena
                continue;
            }
            if (warnings.contains((int) left)) {
                World world = Bukkit.getWorld(arena.worldName);
                if (world != null) {
                    String next = plugin.portals().nextArenaName(arena.name);
                    String extra = next == null ? "" : " <gray>Next map: <white>" + UnstableFFA.esc(next) + "<gray>.";
                    for (Player p : world.getPlayers()) {
                        plugin.send(p, "<yellow>Map resets in <red>" + left + "s<yellow>!" + extra);
                    }
                }
            }
        }
    }

    /** Starts a fresh reset countdown (used when a rotating portal opens this arena). */
    public void startCycle(Arena arena) {
        if (arena.locked) {
            arena.nextReset = System.currentTimeMillis() + intervalMillis();
        }
    }

    public long secondsUntilReset(Arena arena) {
        return Math.max(0L, (arena.nextReset - System.currentTimeMillis()) / 1000L);
    }

    private long intervalMillis() {
        return Math.max(1L, plugin.getConfig().getLong("arena.reset-interval-minutes", 15L)) * 60_000L;
    }

    // ---- joining an arena ------------------------------------------------------------------

    /**
     * Sends the player into the arena with their selected kit.
     *
     * @return false if they could not join (a message has already been sent)
     */
    public boolean join(Player p, Arena arena) {
        if (!arena.locked) {
            plugin.send(p, "<red>This arena is not open yet.");
            return false;
        }
        if (arena.resetting) {
            plugin.send(p, "<red>This arena is resetting, try again in a moment.");
            return false;
        }
        Location spawn = randomSpawn(arena);
        if (spawn == null) {
            plugin.send(p, "<red>This arena has no spawn points yet.");
            return false;
        }

        UUID id = p.getUniqueId();
        KitManager.Kit kit = plugin.kits().get(plugin.data().getSelectedKit(id));
        if (kit == null) {
            plugin.send(p, "<red>Pick a kit first - use <white>/kit<red> or the nether star.");
            return false;
        }
        if (kit.price > 0 && !plugin.data().ownsKit(id, kit.id)) {
            plugin.send(p, "<red>You don't own the kit <white>" + UnstableFFA.esc(kit.display()) + "<red>. Pick another one.");
            return false;
        }

        p.teleport(spawn);
        p.setGameMode(GameMode.SURVIVAL);
        plugin.kits().apply(p, kit);
        deliverShopItems(p);
        plugin.send(p, "<green>Fight! <gray>Kit: <white>" + UnstableFFA.esc(kit.display())
                + " <gray>| Map resets every " + plugin.getConfig().getLong("arena.reset-interval-minutes", 15L) + " min.");

        int seconds = plugin.getConfig().getInt("arena.spawn-protection-seconds", 3);
        if (seconds > 0) {
            spawnProtected.add(id);
            Bukkit.getScheduler().runTaskLater(plugin, () -> spawnProtected.remove(id), seconds * 20L);
        }
        return true;
    }

    /** Items bought in /shop are added on top of the kit the next time the player enters an arena. */
    private void deliverShopItems(Player p) {
        List<ItemStack> bought = plugin.data().takeStash(p.getUniqueId());
        if (bought.isEmpty()) {
            return;
        }
        for (ItemStack item : bought) {
            for (ItemStack leftover : p.getInventory().addItem(item).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
            }
        }
        plugin.send(p, "<green>Your shop items were added to your inventory.");
    }

    public boolean isSpawnProtected(UUID id) {
        return spawnProtected.contains(id);
    }

    public void removeSpawnProtection(UUID id) {
        spawnProtected.remove(id);
    }

    // ---- io --------------------------------------------------------------------------------

    public void reloadSettings() {
        Set<EntityType> set = new HashSet<>();
        for (String name : plugin.getConfig().getStringList("arena.remove-entities-on-reset")) {
            try {
                set.add(EntityType.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Unknown entity type in config (arena.remove-entities-on-reset): " + name);
            }
        }
        removable = set;
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("arenas");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null || s.getString("world") == null) {
                continue;
            }
            Arena arena = new Arena(name, s.getString("world"));
            arena.locked = s.getBoolean("locked");
            for (String line : s.getStringList("spawns")) {
                String[] p = line.split(";");
                if (p.length == 5) {
                    try {
                        arena.spawns.add(new double[]{
                                Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                                Double.parseDouble(p[3]), Double.parseDouble(p[4])});
                    } catch (NumberFormatException ignored) {
                        // skip a broken spawn line
                    }
                }
            }
            if (arena.locked) {
                arena.nextReset = System.currentTimeMillis() + intervalMillis();
            }
            arenas.put(name, arena);
            byWorld.put(arena.worldName, arena);
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Arena arena : arenas.values()) {
            String base = "arenas." + arena.name;
            yaml.set(base + ".world", arena.worldName);
            yaml.set(base + ".locked", arena.locked);
            List<String> lines = new ArrayList<>();
            for (double[] s : arena.spawns) {
                lines.add(String.format(Locale.ROOT, "%.2f;%.2f;%.2f;%.1f;%.1f", s[0], s[1], s[2], s[3], s[4]));
            }
            yaml.set(base + ".spawns", lines);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save arenas.yml: " + ex.getMessage());
        }
    }
}
