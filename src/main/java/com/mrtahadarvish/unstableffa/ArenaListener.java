package com.mrtahadarvish.unstableffa;

import com.destroystokyo.paper.event.block.TNTPrimeEvent;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.StructureGrowEvent;

/**
 * Records the ORIGINAL state of every block that changes inside a locked
 * arena, so the periodic reset can put the map back exactly as it was.
 * All handlers run at MONITOR (after other plugins), and always BEFORE the
 * server actually changes the block, so getState() is still the original.
 */
public final class ArenaListener implements Listener {

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final UnstableFFA plugin;
    private final ArenaManager arenas;

    public ArenaListener(UnstableFFA plugin) {
        this.plugin = plugin;
        this.arenas = plugin.arenas();
    }

    /** Tracks the block and its 6 neighbours (torches, doors, plants... that may pop off). */
    private void around(Block block) {
        if (!arenas.isTracking(block.getWorld())) {
            return;
        }
        arenas.track(block);
        for (BlockFace face : FACES) {
            arenas.track(block.getRelative(face));
        }
    }

    // ---- block changes ---------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        arenas.track(e.getBlockReplacedState());
        if (e instanceof BlockMultiPlaceEvent multi) {
            // doors, beds, tall plants: every half that was replaced
            for (BlockState state : multi.getReplacedBlockStates()) {
                arenas.track(state);
            }
        }
    }

    /** Doors, trapdoors, levers, buttons, pressure plates, cake... change state without a block event. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUseBlock(PlayerInteractEvent e) {
        if ((e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL)
                && e.getClickedBlock() != null) {
            around(e.getClickedBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        around(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        for (Block block : e.blockList()) {
            around(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        for (Block block : e.blockList()) {
            around(block);
        }
        arenas.track(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent e) {
        arenas.track(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        around(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        arenas.track(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFromTo(BlockFromToEvent e) {
        // water / lava flowing, dragon egg teleporting
        arenas.track(e.getToBlock());
        arenas.track(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        // snow, ice, cobblestone/obsidian from lava, grass/fire/vine spread (BlockSpreadEvent extends this)
        arenas.track(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        arenas.track(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent e) {
        arenas.track(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent e) {
        for (BlockState state : e.getBlocks()) {
            arenas.track(state.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent e) {
        for (BlockState state : e.getBlocks()) {
            arenas.track(state.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent e) {
        arenas.track(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        // falling blocks, endermen, farmland trampling, wither...
        arenas.track(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        BlockFace dir = e.getDirection();
        arenas.track(e.getBlock());
        arenas.track(e.getBlock().getRelative(dir));
        for (Block block : e.getBlocks()) {
            arenas.track(block);
            arenas.track(block.getRelative(dir));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        BlockFace dir = e.getDirection();
        arenas.track(e.getBlock());
        arenas.track(e.getBlock().getRelative(dir));
        for (Block block : e.getBlocks()) {
            arenas.track(block);
            arenas.track(block.getRelative(dir));
            arenas.track(block.getRelative(dir.getOppositeFace()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        arenas.track(e.getBlock());
        arenas.track(e.getBlockClicked());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        arenas.track(e.getBlock());
        arenas.track(e.getBlockClicked());
    }

    // ---- things that can't be restored are simply blocked ---------------------------

    private boolean guarded(Player p) {
        return arenas.isTracking(p.getWorld()) && !plugin.lobby().bypass(p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("arena.block-containers", true) || !guarded(e.getPlayer())) {
            return;
        }
        if (Tag.SHULKER_BOXES.isTagged(e.getClickedBlock().getType())) {
            return;   // shulker boxes are always allowed: open, fill, empty, place and break them
        }
        if (e.getClickedBlock().getState(false) instanceof Container) {
            // block opening the chest, but still allow placing blocks against it
            e.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if ((e.getRightClicked() instanceof ItemFrame || e.getRightClicked() instanceof ArmorStand)
                && guarded(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (guarded(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (e.getRemover() instanceof Player p && guarded(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHitDecoration(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p
                && (e.getEntity() instanceof ItemFrame || e.getEntity() instanceof ArmorStand)
                && guarded(p)) {
            e.setCancelled(true);
        }
    }

    // ---- no natural mobs / rain in arena worlds ----------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!arenas.isArenaWorld(e.getLocation().getWorld())) {
            return;
        }
        switch (e.getSpawnReason()) {
            case CUSTOM, COMMAND, SPAWNER_EGG, DISPENSE_EGG -> { }
            default -> e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        World world = e.getWorld();
        if (e.toWeatherState() && arenas.isArenaWorld(world)) {
            e.setCancelled(true);
        }
    }
}
