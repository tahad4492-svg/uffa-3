package com.mrtahadarvish.unstableffa;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Walking/jumping into a linked end portal sends the player into its arena.
 */
public final class PortalListener implements Listener {

    private final UnstableFFA plugin;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public PortalListener(UnstableFFA plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedBlock() || e.getTo() == null) {
            return;
        }
        Block block = e.getTo().getBlock();
        if (block.getType() != Material.END_PORTAL) {
            return;
        }
        PortalManager.Portal portal = plugin.portals().findAt(block);
        if (portal == null) {
            return;
        }

        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        Long last = cooldown.get(p.getUniqueId());
        if (last != null && now - last < 2000L) {
            return;
        }
        cooldown.put(p.getUniqueId(), now);

        ArenaManager.Arena arena = plugin.arenas().get(portal.current());
        if (arena == null) {
            plugin.send(p, "<red>This portal is not linked to a working arena.");
            return;
        }

        // run next tick so we are not teleporting inside the move event
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.arenas().join(p, arena)) {
                plugin.lobby().sendToLobby(p);   // step out of the portal so it doesn't re-trigger
            }
        });
    }

    /** Stop the vanilla "go to the End" behaviour in lobby and arena worlds. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPortal(PlayerPortalEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            return;
        }
        if (plugin.lobby().isLobbyWorld(e.getFrom().getWorld())
                || plugin.arenas().isArenaWorld(e.getFrom().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityPortal(EntityPortalEvent e) {
        if (plugin.lobby().isLobbyWorld(e.getFrom().getWorld())
                || plugin.arenas().isArenaWorld(e.getFrom().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cooldown.remove(e.getPlayer().getUniqueId());
    }
}
