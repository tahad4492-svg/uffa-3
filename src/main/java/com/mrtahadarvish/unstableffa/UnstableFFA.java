package com.mrtahadarvish.unstableffa;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MrTahaDarvish's UnstableFFA - main class.
 */
public final class UnstableFFA extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private DataManager data;
    private KitManager kits;
    private WorldManager worlds;
    private LobbyManager lobby;
    private ArenaManager arenas;
    private PortalManager portals;
    private ShopManager shop;
    private SidebarManager sidebar;
    private HologramManager holograms;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();

        HeadUtil.init(this);
        data = new DataManager(this);
        kits = new KitManager(this);
        shop = new ShopManager(this);
        worlds = new WorldManager(this);   // loads/creates saved worlds first
        lobby = new LobbyManager(this);
        arenas = new ArenaManager(this);
        portals = new PortalManager(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new LobbyListener(this), this);
        pm.registerEvents(new KitMenuListener(this), this);
        pm.registerEvents(new ShopListener(), this);
        pm.registerEvents(new ArenaListener(this), this);
        pm.registerEvents(new PortalListener(this), this);
        pm.registerEvents(new CombatListener(this), this);

        bind("ufa", new UfaCommand(this));
        bind("kit", new KitCommand(this));
        bind("coins", new CoinsCommand(this));
        bind("lobby", new LobbyCommand(this));
        bind("shop", new ShopCommand(this));
        bind("playerhead", new PlayerHeadCommand(this));
        bind("leaderboard", new LeaderboardCommand(this));

        arenas.startTimer();
        sidebar = new SidebarManager(this);
        sidebar.start();
        holograms = new HologramManager(this);
        holograms.start();
        getLogger().info("MrTahaDarvish's UnstableFFA enabled.");
    }

    @Override
    public void onDisable() {
        if (holograms != null) {
            holograms.removeAll();
        }
        // put every arena back to its original state so the map is never left damaged
        if (arenas != null) {
            arenas.restoreAllNow();
        }
        if (data != null) {
            data.saveNow();
        }
    }

    /** Brings an older config.yml up to date (new keys are added, the reset time becomes 15 minutes). */
    private void migrateConfig() {
        if (getConfig().getInt("config-version", 1) < 2) {
            getConfig().set("arena.reset-interval-minutes", 15);
            getConfig().set("config-version", 2);
        }
        getConfig().options().copyDefaults(true);   // adds any missing keys (shop, scoreboard...)
        saveConfig();
    }

    private void bind(String name, TabExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command missing from plugin.yml: " + name);
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);
    }

    // ---- accessors -------------------------------------------------------

    public DataManager data() { return data; }
    public KitManager kits() { return kits; }
    public WorldManager worlds() { return worlds; }
    public LobbyManager lobby() { return lobby; }
    public ArenaManager arenas() { return arenas; }
    public PortalManager portals() { return portals; }
    public ShopManager shop() { return shop; }
    public HologramManager holograms() { return holograms; }

    // ---- messaging helpers -------------------------------------------------

    public Component parse(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    /** Escapes MiniMessage tags in user supplied text (names etc.). */
    public static String esc(String text) {
        return MM.escapeTags(text);
    }

    public void send(CommandSender to, String miniMessage) {
        to.sendMessage(MM.deserialize(getConfig().getString("prefix", "") + miniMessage));
    }

    /** Finds an online player or an offline player that has joined before. */
    public OfflinePlayer findPlayer(String name) {
        OfflinePlayer online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }
}
