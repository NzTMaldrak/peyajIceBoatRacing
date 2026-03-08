package peyaj.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import peyaj.IceBoatRacing;

/**
 * PlaceholderAPI expansion for IceBoatRacing.
 * Provides placeholders: %iceboat_wins%, %iceboat_races%, %iceboat_winrate%,
 * %iceboat_best_time_<arena>%, %iceboat_current_arena%, %iceboat_title%
 */
public class IceBoatPlaceholders extends PlaceholderExpansion {

    private final IceBoatRacing plugin;

    public IceBoatPlaceholders(IceBoatRacing plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "iceboat";
    }

    @Override
    public @NotNull String getAuthor() {
        return "peyaj";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Don't unregister on reload
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null)
            return "";

        // %iceboat_wins%
        if (params.equalsIgnoreCase("wins")) {
            return String.valueOf(plugin.getStat(offlinePlayer.getUniqueId(), "wins"));
        }

        // %iceboat_races%
        if (params.equalsIgnoreCase("races")) {
            return String.valueOf(plugin.getStat(offlinePlayer.getUniqueId(), "races_played"));
        }

        // %iceboat_winrate%
        if (params.equalsIgnoreCase("winrate")) {
            int wins = plugin.getStat(offlinePlayer.getUniqueId(), "wins");
            int races = plugin.getStat(offlinePlayer.getUniqueId(), "races_played");
            if (races == 0)
                return "0%";
            return (wins * 100 / races) + "%";
        }

        // %iceboat_current_arena%
        if (params.equalsIgnoreCase("current_arena")) {
            var arena = plugin.getPlayerArena(offlinePlayer.getUniqueId());
            return arena != null ? arena.getName() : "None";
        }

        // %iceboat_in_race%
        if (params.equalsIgnoreCase("in_race")) {
            return plugin.isRacer(offlinePlayer.getUniqueId()) ? "Yes" : "No";
        }

        // %iceboat_title%
        if (params.equalsIgnoreCase("title")) {
            return getPlayerTitle(offlinePlayer);
        }

        // %iceboat_best_time_<arena>%
        if (params.toLowerCase().startsWith("best_time_")) {
            String arenaName = params.substring(10);
            var arena = plugin.getArena(arenaName);
            if (arena == null)
                return "N/A";

            Long bestTime = arena.bestTimes.get(offlinePlayer.getUniqueId());
            if (bestTime == null)
                return "N/A";

            return formatTime(bestTime);
        }

        // %iceboat_arena_record_<arena>%
        if (params.toLowerCase().startsWith("arena_record_")) {
            String arenaName = params.substring(13);
            var arena = plugin.getArena(arenaName);
            if (arena == null || arena.bestTimes.isEmpty())
                return "N/A";

            long record = arena.bestTimes.values().stream().mapToLong(Long::longValue).min().orElse(0);
            return formatTime(record);
        }

        // %iceboat_total_arenas%
        if (params.equalsIgnoreCase("total_arenas")) {
            return String.valueOf(plugin.getArenas().size());
        }

        return null;
    }

    private String getPlayerTitle(OfflinePlayer player) {
        int wins = plugin.getStat(player.getUniqueId(), "wins");
        int races = plugin.getStat(player.getUniqueId(), "races_played");

        if (wins >= 100)
            return "§6§lLegend";
        if (wins >= 50)
            return "§c§lChampion";
        if (wins >= 25)
            return "§d§lMaster";
        if (wins >= 10)
            return "§b§lVeteran";
        if (wins >= 5)
            return "§a§lRacer";
        if (races >= 10)
            return "§e§lEnthusiast";
        if (races >= 1)
            return "§7§lNewcomer";
        return "§8§lRookie";
    }

    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        int minutes = (int) (totalSeconds / 60);
        int seconds = (int) (totalSeconds % 60);
        int ms = (int) ((millis % 1000) / 10);
        return String.format("%d:%02d.%02d", minutes, seconds, ms);
    }
}
