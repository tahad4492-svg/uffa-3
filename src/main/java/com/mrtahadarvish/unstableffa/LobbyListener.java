package com.mrtahadarvish.unstableffa;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * In the lobby players can do nothing: no PvP, no damage, no building, no
 * breaking, no potions, no items, no inventory moving. Admins in creative
 * mode are exempt so they can build the lobby.
 */
public final class LobbyListener implements Listener {

    private final UnstableFFA plugin;
    private final LobbyManager lobby;

    public LobbyListener(UnstableFFA plugin) {
        this.plugin = plugin;
        this.lobby = plugin.lobby();
    }

    private boolean restricted(Player p) {
        return lobby.restricted(p);
    }

    // ---- join ----------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("lobby.teleport-on-join", true)) {
            return;
        }
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> lobby.sendToLobby(p));
    }

    // ---- blocks --------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (restricted(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (restricted(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (restricted(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (restricted(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!restricted(p)) {
            return;
        }
        boolean rightClick = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (rightClick && e.getHand() == EquipmentSlot.HAND && lobby.isSelector(e.getItem())) {
            KitMenu.open(plugin, p, 0);
        }
        e.setCancelled(true);   // no doors, buttons, ender pearls, potions, buckets, ...
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (restricted(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (restricted(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (e.getRemover() instanceof Player p && restricted(p)) {
            e.setCancelled(true);
        }
    }

    // ---- damage / hunger -------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !lobby.isLobbyWorld(p.getWorld())) {
            return;
        }
        e.setCancelled(true);   // covers PvP, fall damage, fire, everything
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            p.teleport(lobby.getLobby());
        }
    }

    /** EntityDamageEvent above only covers players; this stops lobby players hitting armor stands, frames, animals... */
    @EventHandler(priority = EventPriority.HIGH)
    public void onHitEntity(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && restricted(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && lobby.isLobbyWorld(p.getWorld())) {
            e.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    // ---- items -----------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent e) {
        if (restricted(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && restricted(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        if (restricted(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (restricted(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof KitMenu) {
            return; // the kit menu handles (and cancels) its own clicks
        }
        if (e.getWhoClicked() instanceof Player p && restricted(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && restricted(p)) {
            e.setCancelled(true);
        }
    }

    // ---- potions / projectiles ---------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player p && restricted(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSplash(PotionSplashEvent e) {
        if (lobby.isLobbyWorld(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLingering(LingeringPotionSplashEvent e) {
        if (lobby.isLobbyWorld(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCloud(AreaEffectCloudApplyEvent e) {
        if (lobby.isLobbyWorld(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    // ---- world protection --------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (lobby.isLobbyWorld(e.getLocation().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (lobby.isLobbyWorld(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBurn(BlockBurnEvent e) {
        if (lobby.isLobbyWorld(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onIgnite(BlockIgniteEvent e) {
        if (lobby.isLobbyWorld(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (lobby.isLobbyWorld(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!lobby.isLobbyWorld(e.getLocation().getWorld())) {
            return;
        }
        switch (e.getSpawnReason()) {
            case CUSTOM, COMMAND, SPAWNER_EGG, DISPENSE_EGG -> { }
            default -> e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onWeather(WeatherChangeEvent e) {
        World world = e.getWorld();
        if (e.toWeatherState() && lobby.isLobbyWorld(world)) {
            e.setCancelled(true);
        }
    }
}
