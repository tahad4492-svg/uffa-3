package com.mrtahadarvish.unstableffa;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Kill rewards (2 coins per kill by default), spawn protection and the
 * death -> lobby loop.
 */
public final class CombatListener implements Listener {

    private final UnstableFFA plugin;

    public CombatListener(UnstableFFA plugin) {
        this.plugin = plugin;
    }

    // ---- spawn protection ---------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && plugin.arenas().isSpawnProtected(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (damager instanceof Player p) {
            // attacking ends your own protection early
            plugin.arenas().removeSpawnProtection(p.getUniqueId());
        }
    }

    // ---- death / coins ----------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        if (!plugin.arenas().isArenaWorld(victim.getWorld())) {
            return;
        }

        // nothing drops - kits are re-issued fresh on every entry
        e.getDrops().clear();
        e.setDroppedExp(0);
        plugin.arenas().removeSpawnProtection(victim.getUniqueId());

        // tamed pets (wolves, cats, horses, dog cannon dogs...) die together with their owner
        Pets.removeTamed(victim.getWorld(), victim.getUniqueId());
        plugin.data().addStat(victim.getUniqueId(), "deaths");

        Player killer = findKiller(victim);
        if (killer != null && !killer.equals(victim)) {
            plugin.data().addStat(killer.getUniqueId(), "kills");
            long reward = plugin.getConfig().getLong("coins.per-kill", 2L);
            plugin.data().addCoins(killer.getUniqueId(), reward);
            killer.sendActionBar(plugin.parse("<gold>+" + reward + " coins <gray>(total: <yellow>"
                    + plugin.data().getCoins(killer.getUniqueId()) + "<gray>)"));
        }

        if (plugin.getConfig().getBoolean("arena.auto-respawn", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (victim.isOnline() && victim.isDead()) {
                    victim.spigot().respawn();
                }
            }, 2L);
        }
    }

    /**
     * Who gets the kill. Normally the last player that hit the victim, but kills made with
     * projectiles, primed TNT or a player's tamed pets (dog cannons...) count for their owner too.
     */
    private Player findKiller(Player victim) {
        Player direct = victim.getKiller();
        if (direct != null) {
            return direct;
        }
        if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent hit) {
            Entity damager = hit.getDamager();
            if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }
            if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player source) {
                return source;
            }
            if (damager instanceof Tameable pet && pet.getOwnerUniqueId() != null) {
                return Bukkit.getPlayer(pet.getOwnerUniqueId());
            }
            if (damager instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.data().setName(e.getPlayer().getUniqueId(), e.getPlayer().getName());   // names for the leaderboard
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (plugin.arenas().isArenaWorld(p.getWorld())) {
            Pets.removeTamed(p.getWorld(), p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        e.setRespawnLocation(plugin.lobby().getLobby());
        // the respawn has finished by next tick: give lobby state (kit selector etc.)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && !plugin.lobby().bypass(p)) {
                plugin.lobby().prepare(p);
            }
        });
    }
}
