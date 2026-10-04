package com.mrtahadarvish.unstableffa;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Floating text (text display entities, not saved with the world):
 *  - above every arena portal: arena name, active players, countdown
 *  - optionally a kill leaderboard (/ufa leaderboard set)
 */
public final class HologramManager {

    private final UnstableFFA plugin;
    private final File file;
    private final Map<String, TextDisplay> entities = new HashMap<>();
    private final Map<String, String> lastText = new HashMap<>();
    private final Map<String, PortalManager.Portal> portalOwner = new HashMap<>();
    private final Map<String, Location> portalSpots = new HashMap<>();
    private Location board;
    private String boardText = "";
    private long boardBuiltAt;

    public HologramManager(UnstableFFA plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "leaderboard.yml");
        loadBoard();
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("hologram.enabled", true)) {
            return;
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    public void removeAll() {
        for (TextDisplay td : entities.values()) {
            if (td != null) {
                td.remove();
            }
        }
        entities.clear();
        lastText.clear();
    }

    // ---- leaderboard hologram location ----------------------------------------------

    public void setBoard(Location loc) {
        board = loc.clone();
        boardBuiltAt = 0L;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", loc.getWorld().getName());
        yaml.set("x", loc.getX());
        yaml.set("y", loc.getY());
        yaml.set("z", loc.getZ());
        saveBoard(yaml);
    }

    public void removeBoard() {
        board = null;
        saveBoard(new YamlConfiguration());
    }

    private void saveBoard(YamlConfiguration yaml) {
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save leaderboard.yml: " + ex.getMessage());
        }
    }

    private void loadBoard() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String worldName = yaml.getString("world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world != null) {
            board = new Location(world, yaml.getDouble("x"), yaml.getDouble("y"), yaml.getDouble("z"));
        }
    }

    // ---- update loop -------------------------------------------------------------------

    private void tick() {
        Set<String> wanted = new HashSet<>();

        for (PortalManager.Portal portal : plugin.portals().all()) {
            String key = "portal:" + portal.id;
            Location spot = portalSpot(portal);
            if (spot == null) {
                continue;
            }
            wanted.add(key);
            show(key, spot, portalText(portal));
        }

        if (board != null && board.getWorld() != null) {
            wanted.add("board");
            show("board", board, boardText());
        }

        // portals or the board that no longer exist
        for (String key : new HashSet<>(entities.keySet())) {
            if (!wanted.contains(key)) {
                TextDisplay td = entities.remove(key);
                lastText.remove(key);
                if (td != null) {
                    td.remove();
                }
            }
        }
    }

    private void show(String key, Location loc, String miniMessage) {
        World world = loc.getWorld();
        if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            return;
        }
        TextDisplay td = entities.get(key);
        if (td == null || !td.isValid()) {
            final String initial = miniMessage;
            td = world.spawn(loc, TextDisplay.class, e -> {
                e.setPersistent(false);   // never saved with the world, we recreate it ourselves
                e.setBillboard(Display.Billboard.CENTER);
                e.setAlignment(TextDisplay.TextAlignment.CENTER);
                e.text(plugin.parse(initial));
            });
            entities.put(key, td);
            lastText.put(key, miniMessage);
            return;
        }
        if (!miniMessage.equals(lastText.get(key))) {
            td.text(plugin.parse(miniMessage));
            lastText.put(key, miniMessage);
        }
    }

    // ---- texts -----------------------------------------------------------------------------

    private String portalText(PortalManager.Portal portal) {
        ArenaManager.Arena arena = plugin.arenas().get(portal.current());
        if (arena == null || !arena.locked) {
            return "<red><bold>Arena closed";
        }
        World world = Bukkit.getWorld(arena.worldName);
        int players = world == null ? 0 : world.getPlayers().size();
        long left = plugin.arenas().secondsUntilReset(arena);
        String time = String.format(Locale.ROOT, "%d:%02d", left / 60, left % 60);
        return "<red><bold>" + UnstableFFA.esc(pretty(arena.name)) + "<newline>"
                + "<gray>Active Players: <white>" + players + "<newline>"
                + "<gray>" + (portal.rotating() ? "Map changes in " : "Map resets in ") + "<white>" + time;
    }

    private String boardText() {
        long now = System.currentTimeMillis();
        if (now - boardBuiltAt > 30_000L) {
            StringBuilder sb = new StringBuilder("<gold><bold>Top Killers");
            List<Map.Entry<UUID, Long>> top = plugin.data().top("kills", 10);
            if (top.isEmpty()) {
                sb.append("<newline><gray>No kills yet");
            }
            int rank = 1;
            for (Map.Entry<UUID, Long> entry : top) {
                String color = rank == 1 ? "<gold>" : rank == 2 ? "<white>" : rank == 3 ? "<#cd7f32>" : "<gray>";
                sb.append("<newline>").append(color).append('#').append(rank).append(" <white>")
                        .append(UnstableFFA.esc(plugin.data().getName(entry.getKey())))
                        .append(" <dark_gray>- ").append(color).append(entry.getValue());
                rank++;
            }
            boardText = sb.toString();
            boardBuiltAt = now;
        }
        return boardText;
    }

    /** capital_city -> Capital City */
    private static String pretty(String name) {
        StringBuilder sb = new StringBuilder();
        for (String word : name.replace('-', ' ').replace('_', ' ').split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    // ---- where the portal hologram floats --------------------------------------------------------

    /** Centre of the portal blocks, a little above the top. Cached until the portal object changes. */
    private Location portalSpot(PortalManager.Portal portal) {
        if (portalOwner.get(portal.id) != portal || !portalSpots.containsKey(portal.id)) {
            World world = Bukkit.getWorld(portal.world);
            if (world == null || portal.blocks.isEmpty()) {
                return null;
            }
            double sx = 0;
            double sz = 0;
            int maxY = Integer.MIN_VALUE;
            for (long packed : portal.blocks) {
                sx += (int) (packed >> 38);
                sz += (int) ((packed << 26) >> 38);
                maxY = Math.max(maxY, (int) (packed & 0xFFFL) - 2048);
            }
            int n = portal.blocks.size();
            portalSpots.put(portal.id, new Location(world, sx / n + 0.5, maxY + 2.6, sz / n + 0.5));
            portalOwner.put(portal.id, portal);
        }
        return portalSpots.get(portal.id);
    }
}
