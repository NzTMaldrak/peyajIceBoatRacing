package peyaj;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import peyaj.commands.RaceTabCompleter;
import peyaj.cosmetics.EditMode;
import peyaj.cosmetics.TrailType;
import peyaj.data.GhostData;
import peyaj.data.PlayerSessionData;
import peyaj.hologram.HologramManager;
import peyaj.integration.DiscordWebhook;
import peyaj.integration.IceBoatPlaceholders;
import peyaj.integration.AntiCheatHook;
import peyaj.integration.RaceClientHook;
import peyaj.replay.ReplayManager;

import peyaj.utils.AsyncIO;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IceBoatRacing extends JavaPlugin {

    private final Map<String, RaceArena> arenas = new HashMap<>();
    private final Map<UUID, String> playerArenaMap = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSessionData> playerSessions = new HashMap<>();

    // --- EDITOR & INPUT DATA ---
    public final Map<UUID, String> editorArena = new HashMap<>();
    public final Map<UUID, EditMode> editorMode = new HashMap<>();
    public final Map<UUID, String> activeVisualizers = new HashMap<>();
    public final Map<UUID, String> inputMode = new HashMap<>();

    // --- COSMETICS DATA ---
    private final Map<UUID, Material> playerCagePreference = new HashMap<>();
    private final Map<UUID, TrailType> playerTrailPreference = new HashMap<>();

    // --- VOTING DATA ---
    public boolean isVoting = false;
    public int votingTimeRemaining = 0;
    private BukkitTask votingTask;
    public final Map<UUID, String> playerVotes = new HashMap<>();

    // --- CONFIGS ---
    private File messagesFile;
    private FileConfiguration messagesConfig;
    private File statsFile;
    private FileConfiguration statsConfig;
    private File arenasFile;
    private FileConfiguration arenasConfig;
    private File scoreboardFile;
    private FileConfiguration scoreboardConfig;

    // --- MANAGERS ---
    public GUIManager guiManager;
    public ReplayManager replayManager;
    public DiscordWebhook discordWebhook;
    public HologramManager hologramManager;
    public AntiCheatHook antiCheatHook;
    public RaceClientHook raceClientHook;

    // --- SETTINGS ---
    public double checkpointRadius = 25.0;
    public String discordWebhookUrl = "";

    public boolean musicEnabled = true;
    public String musicSound = "minecraft:coconutmallmariokartwiiostfourone";
    public int musicDuration = 180;
    public float musicVolume = 10000.0f;
    public float musicPitch = 1.0f;

    public boolean rewardsEnabled = false;
    public int rewardsMinPlayers = 2;
    public final Map<Integer, List<String>> rewardCommands = new HashMap<>();

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().checkForUpdates(false).bStats(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        PacketEvents.getAPI().init();
        hologramManager = new HologramManager(this);

        sendStartupBanner();

        saveDefaultConfig();
        checkConfigUpdates();
        loadConfigSettings();
        loadMessages();
        loadScoreboard();
        loadStats();

        // Load Arenas from dedicated file
        loadArenasConfig();

        if (getConfig().contains("arenas")) {
            getLogger().info("Migrating arenas from config.yml to arenas.yml...");
            arenasConfig.set("arenas", getConfig().getConfigurationSection("arenas"));
            getConfig().set("arenas", null);
            saveConfig();
            saveArenasConfig();
            getLogger().info("Migration complete!");
        }

        loadArenas();

        // Initialize managers
        guiManager = new GUIManager(this);
        replayManager = new ReplayManager(this);
        discordWebhook = new DiscordWebhook(this);
        antiCheatHook = new AntiCheatHook(this);
        raceClientHook = new RaceClientHook(this);

        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(raceClientHook, this);

        // Register commands with tab completer
        RaceCommand cmd = new RaceCommand(this);
        RaceTabCompleter tabCompleter = new RaceTabCompleter(this);

        if (getCommand("race") != null) {
            Objects.requireNonNull(getCommand("race")).setExecutor(cmd);
            Objects.requireNonNull(getCommand("race")).setTabCompleter(tabCompleter);
        }
        if (getCommand("iceboat") != null) {
            Objects.requireNonNull(getCommand("iceboat")).setExecutor(cmd);
            Objects.requireNonNull(getCommand("iceboat")).setTabCompleter(tabCompleter);
        }
        if (getCommand("checkpoint") != null) {
            Objects.requireNonNull(getCommand("checkpoint")).setExecutor(cmd);
        }
        if (getCommand("racequit") != null) {
            Objects.requireNonNull(getCommand("racequit")).setExecutor(cmd);
        }

        // Initialize bStats Metrics
        int pluginId = 33031;
        Metrics metrics = new Metrics(this, pluginId);
        metrics.addCustomChart(new SimplePie("total_arenas", () -> String.valueOf(arenas.size())));

        getServer().getPluginManager().registerEvents(new RaceListener(this), this);

        // A /reload does not fire PlayerJoinEvent. Remove any IceBoat sidebar
        // left on players that are online but are not currently in an arena.
        Bukkit.getScheduler().runTask(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduleRaceScoreboardCleanup(player);
            }
        });

        // Main game tick
        new BukkitRunnable() {
            @Override
            public void run() {
                for (RaceArena arena : arenas.values())
                    arena.tick();
            }
        }.runTaskTimer(this, 0L, 1L);

        // Enforce scoreboard ownership continuously. Event-only cleanup is not
        // sufficient when a stale client/sidebar survives a reload or when an
        // arena association becomes inconsistent.
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    clearRaceScoreboardOutsideRaceContext(player);
                }
            }
        }.runTaskTimer(this, 20L, 20L);

        // Visualizer tick
        new BukkitRunnable() {
            @Override
            public void run() {
                Utils.tickVisualizers(IceBoatRacing.this);
            }
        }.runTaskTimer(this, 0L, 10L);

        // Register PlaceholderAPI expansion if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new IceBoatPlaceholders(this).register();
            getLogger().info("PlaceholderAPI expansion registered!");
        }
    }

    @Override
    public void onDisable() {
        for (RaceArena arena : arenas.values()) {
            arena.stopRace();
        }
        if (antiCheatHook != null) {
            antiCheatHook.clearAll();
        }
        if (raceClientHook != null) {
            raceClientHook.close();
        }
        if (hologramManager != null) {
            hologramManager.removeAll();
        }
        if (arenasConfig != null) {
            saveArenas();
        }
        if (statsConfig != null) {
            saveStats();
        }
        try {
            PacketEvents.getAPI().terminate();
        } catch (Exception ignored) {
        }
    }

    public void reload() {
        reloadConfig();
        loadConfigSettings();
        loadMessages();
        loadScoreboard();
        loadArenasConfig();
        for (RaceArena arena : arenas.values())
            arena.refreshLobbyScoreboard();
        getLogger().info("Configuration reloaded.");
    }

    // --- VOTING LOGIC ---

    public void startVotingRound(int durationSeconds) {
        if (isVoting)
            return;
        isVoting = true;
        votingTimeRemaining = durationSeconds;
        playerVotes.clear();

        Bukkit.broadcast(Component.text("---------------------------------------", NamedTextColor.GREEN));
        Bukkit.broadcast(Component.text(" 🗳️ La votazione della mappa è iniziata!", NamedTextColor.YELLOW));
        Bukkit.broadcast(Component.text(" Usa /race vote per scegliere la prossima mappa!", NamedTextColor.AQUA));
        Bukkit.broadcast(Component.text("---------------------------------------", NamedTextColor.GREEN));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        }

        votingTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (votingTimeRemaining <= 0) {
                    endVotingRound();
                    cancel();
                    return;
                }

                if (votingTimeRemaining == 30 || votingTimeRemaining == 10 || votingTimeRemaining <= 5) {
                    Bukkit.broadcast(
                            Component.text("La votazione termina tra " + votingTimeRemaining + "s...", NamedTextColor.GRAY));
                }

                votingTimeRemaining--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    public void endVotingRound() {
        isVoting = false;
        if (votingTask != null)
            votingTask.cancel();

        Map<String, Integer> counts = new HashMap<>();
        for (String arena : playerVotes.values()) {
            counts.put(arena, counts.getOrDefault(arena, 0) + 1);
        }

        String winner = null;
        int max = -1;

        if (counts.isEmpty()) {
            List<String> keys = new ArrayList<>(arenas.keySet());
            if (!keys.isEmpty())
                winner = keys.get(new Random().nextInt(keys.size()));
        } else {
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (entry.getValue() > max) {
                    max = entry.getValue();
                    winner = entry.getKey();
                }
            }
        }

        if (winner == null || !arenas.containsKey(winner)) {
            Bukkit.broadcast(Component.text("Votazione terminata. Nessuna arena disponibile.", NamedTextColor.RED));
            return;
        }

        RaceArena winningArena = arenas.get(winner);
        Bukkit.broadcast(Component.text("---------------------------------------", NamedTextColor.GREEN));
        Bukkit.broadcast(Component.text(" 🏆 Votazione terminata!", NamedTextColor.GOLD));
        Bukkit.broadcast(Component.text(" Prossima mappa: " + winningArena.getName(), NamedTextColor.AQUA));
        Bukkit.broadcast(Component.text("---------------------------------------", NamedTextColor.GREEN));

        for (UUID uuid : playerVotes.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && !isRacer(uuid)) {
                winningArena.addPlayer(p);
            }
        }
    }

    public void castVote(Player p, String arenaName) {
        if (!isVoting) {
            p.sendMessage(Component.text("Non c'è alcuna votazione in corso.", NamedTextColor.RED));
            return;
        }
        playerVotes.put(p.getUniqueId(), arenaName);
        p.sendMessage(Component.text("Hai votato per " + arenaName, NamedTextColor.GREEN));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    public int getVoteCount(String arenaName) {
        int count = 0;
        for (String s : playerVotes.values()) {
            if (s.equals(arenaName))
                count++;
        }
        return count;
    }

    private void checkConfigUpdates() {
        if (!getConfig().contains("victory.rewards")) {
            getConfig().set("victory.rewards.enabled", false);
            getConfig().set("victory.rewards.min-players", 2);
            getConfig().set("victory.rewards.1", Arrays.asList("eco give %player% 500"));
            getConfig().set("victory.rewards.2", Arrays.asList("eco give %player% 250"));
            getConfig().set("victory.rewards.3", Arrays.asList("eco give %player% 100"));
            saveConfig();
            getLogger().info("Updated config.yml with new victory.rewards section.");
        }
    }

    // --- CONFIG HELPERS ---
    private void loadArenasConfig() {
        arenasFile = new File(getDataFolder(), "arenas.yml");
        if (!arenasFile.exists()) {
            try {
                arenasFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        arenasConfig = YamlConfiguration.loadConfiguration(arenasFile);
    }

    public void saveArenasConfig() {
        if (arenasConfig != null && arenasFile != null) {
            AsyncIO.saveConfigAsync(arenasConfig, arenasFile, this);
        }
    }

    private void loadMessages() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists())
            saveResource("messages.yml", false);
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Aggiorna automaticamente soltanto il file inglese originale. Un file
        // personalizzato non viene toccato; prima della migrazione viene conservata
        // anche una copia recuperabile.
        boolean stockEnglishMessages = "&cNo permission.".equals(messagesConfig.getString("no-permission"))
                && "&eStopped spectating.".equals(messagesConfig.getString("spectator-left"))
                && "&a&lGO!".equals(messagesConfig.getString("race-started"))
                && "&6&lFINISHED!".equals(messagesConfig.getString("finished-title"));
        if (stockEnglishMessages) {
            try {
                File backup = new File(getDataFolder(), "messages-english-backup.yml");
                Files.copy(messagesFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                saveResource("messages.yml", true);
                messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
                getLogger().info("Messaggi predefiniti migrati automaticamente in italiano.");
            } catch (IOException e) {
                getLogger().warning("Impossibile migrare messages.yml in italiano: " + e.getMessage());
            }
        }
        boolean updated = false;
        if (!messagesConfig.contains("arena-full")) {
            messagesConfig.set("arena-full", "&cQuesta gara è piena ({max} giocatori).");
            updated = true;
        }
        if (!messagesConfig.contains("ready-enabled")) {
            messagesConfig.set("ready-enabled", "&aSei pronto!");
            updated = true;
        }
        if (!messagesConfig.contains("ready-disabled")) {
            messagesConfig.set("ready-disabled", "&eNon sei più pronto.");
            updated = true;
        }
        if (!messagesConfig.contains("cannot-race-vanished")) {
            messagesConfig.set("cannot-race-vanished",
                    "&cDisattiva il vanish prima di entrare in una gara o come spettatore.");
            updated = true;
        }
        if (updated) {
            try {
                messagesConfig.save(messagesFile);
            } catch (IOException e) {
                getLogger().warning("Impossibile aggiornare messages.yml: " + e.getMessage());
            }
        }
    }

    private void loadScoreboard() {
        scoreboardFile = new File(getDataFolder(), "scoreboard.yml");
        if (!scoreboardFile.exists())
            saveResource("scoreboard.yml", false);
        scoreboardConfig = YamlConfiguration.loadConfiguration(scoreboardFile);
    }

    public String getScoreboardString(String path, String fallback) {
        return scoreboardConfig == null ? fallback : scoreboardConfig.getString(path, fallback);
    }

    public List<String> getScoreboardLines(String path, List<String> fallback) {
        if (scoreboardConfig == null || !scoreboardConfig.isList(path))
            return fallback;
        List<String> lines = scoreboardConfig.getStringList(path);
        return lines.isEmpty() ? fallback : lines;
    }

    public Component getMessage(String key) {
        String prefix = messagesConfig.getString("prefix", "&b[IceBoat] ");
        String msg = messagesConfig.getString(key, "&cMissing message: " + key);
        return LegacyComponentSerializer.legacyAmpersand().deserialize(normalizeMessagePrefix(prefix) + msg);
    }

    private String normalizeMessagePrefix(String prefix) {
        if (prefix == null || prefix.isEmpty())
            return "";
        // Ignore trailing legacy formatting codes when checking whether the visible
        // prefix already contains a separator (for example "VoxelKart &r").
        String visibleEnd = prefix.replaceFirst("(?i)(?:&[0-9A-FK-OR])+$", "");
        return !visibleEnd.isEmpty() && Character.isWhitespace(visibleEnd.charAt(visibleEnd.length() - 1))
                ? prefix
                : prefix + " ";
    }

    public String getRawMessage(String key) {
        return messagesConfig.getString(key, key);
    }

    private void loadStats() {
        statsFile = new File(getDataFolder(), "stats.yml");
        if (!statsFile.exists()) {
            try {
                statsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
    }

    public void saveStats() {
        if (statsConfig != null && statsFile != null) {
            AsyncIO.saveConfigAsync(statsConfig, statsFile, this);
        }
    }

    public void incrementStat(UUID uuid, String stat) {
        String path = uuid.toString() + "." + stat;
        int current = statsConfig.getInt(path, 0);
        statsConfig.set(path, current + 1);
        saveStats();
    }

    public int getStat(UUID uuid, String stat) {
        return statsConfig.getInt(uuid.toString() + "." + stat, 0);
    }

    // --- COSMETICS ---
    public Material getPlayerCagePreference(UUID uuid) {
        return playerCagePreference.getOrDefault(uuid, Material.GLASS);
    }

    public void setPlayerCagePreference(UUID uuid, Material mat) {
        playerCagePreference.put(uuid, mat);
    }

    public TrailType getPlayerTrailPreference(UUID uuid) {
        return playerTrailPreference.getOrDefault(uuid, TrailType.SMOKE);
    }

    public void setPlayerTrailPreference(UUID uuid, TrailType trail) {
        playerTrailPreference.put(uuid, trail);
    }

    // --- ARENA MANAGEMENT ---
    public RaceArena getArena(String name) {
        return arenas.get(name.toLowerCase());
    }

    public Map<String, RaceArena> getArenas() {
        return arenas;
    }

    public void addArena(String name, RaceArena arena) {
        arenas.put(name.toLowerCase(), arena);
    }

    public void removeArena(String name) {
        RaceArena arena = arenas.remove(name.toLowerCase());
        if (arena != null) {
            arena.stopRace();
            arena.deleteLeaderboardHologram();
        }
    }

    public RaceArena getPlayerArena(UUID uuid) {
        String name = playerArenaMap.get(uuid);
        return (name != null) ? arenas.get(name.toLowerCase(Locale.ROOT)) : null;
    }

    public void setPlayerArena(UUID uuid, String arenaName) {
        playerArenaMap.put(uuid, arenaName);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && antiCheatHook != null)
            antiCheatHook.exempt(player);
    }

    public boolean isPlayerVanished(Player player) {
        if (player == null)
            return false;
        if (player.isInvisible())
            return true;
        for (org.bukkit.metadata.MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean())
                return true;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId()) && !viewer.canSee(player))
                return true;
        }
        return false;
    }

    public void removePlayerFromArenaMap(UUID uuid) {
        playerArenaMap.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && raceClientHook != null)
            raceClientHook.disableRacePhysics(player);
        if (antiCheatHook != null)
            antiCheatHook.releaseLater(uuid);
    }

    public boolean isRacer(UUID uuid) {
        return playerArenaMap.containsKey(uuid);
    }

    /** Saves state only once, before the plugin replaces inventory/scoreboard/game mode. */
    public void capturePlayerState(Player player) {
        // Non usare mai come stato originale una scoreboard IceBoat rimasta da una
        // sessione precedente.
        if (isIceBoatScoreboard(player.getScoreboard())) {
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
        playerSessions.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerSessionData(player));
    }

    /** Restores and consumes the saved state so a later session gets a fresh snapshot. */
    public void restorePlayerState(Player player) {
        PlayerSessionData session = playerSessions.remove(player.getUniqueId());
        Scoreboard targetScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        if (session != null) {
            targetScoreboard = session.getScoreboard();
            session.restore(player);
        }
        targetScoreboard = getSafeRestoredScoreboard(targetScoreboard);
        player.setScoreboard(targetScoreboard);
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        player.setFlying(false);
        player.setAllowFlight(false);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.showEntity(this, player);
        }
        ensureRaceScoreboardIsGone(player, targetScoreboard);
    }

    public void restorePlayerScoreboard(Player player) {
        PlayerSessionData session = playerSessions.get(player.getUniqueId());
        Scoreboard targetScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        if (session != null) {
            targetScoreboard = session.getScoreboard();
        }
        targetScoreboard = getSafeRestoredScoreboard(targetScoreboard);
        player.setScoreboard(targetScoreboard);
        ensureRaceScoreboardIsGone(player, targetScoreboard);
    }

    private boolean isIceBoatScoreboard(Scoreboard scoreboard) {
        if (scoreboard == null)
            return false;
        if (scoreboard.getObjective("IceRace") != null)
            return true;

        org.bukkit.scoreboard.Objective lobbyObjective = scoreboard.getObjective("Lobby");
        if (lobbyObjective != null) {
            String lobbyTitle = normalizedScoreboardTitle(lobbyObjective.displayName());
            String configuredLobbyTitle = normalizedScoreboardTitle(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(
                            getScoreboardString("lobby.title", "&b&lVoxelKart")));
            if ((!configuredLobbyTitle.isEmpty() && lobbyTitle.equals(configuredLobbyTitle))
                    || hasLegacyScoreboardTitle(lobbyTitle)) {
                return true;
            }
        }

        // Riconosce anche scoreboard create da vecchie versioni del plugin, come
        // quella con titolo "-- Boat Race --".
        for (org.bukkit.scoreboard.Objective objective : scoreboard.getObjectives()) {
            if (hasLegacyScoreboardTitle(normalizedScoreboardTitle(objective.displayName())))
                return true;
        }
        return false;
    }

    private String normalizedScoreboardTitle(Component title) {
        return PlainTextComponentSerializer.plainText().serialize(title)
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private boolean hasLegacyScoreboardTitle(String normalizedTitle) {
        return normalizedTitle.contains("boatrace") || normalizedTitle.contains("iceboatracing");
    }

    private Scoreboard getSafeRestoredScoreboard(Scoreboard scoreboard) {
        return isIceBoatScoreboard(scoreboard)
                ? Bukkit.getScoreboardManager().getNewScoreboard()
                : scoreboard;
    }

    /**
     * Removes a stale IceBoat sidebar unless the player is actually in the
     * pre-lobby, race, or spectator audience of an arena. The context check is
     * repeated by every delayed pass so joining while cleanup is pending cannot
     * remove a legitimate scoreboard.
     */
    public void scheduleRaceScoreboardCleanup(Player player) {
        clearRaceScoreboardOutsideRaceContext(player);
        for (long delay : new long[] { 1L, 10L, 40L }) {
            Bukkit.getScheduler().runTaskLater(this,
                    () -> clearRaceScoreboardOutsideRaceContext(player), delay);
        }
    }

    private boolean shouldShowRaceScoreboard(UUID uuid) {
        RaceArena arena = getPlayerArena(uuid);
        return arena != null && arena.isScoreboardAudience(uuid);
    }

    private void clearRaceScoreboardOutsideRaceContext(Player player) {
        if (!player.isOnline() || shouldShowRaceScoreboard(player.getUniqueId()))
            return;
        if (isIceBoatScoreboard(player.getScoreboard())) {
            PlayerSessionData session = playerSessions.get(player.getUniqueId());
            Scoreboard targetScoreboard = session != null
                    ? session.getScoreboard()
                    : Bukkit.getScoreboardManager().getMainScoreboard();
            player.setScoreboard(getSafeRestoredScoreboard(targetScoreboard));
        }
    }

    private void ensureRaceScoreboardIsGone(Player player, Scoreboard restoredScoreboard) {
        Scoreboard safeScoreboard = getSafeRestoredScoreboard(restoredScoreboard);
        for (long delay : new long[] { 1L, 10L }) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline() && !shouldShowRaceScoreboard(player.getUniqueId())
                        && isIceBoatScoreboard(player.getScoreboard())) {
                    player.setScoreboard(safeScoreboard);
                }
            }, delay);
        }
    }

    // --- SAVE LOGIC (ARENAS.YML) ---
    public void saveArenas() {
        getConfig().set("settings.checkpoint-radius", checkpointRadius);
        getConfig().set("settings.discord-webhook-url", discordWebhookUrl);

        getConfig().set("music.enabled", musicEnabled);
        getConfig().set("music.sound-name", musicSound);
        getConfig().set("music.loop-duration-seconds", musicDuration);
        getConfig().set("music.volume", musicVolume);
        getConfig().set("music.pitch", musicPitch);

        saveConfig();

        arenasConfig.set("arenas", null);
        for (RaceArena arena : arenas.values()) {
            String path = "arenas." + arena.getName();
            arenasConfig.set(path + ".type", arena.getType().name());
            arenasConfig.set(path + ".laps", arena.getTotalLaps());
            arenasConfig.set(path + ".min-players", arena.minPlayers);
            arenasConfig.set(path + ".max-players", arena.maxPlayers);
            arenasConfig.set(path + ".auto-start-delay", arena.autoStartDelay);
            arenasConfig.set(path + ".void-y", arena.voidY);
            arenasConfig.set(path + ".lobby", arena.getLobby());
            arenasConfig.set(path + ".mainlobby", arena.getMainLobby());
            arenasConfig.set(path + ".finish1", arena.getFinishPos1());
            arenasConfig.set(path + ".finish2", arena.getFinishPos2());
            arenasConfig.set(path + ".leaderboard", arena.getLeaderboardLocation());
            arenasConfig.set(path + ".spawns", arena.getSpawns());
            arenasConfig.set(path + ".checkpoints", arena.getCheckpoints());

            if (!arena.bestTimes.isEmpty()) {
                for (Map.Entry<UUID, Long> entry : arena.bestTimes.entrySet()) {
                    arenasConfig.set(path + ".best_times." + entry.getKey().toString(), entry.getValue());
                }
            }
        }
        saveArenasConfig();
    }

    private void loadConfigSettings() {
        this.checkpointRadius = getConfig().getDouble("settings.checkpoint-radius", 25.0);
        this.discordWebhookUrl = getConfig().getString("settings.discord-webhook-url", "");

        this.musicEnabled = getConfig().getBoolean("music.enabled", true);
        this.musicSound = getConfig().getString("music.sound-name", "minecraft:coconutmallmariokartwiiostfourone");
        this.musicDuration = getConfig().getInt("music.loop-duration-seconds", 180);
        this.musicVolume = (float) getConfig().getDouble("music.volume", 10000.0);
        this.musicPitch = (float) getConfig().getDouble("music.pitch", 1.0);

        this.rewardsEnabled = getConfig().getBoolean("victory.rewards.enabled", false);
        this.rewardsMinPlayers = getConfig().getInt("victory.rewards.min-players", 2);
        this.rewardCommands.clear();
        
        if (this.rewardsEnabled) {
            org.bukkit.configuration.ConfigurationSection rewardsSection = getConfig().getConfigurationSection("victory.rewards");
            if (rewardsSection != null) {
                for (String key : rewardsSection.getKeys(false)) {
                    if (key.equals("enabled") || key.equals("min-players")) continue;
                    try {
                        int rank = Integer.parseInt(key);
                        List<String> commands = rewardsSection.getStringList(key);
                        if (commands != null && !commands.isEmpty()) {
                            this.rewardCommands.put(rank, commands);
                        }
                    } catch (NumberFormatException ignored) {
                        // Ignore non-integer keys
                    }
                }
            }
        }
    }

    private void loadArenas() {
        ConfigurationSection section = arenasConfig.getConfigurationSection("arenas");
        if (section == null)
            return;

        for (String key : section.getKeys(false)) {
            if (arenas.containsKey(key.toLowerCase()))
                continue;

            String path = "arenas." + key;
            RaceArena arena = new RaceArena(key, this);

            try {
                arena.setType(peyaj.arena.RaceType.valueOf(arenasConfig.getString(path + ".type", "DEFAULT")));
            } catch (Exception e) {
                arena.setType(peyaj.arena.RaceType.DEFAULT);
            }

            arena.setTotalLaps(arenasConfig.getInt(path + ".laps", 1));
            arena.minPlayers = Math.min(25, Math.max(1, arenasConfig.getInt(path + ".min-players", 2)));
            arena.maxPlayers = Math.min(25,
                    Math.max(arena.minPlayers, arenasConfig.getInt(path + ".max-players", 25)));
            arena.autoStartDelay = arenasConfig.getInt(path + ".auto-start-delay", 30);
            arena.voidY = arenasConfig.getInt(path + ".void-y", -64);

            arena.setLobby(arenasConfig.getLocation(path + ".lobby"));
            arena.setMainLobby(arenasConfig.getLocation(path + ".mainlobby"));
            arena.setLeaderboardLocation(arenasConfig.getLocation(path + ".leaderboard"));

            arena.setFinishLine(
                    arenasConfig.getLocation(path + ".finish1"),
                    arenasConfig.getLocation(path + ".finish2"));

            List<?> loadedSpawns = arenasConfig.getList(path + ".spawns");
            if (loadedSpawns != null)
                for (Object obj : loadedSpawns)
                    if (obj instanceof Location)
                        arena.addSpawn((Location) obj);

            List<?> loadedCheckpoints = arenasConfig.getList(path + ".checkpoints");
            if (loadedCheckpoints != null)
                for (Object obj : loadedCheckpoints)
                    if (obj instanceof Location)
                        arena.addCheckpoint((Location) obj);

            ConfigurationSection timeSection = arenasConfig.getConfigurationSection(path + ".best_times");
            if (timeSection != null) {
                for (String uuidStr : timeSection.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        long time = timeSection.getLong(uuidStr);
                        arena.bestTimes.put(uuid, time);
                    } catch (Exception ignored) {
                    }
                }
            }

            arenas.put(key.toLowerCase(), arena);
            arena.updateLeaderboardHologram();
            getLogger().info("Loaded arena: " + key);
        }
    }

    private void sendStartupBanner() {
        Bukkit.getConsoleSender()
                .sendMessage(Component.text("                                                 ", NamedTextColor.AQUA));
        Bukkit.getConsoleSender()
                .sendMessage(Component.text("  ___   ____  _____ ____   ___    _  _____ ", NamedTextColor.AQUA));
        Bukkit.getConsoleSender()
                .sendMessage(Component.text(" |_ _| / ___|| ____| __ ) / _ \\  / \\|_   _|", NamedTextColor.AQUA));
        Bukkit.getConsoleSender()
                .sendMessage(Component.text("  | | | |    |  _| |  _ \\| | | |/ _ \\ | |  ", NamedTextColor.AQUA));
        Bukkit.getConsoleSender()
                .sendMessage(Component.text("  | | | |___ | |___| |_) | |_| / ___ \\| |  ", NamedTextColor.AQUA));
        Bukkit.getConsoleSender()
                .sendMessage(Component.text(" |___| \\____||_____|____/ \\___/_/   \\_\\_|  ", NamedTextColor.AQUA));
        Bukkit.getConsoleSender()
                .sendMessage(Component.text("                                                 ", NamedTextColor.AQUA));
        String versionInfo = "   v" + getPluginMeta().getVersion() + " by "
                + String.join(", ", getPluginMeta().getAuthors()) + " enabled!";
        Bukkit.getConsoleSender().sendMessage(Component.text(versionInfo, NamedTextColor.GREEN));
        Bukkit.getConsoleSender()
                .sendMessage(Component.text("   Parties, Replays, 17 Trails, PAPI Support", NamedTextColor.YELLOW));
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }
}
