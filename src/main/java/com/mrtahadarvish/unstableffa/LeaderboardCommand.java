package com.mrtahadarvish.unstableffa;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /leaderboard [kills|deaths|coins] - top 10 players. */
public final class LeaderboardCommand implements TabExecutor {

    private static final List<String> STATS = List.of("kills", "deaths", "coins");

    private final UnstableFFA plugin;

    public LeaderboardCommand(UnstableFFA plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String stat = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "kills";
        if (!STATS.contains(stat)) {
            plugin.send(sender, "<red>/leaderboard [kills|deaths|coins]");
            return true;
        }
        List<Map.Entry<UUID, Long>> top = plugin.data().top(stat, 10);
        sender.sendMessage(plugin.parse("<gold><bold>Top 10 - " + stat));
        if (top.isEmpty()) {
            sender.sendMessage(plugin.parse("<gray>Nobody on the board yet - kill another player in an arena to get on it."));
            return true;
        }
        int rank = 1;
        for (Map.Entry<UUID, Long> entry : top) {
            String color = rank == 1 ? "<gold>" : rank == 2 ? "<white>" : rank == 3 ? "<#cd7f32>" : "<gray>";
            sender.sendMessage(plugin.parse(color + "#" + rank + " <white>" + UnstableFFA.esc(plugin.data().getName(entry.getKey()))
                    + " <dark_gray>- " + color + entry.getValue() + " " + stat));
            rank++;
        }
        if (sender instanceof Player p) {
            long mine = stat.equals("coins") ? plugin.data().getCoins(p.getUniqueId()) : plugin.data().getStat(p.getUniqueId(), stat);
            sender.sendMessage(plugin.parse("<gray>You: <white>" + mine + " " + stat));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? STATS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of();
    }
}
