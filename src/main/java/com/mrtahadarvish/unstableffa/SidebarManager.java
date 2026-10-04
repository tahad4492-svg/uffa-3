package com.mrtahadarvish.unstableffa;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sidebar scoreboard shown to every player:
 * <pre>
 *   UnstableFFA
 *   Active Players: 7
 *   Coins: 120
 * </pre>
 */
public final class SidebarManager implements Listener {

    private static final String[] ENTRIES = {"§0", "§1", "§2", "§3"};

    private static final class Sidebar {
        final Scoreboard board;
        final Team[] lines = new Team[ENTRIES.length];
        final String[] last = new String[ENTRIES.length];

        Sidebar(UnstableFFA plugin) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = board.registerNewObjective("ufa", Criteria.DUMMY,
                    plugin.parse(plugin.getConfig().getString("scoreboard.title", "<red><bold>UnstableFFA")));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            hideNumbers(objective);
            for (int i = 0; i < ENTRIES.length; i++) {
                lines[i] = board.registerNewTeam("l" + i);
                lines[i].addEntry(ENTRIES[i]);
                objective.getScore(ENTRIES[i]).setScore(ENTRIES.length - i);   // top line has the highest score
            }
        }

        void set(UnstableFFA plugin, int line, String miniMessage) {
            if (miniMessage.equals(last[line])) {
                return;
            }
            last[line] = miniMessage;
            lines[line].prefix(miniMessage.isEmpty() ? Component.empty() : plugin.parse(miniMessage));
        }

        /** Hides the red score numbers on the right (newer Paper versions); harmless if unavailable. */
        private static void hideNumbers(Objective objective) {
            try {
                Class<?> format = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
                Object blank = format.getMethod("blank").invoke(null);
                Objective.class.getMethod("numberFormat", format).invoke(objective, blank);
            } catch (Throwable ignored) {
                // numbers stay visible, nothing else breaks
            }
        }
    }

    private final UnstableFFA plugin;
    private final Map<UUID, Sidebar> boards = new HashMap<>();

    public SidebarManager(UnstableFFA plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, 20L);
    }

    private void update() {
        int active = activePlayers();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Sidebar sidebar = boards.computeIfAbsent(p.getUniqueId(), id -> new Sidebar(plugin));
            if (p.getScoreboard() != sidebar.board) {
                p.setScoreboard(sidebar.board);
            }
            long coins = plugin.data().getCoins(p.getUniqueId());
            sidebar.set(plugin, 0, "");
            sidebar.set(plugin, 1, "<gray>Active Players: <white>" + active);
            sidebar.set(plugin, 2, "<gray>Coins: <gold>" + coins);
            sidebar.set(plugin, 3, "");
        }
    }

    /** Players currently fighting in an arena (or everyone online, see scoreboard.active-players). */
    private int activePlayers() {
        if ("online".equalsIgnoreCase(plugin.getConfig().getString("scoreboard.active-players", "arena"))) {
            return Bukkit.getOnlinePlayers().size();
        }
        int count = 0;
        for (ArenaManager.Arena arena : plugin.arenas().all()) {
            World world = Bukkit.getWorld(arena.worldName);
            if (world != null) {
                count += world.getPlayers().size();
            }
        }
        return count;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        boards.remove(e.getPlayer().getUniqueId());
    }
}
