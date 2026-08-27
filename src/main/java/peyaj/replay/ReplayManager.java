package peyaj.replay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import peyaj.PacketUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages race replay recording, storage, and playback.
 */
public class ReplayManager {

    private final JavaPlugin plugin;
    private final Path replaysFolder;
    private final Map<String, List<ReplayData>> replayCache = new ConcurrentHashMap<>();
    private final Map<UUID, ReplayData> activeRecordings = new ConcurrentHashMap<>();
    private final Map<UUID, ReplayPlayback> activePlaybacks = new ConcurrentHashMap<>();

    private static final int MAX_REPLAYS_PER_ARENA = 1;
    private static final int RECORD_INTERVAL_TICKS = 2; // Record every 2 ticks (10fps)

    public ReplayManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.replaysFolder = plugin.getDataFolder().toPath().resolve("replays");
        try {
            Files.createDirectories(replaysFolder);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create replays folder: " + e.getMessage());
        }
        loadAllReplays();
    }

    /**
     * Starts recording a race.
     */
    public ReplayData startRecording(String arenaName) {
        ReplayData replay = new ReplayData(arenaName, System.currentTimeMillis());
        return replay;
    }

    /**
     * Adds a frame to the replay for all active players.
     */
    public void recordFrame(ReplayData replay, int tickNumber, Map<UUID, Location> playerLocations,
            Set<UUID> finishedPlayers, Map<UUID, Integer> checkpoints, Map<UUID, Integer> laps) {
        if (tickNumber % RECORD_INTERVAL_TICKS != 0)
            return; // Only record every N ticks

        ReplayData.ReplayFrame frame = new ReplayData.ReplayFrame(tickNumber);

        for (Map.Entry<UUID, Location> entry : playerLocations.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null)
                continue;

            String playerName = p.getName();
            replay.addPlayer(playerName);

            ReplayData.PlayerFrameData data = new ReplayData.PlayerFrameData(
                    entry.getValue(),
                    finishedPlayers.contains(entry.getKey()),
                    checkpoints.getOrDefault(entry.getKey(), 0),
                    laps.getOrDefault(entry.getKey(), 1));
            frame.addPlayerData(playerName, data);
        }

        replay.addFrame(frame);
    }

    /**
     * Finishes recording and saves the replay.
     */
    public void finishRecording(ReplayData replay, Map<UUID, Long> finishTimesMs) {
        // Add finish times
        for (Map.Entry<UUID, Long> entry : finishTimesMs.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                replay.setFinishTime(p.getName(), entry.getValue());
            }
        }

        saveReplay(replay);
    }

    /**
     * Saves a replay to disk.
     */
    private void saveReplay(ReplayData replay) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Path arenaFolder = replaysFolder.resolve(replay.getArenaName().toLowerCase());
                Files.createDirectories(arenaFolder);

                Path replayFile = arenaFolder.resolve(replay.getTimestamp() + ".replay");

                try (ObjectOutputStream oos = new ObjectOutputStream(
                        new BufferedOutputStream(Files.newOutputStream(replayFile)))) {
                    oos.writeObject(replay);
                }

                // Cache it
                replayCache.computeIfAbsent(replay.getArenaName().toLowerCase(), k -> new ArrayList<>())
                        .add(replay);

                // Cleanup old replays
                cleanupOldReplays(replay.getArenaName());

                plugin.getLogger().info("Saved replay for " + replay.getArenaName() +
                        " (" + replay.getFrames().size() + " frames)");

            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save replay: " + e.getMessage());
            }
        });
    }

    /**
     * Removes old replays beyond the limit.
     */
    private void cleanupOldReplays(String arenaName) {
        List<ReplayData> replays = replayCache.get(arenaName.toLowerCase());
        if (replays == null || replays.size() <= MAX_REPLAYS_PER_ARENA)
            return;

        // Sort by timestamp, oldest first
        replays.sort(Comparator.comparingLong(ReplayData::getTimestamp));

        while (replays.size() > MAX_REPLAYS_PER_ARENA) {
            ReplayData oldest = replays.remove(0);
            try {
                Path arenaFolder = replaysFolder.resolve(arenaName.toLowerCase());
                Path replayFile = arenaFolder.resolve(oldest.getTimestamp() + ".replay");
                Files.deleteIfExists(replayFile);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to delete old replay: " + e.getMessage());
            }
        }
    }

    /**
     * Loads all replays from disk.
     */
    private void loadAllReplays() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!Files.exists(replaysFolder))
                    return;

                Files.list(replaysFolder)
                        .filter(Files::isDirectory)
                        .forEach(arenaFolder -> {
                            String arenaName = arenaFolder.getFileName().toString();
                            try {
                                List<ReplayData> replays = Files.list(arenaFolder)
                                        .filter(f -> f.toString().endsWith(".replay"))
                                        .map(this::loadReplayFile)
                                        .filter(Objects::nonNull)
                                        .sorted(Comparator.comparingLong(ReplayData::getTimestamp).reversed())
                                        .limit(MAX_REPLAYS_PER_ARENA)
                                        .collect(Collectors.toList());
                                replayCache.put(arenaName, replays);
                            } catch (IOException e) {
                                plugin.getLogger().warning("Failed to load replays for " + arenaName);
                            }
                        });

            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load replays: " + e.getMessage());
            }
        });
    }

    private ReplayData loadReplayFile(Path file) {
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            return (ReplayData) ois.readObject();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load replay file: " + file.getFileName());
            return null;
        }
    }

    /**
     * Gets list of replays for an arena.
     */
    public List<ReplayData> getReplays(String arenaName) {
        return replayCache.getOrDefault(arenaName.toLowerCase(), Collections.emptyList());
    }

    /**
     * Starts replay playback for a player.
     */
    public boolean startPlayback(Player viewer, ReplayData replay, World world) {
        if (activePlaybacks.containsKey(viewer.getUniqueId())) {
            viewer.sendMessage(Component.text("Stai già guardando un replay! Prima usa /race replay stop.",
                    NamedTextColor.RED));
            return false;
        }

        if (replay.getFrames().isEmpty()) {
            viewer.sendMessage(Component.text("Questo replay non contiene dati!", NamedTextColor.RED));
            return false;
        }

        // Put player in spectator mode
        viewer.setGameMode(GameMode.SPECTATOR);

        // Teleport to first frame location
        ReplayData.ReplayFrame firstFrame = replay.getFrames().get(0);
        for (ReplayData.PlayerFrameData data : firstFrame.getPlayerData().values()) {
            viewer.teleport(data.toLocation(world));
            break;
        }

        ReplayPlayback playback = new ReplayPlayback(viewer, replay, world);
        activePlaybacks.put(viewer.getUniqueId(), playback);
        playback.start();

        viewer.sendMessage(Component.text("▶ Replay avviato! Usa /race replay stop per uscire.", NamedTextColor.GREEN));
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);

        return true;
    }

    /**
     * Stops replay playback for a player.
     */
    public void stopPlayback(Player viewer) {
        ReplayPlayback playback = activePlaybacks.remove(viewer.getUniqueId());
        if (playback != null) {
            playback.stop();
            viewer.setGameMode(GameMode.ADVENTURE);
            viewer.sendMessage(Component.text("⏹ Replay interrotto.", NamedTextColor.YELLOW));
        }
    }

    /**
     * Checks if a player is watching a replay.
     */
    public boolean isWatchingReplay(UUID uuid) {
        return activePlaybacks.containsKey(uuid);
    }

    /**
     * Handles the playback of a replay.
     */
    private class ReplayPlayback {
        private final Player viewer;
        private final ReplayData replay;
        private final World world;
        private final Map<String, Integer> fakeEntityIds = new HashMap<>();
        private BukkitTask playbackTask;
        private int currentFrame = 0;

        ReplayPlayback(Player viewer, ReplayData replay, World world) {
            this.viewer = viewer;
            this.replay = replay;
            this.world = world;
        }

        void start() {
            // Spawn fake boats for each player
            ReplayData.ReplayFrame firstFrame = replay.getFrames().get(0);
            for (Map.Entry<String, ReplayData.PlayerFrameData> entry : firstFrame.getPlayerData().entrySet()) {
                Location loc = entry.getValue().toLocation(world);
                int entityId = PacketUtils.spawnFakeBoat(viewer, loc, UUID.randomUUID());
                fakeEntityIds.put(entry.getKey(), entityId);
            }

            // Start playback loop
            playbackTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!viewer.isOnline() || currentFrame >= replay.getFrames().size()) {
                        // Replay finished or player offline
                        if (viewer.isOnline()) {
                            viewer.sendMessage(Component.text("⏹ Replay terminato!", NamedTextColor.GOLD));
                        }
                        stopPlayback(viewer);
                        return;
                    }

                    ReplayData.ReplayFrame frame = replay.getFrames().get(currentFrame);

                    // Move all fake boats
                    for (Map.Entry<String, ReplayData.PlayerFrameData> entry : frame.getPlayerData().entrySet()) {
                        Integer entityId = fakeEntityIds.get(entry.getKey());
                        if (entityId != null) {
                            Location loc = entry.getValue().toLocation(world);
                            PacketUtils.moveFakeBoat(viewer, entityId, loc);
                        }
                    }

                    // Update action bar with progress
                    int progress = (currentFrame * 100) / replay.getFrames().size();
                    viewer.sendActionBar(Component.text(
                            "§b▶ Replay: " + progress + "% §7| §e" + replay.getPlayerNames().size() + " piloti"));

                    currentFrame++;
                }
            }.runTaskTimer(plugin, 0L, RECORD_INTERVAL_TICKS);
        }

        void stop() {
            if (playbackTask != null) {
                playbackTask.cancel();
            }

            // Destroy all fake entities
            for (int entityId : fakeEntityIds.values()) {
                PacketUtils.destroyFakeEntity(viewer, entityId);
            }
            fakeEntityIds.clear();
        }
    }
}
