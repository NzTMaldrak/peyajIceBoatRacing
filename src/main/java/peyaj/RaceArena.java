package peyaj;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import peyaj.arena.RaceState;
import peyaj.arena.RaceType;
import peyaj.arena.SpectatorMode;
import peyaj.cosmetics.TrailType;
import peyaj.data.GhostData;
import peyaj.replay.ReplayData;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class RaceArena {

    private final String name;
    private final IceBoatRacing plugin;

    private RaceType type = RaceType.DEFAULT;
    private int totalLaps = 1;
    private RaceState state = RaceState.LOBBY;

    // Locations
    private final List<Location> spawns = new ArrayList<>();
    private final List<Location> checkpoints = new ArrayList<>();
    private Location lobby;
    private Location mainLobby;
    private Location leaderboardLocation;

    private Location finishPos1, finishPos2;
    private BoundingBox finishBox;
    private Location finishCenter;

    // Settings
    public int minPlayers = 2;
    public int maxPlayers = 25;
    public int autoStartDelay = 30;
    public int voidY = -64;

    // Runtime Data
    private final Map<UUID, Integer> playerCheckpoints = new HashMap<>();
    private final Map<UUID, Integer> playerLaps = new HashMap<>();
    private final Map<UUID, Long> startTimes = new HashMap<>();
    private final Map<UUID, String> finishTimes = new HashMap<>();
    private final Map<UUID, Long> finishTimesMs = new HashMap<>();
    private final Map<UUID, Boat> playerBoats = new HashMap<>();
    private final Set<UUID> respawningPlayers = new HashSet<>();
    private final Set<UUID> players = new HashSet<>();
    private final Set<UUID> readyPlayers = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final List<UUID> finishOrder = new ArrayList<>();
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final List<Location> glassBlocks = new ArrayList<>();

    // Spectator modes
    private final Map<UUID, SpectatorMode> spectatorModes = new HashMap<>();
    private final Map<UUID, UUID> spectatorTargets = new HashMap<>();

    // Leaderboard Data
    public final Map<UUID, Long> bestTimes = new HashMap<>();

    // Ghost & Replay
    private final Map<UUID, GhostData> currentRecordings = new HashMap<>();
    private GhostData bestGhost = null;
    private int ghostPlaybackTick = 0;
    private Boat visualGhostBoat = null;
    private ReplayData currentReplay = null;

    private final Map<UUID, Map<Integer, Long>> checkpointTimestamps = new HashMap<>();

    // Utils
    private int tickCounter = 0;
    private BukkitTask autoStartTask = null;
    private int lobbyCountdown = -1;
    private int raceStartCountdown = -1;
    private BukkitTask musicTask = null;
    private boolean isTimeTrialMode = false;

    // Elimination mode tracking
    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private int currentLapForElimination = 0;

    // Fake entity IDs for ghosts
    private final Map<UUID, Integer> ghostEntityIds = new HashMap<>();

    public RaceArena(String name, IceBoatRacing plugin) {
        this.name = name;
        this.plugin = plugin;
    }

    // --- GETTERS & SETTERS ---
    public String getName() {
        return name;
    }

    public RaceType getType() {
        return type;
    }

    public void setType(RaceType type) {
        this.type = type;
    }

    public int getTotalLaps() {
        return totalLaps;
    }

    public void setTotalLaps(int laps) {
        this.totalLaps = laps;
    }

    public Location getLobby() {
        return lobby;
    }

    public void setLobby(Location loc) {
        this.lobby = loc;
    }

    public Location getMainLobby() {
        return mainLobby;
    }

    public void setMainLobby(Location loc) {
        this.mainLobby = loc;
    }

    public List<Location> getSpawns() {
        return spawns;
    }

    public List<Location> getCheckpoints() {
        return checkpoints;
    }

    public Location getFinishPos1() {
        return finishPos1;
    }

    public Location getFinishPos2() {
        return finishPos2;
    }

    public BoundingBox getFinishBox() {
        return finishBox;
    }

    public RaceState getState() {
        return state;
    }

    public int getPlayerCount() {
        return players.size();
    }

    public int getReadyCount() {
        return readyPlayers.size();
    }

    public boolean isTimeTrial() {
        return isTimeTrialMode;
    }

    public Location getLeaderboardLocation() {
        return leaderboardLocation;
    }

    public void setLeaderboardLocation(Location loc) {
        this.leaderboardLocation = loc;
        updateLeaderboardHologram();
    }

    public boolean isSpectator(UUID uuid) {
        return spectators.contains(uuid) || eliminatedPlayers.contains(uuid) || finishOrder.contains(uuid);
    }

    public boolean isRespawning(UUID uuid) {
        return respawningPlayers.contains(uuid);
    }

    public boolean isActiveRacer(UUID uuid) {
        return state == RaceState.ACTIVE && players.contains(uuid) && !isSpectator(uuid);
    }

    public List<Player> getSpectatablePlayers() {
        List<Player> result = new ArrayList<>();
        for (UUID uuid : players) {
            if (finishOrder.contains(uuid) || eliminatedPlayers.contains(uuid))
                continue;
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline())
                result.add(player);
        }
        result.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public boolean isFirstPersonSpectating(UUID uuid) {
        return spectatorTargets.containsKey(uuid);
    }

    public void startFirstPersonSpectating(Player spectator, Player target) {
        if (!isSpectator(spectator.getUniqueId()) || target == null || !target.isOnline()
                || !players.contains(target.getUniqueId()) || finishOrder.contains(target.getUniqueId())
                || eliminatedPlayers.contains(target.getUniqueId())) {
            spectator.sendMessage(Component.text("Questo pilota non è più disponibile.", NamedTextColor.RED));
            return;
        }

        spectatorTargets.put(spectator.getUniqueId(), target.getUniqueId());
        spectatorModes.put(spectator.getUniqueId(), SpectatorMode.FOLLOW_PLAYER);
        spectator.getInventory().clear();
        spectator.setGameMode(GameMode.SPECTATOR);
        spectator.setSpectatorTarget(target);
        spectator.sendMessage(Component.text("Stai seguendo " + target.getName()
                + " in prima persona. Premi SHIFT per tornare al volo libero.", NamedTextColor.AQUA));
    }

    public void stopFirstPersonSpectating(Player spectator) {
        if (!isSpectator(spectator.getUniqueId()))
            return;
        spectatorTargets.remove(spectator.getUniqueId());
        spectatorModes.put(spectator.getUniqueId(), SpectatorMode.FREE_FLY);
        configureSpectatorControls(spectator);
        giveSpectatorItems(spectator);
        spectator.sendMessage(Component.text("Sei tornato alla modalità spettatore libera.", NamedTextColor.AQUA));
    }

    public void addSpawn(Location loc) {
        spawns.add(loc);
    }

    public void addCheckpoint(Location loc) {
        checkpoints.add(loc);
    }

    public boolean removeNodeAtBlock(List<Location> list, Location clickedBlockLoc) {
        Iterator<Location> it = list.iterator();
        while (it.hasNext()) {
            Location nodeLoc = it.next();
            if (nodeLoc.getWorld().equals(clickedBlockLoc.getWorld()) &&
                    nodeLoc.getBlockX() == clickedBlockLoc.getBlockX() &&
                    nodeLoc.getBlockZ() == clickedBlockLoc.getBlockZ() &&
                    (nodeLoc.getBlockY() == clickedBlockLoc.getBlockY()
                            || nodeLoc.getBlockY() == clickedBlockLoc.getBlockY() + 1)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public void setFinishLine(Location p1, Location p2) {
        this.finishPos1 = p1;
        this.finishPos2 = p2;
        recalculateFinishBox();
    }

    public void recalculateFinishBox() {
        if (finishPos1 != null && finishPos2 != null && finishPos1.getWorld() != null
                && finishPos1.getWorld().equals(finishPos2.getWorld())) {
            finishBox = BoundingBox.of(finishPos1, finishPos2).expand(0, 10.0, 0);
            finishCenter = finishBox.getCenter().toLocation(finishPos1.getWorld());
        }
    }

    // --- HOLOGRAMS ---
    public void updateLeaderboardHologram() {
        if (leaderboardLocation == null || plugin.getHologramManager() == null)
            return;
        try {
            String holoName = "race_lb_" + name;
            List<String> lines = new ArrayList<>();
            lines.add("&b&l❄ CLASSIFICA " + name.toUpperCase() + " ❄");
            lines.add("&7------------------------");
            List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(bestTimes.entrySet());
            sorted.sort(Map.Entry.comparingByValue());
            int limit = Math.min(sorted.size(), 10);
            for (int i = 0; i < limit; i++) {
                UUID uuid = sorted.get(i).getKey();
                long time = sorted.get(i).getValue();
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                String pName = (op.getName() != null) ? op.getName() : "Sconosciuto";
                String color = (i == 0) ? "&e" : (i == 1) ? "&f" : (i == 2) ? "&6" : "&7";
                lines.add(color + (i + 1) + ". &f" + pName + " &7- &b" + Utils.formatTime(time));
            }
            if (limit == 0)
                lines.add("&7Nessun record presente!");
            lines.add("&7------------------------");
            plugin.getHologramManager().createOrUpdateHologram(holoName, leaderboardLocation, lines);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update leaderboard hologram for " + name + ": " + e.getMessage());
        }
    }

    public void deleteLeaderboardHologram() {
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().removeHologram("race_lb_" + name);
        }
    }

    // --- PLAYER MANAGEMENT ---
    public void addPlayer(Player p) {
        addPlayer(p, false);
    }

    public void addPlayer(Player p, boolean timeTrial) {
        if (plugin.isPlayerVanished(p)) {
            p.sendMessage(plugin.getMessage("cannot-race-vanished"));
            return;
        }
        if (state != RaceState.LOBBY && !timeTrial) {
            addSpectator(p);
            return;
        }
        if (timeTrial && state == RaceState.ACTIVE) {
            p.sendMessage(plugin.getMessage("race-already-active"));
            return;
        }
        if (timeTrial && !players.isEmpty()) {
            p.sendMessage(
                    Component.text("La lobby non è vuota! Entrerai nella gara invece che nella prova a tempo.",
                            NamedTextColor.YELLOW));
            timeTrial = false;
        }

        if (!timeTrial && players.size() >= maxPlayers) {
            p.sendMessage(plugin.getMessage("arena-full").replaceText(
                    builder -> builder.matchLiteral("{max}").replacement(String.valueOf(maxPlayers))));
            return;
        }
        if (plugin.raceClientHook == null || !plugin.raceClientHook.canEnterRace(p))
            return;

        plugin.capturePlayerState(p);
        p.getInventory().clear();
        players.add(p.getUniqueId());
        readyPlayers.remove(p.getUniqueId());
        plugin.setPlayerArena(p.getUniqueId(), name);
        if (lobby != null && lobby.getWorld() != null) {
            p.teleport(lobby);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        }
        giveLobbyItems(p, timeTrial);
        playerCheckpoints.put(p.getUniqueId(), 0);
        playerLaps.put(p.getUniqueId(), 1);
        p.sendMessage(plugin.getMessage("arena-joined").replaceText(b -> b.matchLiteral("{arena}").replacement(name)));

        if (timeTrial) {
            startRace(true);
        } else {
            checkAutoStart();
        }
        updateLobbyScoreboard();
    }

    public void addSpectator(Player p) {
        if (plugin.isPlayerVanished(p)) {
            p.sendMessage(plugin.getMessage("cannot-race-vanished"));
            return;
        }
        plugin.capturePlayerState(p);
        p.getInventory().clear();
        spectators.add(p.getUniqueId());
        plugin.setPlayerArena(p.getUniqueId(), name);
        spectatorModes.put(p.getUniqueId(), SpectatorMode.FREE_FLY);
        configureSpectatorControls(p);
        if (!spawns.isEmpty())
            p.teleport(spawns.get(0));
        else if (lobby != null)
            p.teleport(lobby);
        p.sendMessage(
                plugin.getMessage("arena-spectating").replaceText(b -> b.matchLiteral("{arena}").replacement(name)));
        p.showTitle(Title.title(Component.text("MODALITÀ SPETTATORE", NamedTextColor.GREEN),
                Component.text("Stai osservando " + name, NamedTextColor.AQUA)));
        p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1f);
        giveSpectatorItems(p);
        setupRaceScoreboard(p);
    }

    private void configureSpectatorControls(Player p) {
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setInvulnerable(true);
        p.setCollidable(false);
        p.setCanPickupItems(false);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(p.getUniqueId())) {
                viewer.hidePlayer(plugin, p);
            }
        }
    }

    private void giveSpectatorItems(Player p) {
        p.getInventory().clear();

        ItemStack compass = new ItemStack(Material.COMPASS);
        var compassMeta = compass.getItemMeta();
        compassMeta.displayName(Component.text("Seleziona pilota", NamedTextColor.YELLOW));
        compassMeta.lore(List.of(Component.text("Click destro per aprire", NamedTextColor.GRAY)));
        compass.setItemMeta(compassMeta);
        p.getInventory().setItem(0, compass);

        ItemStack barrier = new ItemStack(Material.BARRIER);
        var barrierMeta = barrier.getItemMeta();
        barrierMeta.displayName(Component.text("Esci dalla modalità spettatore", NamedTextColor.RED));
        barrierMeta.lore(List.of(Component.text("Click destro per uscire", NamedTextColor.GRAY)));
        barrier.setItemMeta(barrierMeta);
        p.getInventory().setItem(8, barrier);
    }

    public void cycleSpectatorMode(Player p) {
        SpectatorMode current = spectatorModes.getOrDefault(p.getUniqueId(), SpectatorMode.FREE_FLY);
        SpectatorMode next = current.next();
        spectatorModes.put(p.getUniqueId(), next);
        if (next == SpectatorMode.FOLLOW_PLAYER) {
            cycleSpectatorTarget(p);
        }
        p.sendMessage(
                Component.text("Modalità visuale: " + next.displayName + " - " + next.description,
                        NamedTextColor.AQUA));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        giveSpectatorItems(p);
    }

    public void cycleSpectatorTarget(Player spectator) {
        List<UUID> targets = new ArrayList<>();
        for (UUID uuid : calculateRankings()) {
            Player target = Bukkit.getPlayer(uuid);
            if (target != null && target.isOnline() && !finishOrder.contains(uuid)
                    && !eliminatedPlayers.contains(uuid)) {
                targets.add(uuid);
            }
        }

        if (targets.isEmpty()) {
            spectator.sendMessage(Component.text("Nessun pilota disponibile da seguire.", NamedTextColor.RED));
            return;
        }

        UUID current = spectatorTargets.get(spectator.getUniqueId());
        int nextIndex = current == null ? 0 : (targets.indexOf(current) + 1) % targets.size();
        if (nextIndex < 0 || nextIndex >= targets.size())
            nextIndex = 0;
        UUID target = targets.get(nextIndex);
        spectatorTargets.put(spectator.getUniqueId(), target);
        spectatorModes.put(spectator.getUniqueId(), SpectatorMode.FOLLOW_PLAYER);

        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null) {
            spectator.sendMessage(Component.text("Ora stai seguendo: " + targetPlayer.getName(), NamedTextColor.AQUA));
            spectator.playSound(spectator.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        }
        giveSpectatorItems(spectator);
    }

    private void giveLobbyItems(Player p, boolean isTimeTrial) {
        if (!isTimeTrial && state == RaceState.LOBBY) {
            giveReadyItem(p);
        }
        ItemStack compass = new ItemStack(Material.COMPASS);
        var meta = compass.getItemMeta();
        meta.displayName(Component.text("Menu gara", NamedTextColor.AQUA));
        meta.lore(List.of(Component.text("Click destro per aprire", NamedTextColor.GRAY)));
        compass.setItemMeta(meta);
        p.getInventory().setItem(4, compass);

        ItemStack leave = new ItemStack(Material.BARRIER);
        var leaveMeta = leave.getItemMeta();
        leaveMeta.displayName(Component.text("Abbandona gara", NamedTextColor.RED));
        leaveMeta.lore(List.of(Component.text("Click destro per uscire", NamedTextColor.GRAY)));
        leave.setItemMeta(leaveMeta);
        p.getInventory().setItem(8, leave);

        if (isTimeTrial) {
            ItemStack reset = new ItemStack(Material.RED_DYE);
            var rMeta = reset.getItemMeta();
            rMeta.displayName(Component.text("Ricomincia prova", NamedTextColor.RED));
            rMeta.lore(List.of(Component.text("Click destro per ricominciare", NamedTextColor.GRAY)));
            reset.setItemMeta(rMeta);
            p.getInventory().setItem(7, reset);
        }
    }

    private void giveReadyItem(Player p) {
        boolean ready = readyPlayers.contains(p.getUniqueId());
        ItemStack button = new ItemStack(ready ? Material.LIME_DYE : Material.GRAY_DYE);
        var meta = button.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(
                ready ? "&a&lPRONTO" : "&e&lCLICCA: PRONTO"));
        meta.lore(List.of(LegacyComponentSerializer.legacyAmpersand().deserialize(
                ready ? "&7Click destro per annullare" : "&7Click destro quando sei pronto")));
        meta.getPersistentDataContainer().set(plugin.guiManager.readyKey,
                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        button.setItemMeta(meta);
        p.getInventory().setItem(0, button);
    }

    public void toggleReady(Player p) {
        UUID uuid = p.getUniqueId();
        if (state != RaceState.LOBBY || !players.contains(uuid) || isTimeTrialMode)
            return;
        if (readyPlayers.remove(uuid)) {
            p.sendMessage(plugin.getMessage("ready-disabled"));
        } else {
            readyPlayers.add(uuid);
            p.sendMessage(plugin.getMessage("ready-enabled"));
        }
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f,
                readyPlayers.contains(uuid) ? 1.4f : 0.8f);
        giveReadyItem(p);
        checkAutoStart();
        updateLobbyScoreboard();
    }

    private void giveActiveRaceItems(Player p) {
        giveCheckpointItem(p);

        ItemStack leave = new ItemStack(Material.BARRIER);
        var leaveMeta = leave.getItemMeta();
        leaveMeta.displayName(Component.text("Abbandona gara", NamedTextColor.RED));
        leaveMeta.lore(List.of(Component.text("Click destro per uscire", NamedTextColor.GRAY)));
        leave.setItemMeta(leaveMeta);
        p.getInventory().setItem(8, leave);
    }

    private void giveCheckpointItem(Player p) {
        ItemStack checkpoint = new ItemStack(Material.RECOVERY_COMPASS);
        var checkpointMeta = checkpoint.getItemMeta();
        checkpointMeta.displayName(Component.text("Torna al checkpoint", NamedTextColor.GREEN));
        checkpointMeta.lore(List.of(Component.text("Click destro per tornare all'ultimo checkpoint",
                NamedTextColor.GRAY)));
        checkpoint.setItemMeta(checkpointMeta);
        p.getInventory().setItem(0, checkpoint);
    }

    public void resetTimeTrial(Player p) {
        if (!isTimeTrialMode || !players.contains(p.getUniqueId()))
            return;
        p.sendMessage(Component.text("↺ Prova ricominciata!", NamedTextColor.YELLOW));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 2f);

        if (playerBoats.containsKey(p.getUniqueId())) {
            Boat b = playerBoats.remove(p.getUniqueId());
            if (b != null)
                b.remove();
        }

        if (visualGhostBoat != null) {
            visualGhostBoat.remove();
            visualGhostBoat = null;
        }
        ghostPlaybackTick = 0;

        currentRecordings.put(p.getUniqueId(), new GhostData(p.getName(), 0));
        checkpointTimestamps.put(p.getUniqueId(), new HashMap<>());
        playerCheckpoints.put(p.getUniqueId(), 0);
        playerLaps.put(p.getUniqueId(), 1);
        finishOrder.remove(p.getUniqueId());
        finishTimes.remove(p.getUniqueId());
        finishTimesMs.remove(p.getUniqueId());

        startRace(true);
    }

    public void removePlayer(Player p) {
        if (spectators.contains(p.getUniqueId())) {
            spectators.remove(p.getUniqueId());
            spectatorModes.remove(p.getUniqueId());
            spectatorTargets.remove(p.getUniqueId());
            if (mainLobby != null && mainLobby.getWorld() != null)
                p.teleport(mainLobby);
            else if (p.getWorld() != null)
                p.teleport(p.getWorld().getSpawnLocation());
            plugin.removePlayerFromArenaMap(p.getUniqueId());
            plugin.restorePlayerState(p);
            p.sendMessage(plugin.getMessage("spectator-left"));
            return;
        }
        players.remove(p.getUniqueId());
        readyPlayers.remove(p.getUniqueId());
        eliminatedPlayers.remove(p.getUniqueId());
        // Remove the arena association before destroying the boat, otherwise the
        // dismount listener can cancel an intentional /race leave.
        plugin.removePlayerFromArenaMap(p.getUniqueId());
        if (playerBoats.containsKey(p.getUniqueId())) {
            Boat b = playerBoats.remove(p.getUniqueId());
            if (b != null)
                b.remove();
        }
        currentRecordings.remove(p.getUniqueId());
        checkpointTimestamps.remove(p.getUniqueId());
        stopMusic(p);
        if (mainLobby != null && mainLobby.getWorld() != null)
            p.teleport(mainLobby);
        else if (p.getWorld() != null)
            p.teleport(p.getWorld().getSpawnLocation());
        plugin.restorePlayerState(p);

        if (players.isEmpty()) {
            if (state != RaceState.LOBBY)
                stopRace();
            cancelAutoStart();
        } else if (state == RaceState.LOBBY) {
            checkAutoStart();
        } else if (state == RaceState.ACTIVE) {
            checkFinishCondition();
        }
        updateLobbyScoreboard();
    }

    public void checkFinishCondition() {
        if (state != RaceState.ACTIVE)
            return;
        boolean allFinished = true;
        for (UUID uuid : players) {
            if (!finishOrder.contains(uuid) && !eliminatedPlayers.contains(uuid)) {
                allFinished = false;
                break;
            }
        }
        if (players.isEmpty() || allFinished) {
            Bukkit.broadcast(plugin.getMessage("race-ended"));
            new BukkitRunnable() {
                @Override
                public void run() {
                    stopRace();
                }
            }.runTaskLater(plugin, 100L);
        }
    }

    // --- GAME LOOP ---
    public void startRace() {
        startRace(false);
    }

    public void startRace(boolean isTimeTrialSession) {
        boolean automaticStart = autoStartTask != null;
        for (UUID uuid : new HashSet<>(players)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && plugin.isPlayerVanished(player)) {
                player.sendMessage(plugin.getMessage("cannot-race-vanished"));
                removePlayer(player);
            }
        }
        if (players.isEmpty() || (automaticStart && players.size() < minPlayers)) {
            cancelAutoStart();
            return;
        }
        if (spawns.isEmpty())
            return;
        cancelAutoStart();

        this.isTimeTrialMode = isTimeTrialSession;
        this.currentLapForElimination = 0;
        eliminatedPlayers.clear();

        state = RaceState.STARTING;
        removeCages();
        finishOrder.clear();
        finishTimes.clear();
        finishTimesMs.clear();
        currentRecordings.clear();
        checkpointTimestamps.clear();
        ghostPlaybackTick = 0;
        tickCounter = 0;

        // Start replay recording
        if (!isTimeTrialSession && players.size() > 1) {
            currentReplay = plugin.replayManager.startRecording(name);
        }

        if (visualGhostBoat != null) {
            visualGhostBoat.remove();
            visualGhostBoat = null;
        }

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null)
                p.getInventory().clear();
        }

        int spawnIndex = 0;
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null)
                continue;

            Location spawn = spawns.get(spawnIndex % spawns.size());
            if (spawn.getWorld() == null)
                continue;

            spawnIndex++;
            p.teleport(spawn);
            Boat boat = Utils.spawnRandomBoat(spawn);
            boat.addPassenger(p);
            boat.setInvulnerable(true);
            playerBoats.put(uuid, boat);
            currentRecordings.put(uuid, new GhostData(p.getName(), 0));
            checkpointTimestamps.put(uuid, new HashMap<>());
            createCage(spawn, uuid);
            playerCheckpoints.put(uuid, 0);
            playerLaps.put(uuid, 1);
            setupRaceScoreboard(p);
            // Keep the menu and leave controls available during the final countdown.
            giveLobbyItems(p, isTimeTrialSession);
        }
        syncGhostMode();

        raceStartCountdown = 5;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (state != RaceState.STARTING) {
                    removeCages();
                    this.cancel();
                    return;
                }
                if (raceStartCountdown == 3)
                    startMusic();
                if (raceStartCountdown > 0) {
                    // TRAFFIC LIGHT ANIMATION
                    Color lightColor;
                    if (raceStartCountdown >= 4) {
                        lightColor = Color.RED;
                    } else if (raceStartCountdown >= 2) {
                        lightColor = Color.YELLOW;
                    } else {
                        lightColor = Color.LIME;
                    }

                    Title title = Title.title(
                            Component.text(raceStartCountdown,
                                    raceStartCountdown >= 4 ? NamedTextColor.RED
                                            : raceStartCountdown >= 2 ? NamedTextColor.YELLOW : NamedTextColor.GREEN),
                            Component.empty());

                    for (UUID uuid : players) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.showTitle(title);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f,
                                    0.5f + ((5 - raceStartCountdown) * 0.3f));
                            Boat boat = playerBoats.get(uuid);
                            if (boat != null) {
                                boat.setVelocity(new Vector(0, 0, 0));
                                // Spawn traffic light particles above boat
                                Location lightLoc = boat.getLocation().add(0, 3, 0);
                                p.getWorld().spawnParticle(Particle.DUST, lightLoc, 15, 0.3, 0.3, 0.3, 0,
                                        new Particle.DustOptions(lightColor, 2f));
                            }
                        }
                    }
                    raceStartCountdown--;
                } else {
                    removeCages();
                    state = RaceState.ACTIVE;
                    long now = System.currentTimeMillis();
                    for (UUID uuid : players) {
                        startTimes.put(uuid, now);
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            plugin.raceClientHook.enableRacePhysics(p);
                            if (isTimeTrialSession) {
                                p.getInventory().setItem(4, null);
                                giveCheckpointItem(p);
                            } else {
                                p.getInventory().clear();
                                giveActiveRaceItems(p);
                            }
                            Component goTitle = LegacyComponentSerializer.legacyAmpersand()
                                    .deserialize(plugin.getRawMessage("race-started"));
                            p.showTitle(Title.title(goTitle, Component.empty()));
                            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1f);
                            lastLocations.put(uuid, p.getLocation());

                            // Green burst on GO
                            Location boatLoc = p.getLocation();
                            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, boatLoc.add(0, 2, 0), 30, 1, 0.5, 1, 0);
                        }
                    }
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void stopRace() {
        cancelAutoStart();
        stopAllMusic();

        // Send Discord results
        if (!finishOrder.isEmpty() && !plugin.discordWebhookUrl.isEmpty()) {
            plugin.discordWebhook.sendRaceResults(plugin.discordWebhookUrl, name, finishOrder, finishTimes);
        }

        // Save replay
        if (currentReplay != null && !finishOrder.isEmpty()) {
            plugin.replayManager.finishRecording(currentReplay, finishTimesMs);
            currentReplay = null;
        }

        state = RaceState.LOBBY;
        isTimeTrialMode = false;

        removeCages();
        for (Boat b : playerBoats.values())
            b.remove();
        playerBoats.clear();
        finishOrder.clear();
        finishTimes.clear();
        finishTimesMs.clear();
        currentRecordings.clear();
        checkpointTimestamps.clear();
        eliminatedPlayers.clear();

        // Clean up fake entities
        for (UUID uuid : players) {
            if (ghostEntityIds.containsKey(uuid)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null)
                    PacketUtils.destroyFakeEntity(p, ghostEntityIds.get(uuid));
            }
        }
        ghostEntityIds.clear();

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                if (mainLobby != null && mainLobby.getWorld() != null)
                    p.teleport(mainLobby);
                else if (p.getWorld() != null)
                    p.teleport(p.getWorld().getSpawnLocation());
                plugin.removePlayerFromArenaMap(uuid);
                plugin.restorePlayerState(p);
            }
        }
        players.clear();
        readyPlayers.clear();
        for (UUID uuid : spectators) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                if (mainLobby != null && mainLobby.getWorld() != null)
                    p.teleport(mainLobby);
                else if (p.getWorld() != null)
                    p.teleport(p.getWorld().getSpawnLocation());
                plugin.removePlayerFromArenaMap(uuid);
                plugin.restorePlayerState(p);
                p.sendMessage(plugin.getMessage("spectator-left"));
            }
        }
        spectators.clear();
        spectatorModes.clear();
        spectatorTargets.clear();
        updateLeaderboardHologram();
    }

    public void tick() {
        if (state == RaceState.LOBBY)
            return;
        if (state == RaceState.ACTIVE) {
            tickCounter++;

            // Record replay frame
            if (currentReplay != null && tickCounter % 2 == 0) {
                Map<UUID, Location> locations = new HashMap<>();
                for (UUID uuid : players) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null)
                        locations.put(uuid, p.getLocation());
                }
                plugin.replayManager.recordFrame(currentReplay, tickCounter, locations,
                        new HashSet<>(finishOrder), playerCheckpoints, playerLaps);
            }

            // Ghost playback
            if (isTimeTrialMode && bestGhost != null && ghostPlaybackTick < bestGhost.points.size()) {
                Location ghostLoc = bestGhost.points.get(ghostPlaybackTick);
                if (ghostLoc != null && ghostLoc.getWorld() != null) {
                    ghostLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, ghostLoc.clone().add(0, 0.5, 0), 1, 0,
                            0, 0, 0);
                    for (UUID uuid : players) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            if (!ghostEntityIds.containsKey(uuid)) {
                                UUID fakeUuid = UUID.randomUUID();
                                int id = PacketUtils.spawnFakeBoat(p, ghostLoc, fakeUuid);
                                ghostEntityIds.put(uuid, id);
                                org.bukkit.scoreboard.Team t = p.getScoreboard().getTeam("ghost");
                                if (t != null) {
                                    t.addEntry(fakeUuid.toString());
                                }
                            } else {
                                PacketUtils.moveFakeBoat(p, ghostEntityIds.get(uuid), ghostLoc);
                            }
                        }
                    }
                }
            } else if (ghostPlaybackTick >= (bestGhost != null ? bestGhost.points.size() : 0)) {
                for (UUID uuid : ghostEntityIds.keySet()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null)
                        PacketUtils.destroyFakeEntity(p, ghostEntityIds.get(uuid));
                }
                ghostEntityIds.clear();
            }
            ghostPlaybackTick++;

            List<UUID> ranking = calculateRankings();

            // Return to free-flight controls when POV is cancelled or its target is gone.
            for (Map.Entry<UUID, UUID> entry : new HashMap<>(spectatorTargets).entrySet()) {
                Player spectator = Bukkit.getPlayer(entry.getKey());
                Player target = Bukkit.getPlayer(entry.getValue());
                boolean invalidTarget = target == null || !target.isOnline()
                        || finishOrder.contains(entry.getValue()) || eliminatedPlayers.contains(entry.getValue());
                boolean detached = spectator != null && spectator.getGameMode() == GameMode.SPECTATOR
                        && spectator.getSpectatorTarget() == null;
                if (spectator != null && (invalidTarget || detached)) {
                    stopFirstPersonSpectating(spectator);
                }
            }

            for (UUID uuid : players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline())
                    continue;

                if (eliminatedPlayers.contains(uuid))
                    continue;

                if (!finishOrder.contains(uuid) && playerBoats.containsKey(uuid)) {
                    Location currentLoc = p.getLocation();

                    if (currentLoc.getY() < voidY) {
                        respawnPlayer(p);
                        p.sendMessage(Component.text("§cSei caduto! Ritorno in pista..."));
                        continue;
                    }

                    if (currentRecordings.containsKey(uuid)) {
                        currentRecordings.get(uuid).points.add(currentLoc);
                    }
                    Location lastLoc = lastLocations.getOrDefault(uuid, currentLoc);
                    double speedKmH = (lastLoc.getWorld() == currentLoc.getWorld())
                            ? currentLoc.distance(lastLoc) * 72.0
                            : 0;
                    lastLocations.put(uuid, currentLoc);
                    int safety = 0;
                    boolean keepChecking = true;
                    while (keepChecking && safety < 3) {
                        keepChecking = checkObjectivesAlongPath(p, uuid, lastLoc, currentLoc);
                        safety++;
                    }

                    TrailType trail = plugin.getPlayerTrailPreference(uuid);
                    Utils.spawnTrailParticles(p, playerBoats.get(uuid), trail);

                    long timeMs = System.currentTimeMillis()
                            - startTimes.getOrDefault(uuid, System.currentTimeMillis());
                    String timeStr = Utils.formatTime(timeMs);
                    int displayLap = (type == RaceType.LAP || type == RaceType.ELIMINATION)
                            ? playerLaps.getOrDefault(uuid, 1)
                            : 1;
                    int maxLap = (type == RaceType.LAP || type == RaceType.ELIMINATION) ? totalLaps : 1;
                    int cp = playerCheckpoints.getOrDefault(uuid, 0);
                    updateRaceScoreboard(p, timeStr, speedKmH, cp, checkpoints.size(), displayLap, maxLap, ranking);

                    if (tickCounter % 400 == 0) {
                        p.sendMessage(plugin.getMessage("stuck-tip"));
                    }
                    String abText = String.format("§b%.0f km/h  §7|  §aCP: %d/%d", speedKmH, cp, checkpoints.size());
                    if (type == RaceType.LAP || type == RaceType.ELIMINATION)
                        abText += String.format("  §7|  §6Giro: %d/%d", displayLap, maxLap);
                    p.sendActionBar(Component.text(abText));
                    highlightNextTarget(p, uuid);
                }
            }
            for (UUID uuid : spectators) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    updateRaceScoreboard(p, "SPETTATORE", 0, 0, checkpoints.size(), 0, totalLaps, ranking);
                }
            }
        }
    }

    // --- LOGIC HELPERS ---
    private boolean checkObjectivesAlongPath(Player p, UUID uuid, Location from, Location to) {
        int currentCpIndex = playerCheckpoints.getOrDefault(uuid, 0);
        Location cpTarget = null;
        boolean checkFinish = false;
        if (currentCpIndex < checkpoints.size()) {
            cpTarget = checkpoints.get(currentCpIndex);
        } else {
            checkFinish = true;
        }
        if (cpTarget != null) {
            if (Utils.lineSegmentIntersectsSphere(from, to, cpTarget, plugin.checkpointRadius)) {
                int totalCPs = checkpoints.size();
                int currentLap = playerLaps.getOrDefault(uuid, 1);
                int globalCPIndex = ((currentLap - 1) * totalCPs) + currentCpIndex;
                if (checkpointTimestamps.containsKey(uuid)) {
                    checkpointTimestamps.get(uuid).put(globalCPIndex, System.currentTimeMillis());
                }
                playerCheckpoints.put(uuid, currentCpIndex + 1);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                return true;
            }
        }
        if (checkFinish && finishBox != null) {
            Vector start = from.toVector();
            Vector direction = to.toVector().subtract(start);
            double maxDist = direction.length();
            if (maxDist > 0.01) {
                org.bukkit.util.RayTraceResult result = finishBox.rayTrace(start, direction.normalize(), maxDist);
                if (result != null) {
                    handleFinishLineHit(p, uuid);
                    return true;
                }
            }
            if (finishBox.contains(to.toVector())) {
                handleFinishLineHit(p, uuid);
                return true;
            }
        }
        return false;
    }

    private void handleFinishLineHit(Player p, UUID uuid) {
        if (type == RaceType.LAP || type == RaceType.ELIMINATION) {
            int lap = playerLaps.getOrDefault(uuid, 1);
            if (lap < totalLaps) {
                playerLaps.put(uuid, lap + 1);
                playerCheckpoints.put(uuid, 0);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 2f);
                String msg = plugin.getRawMessage("lap-message")
                        .replace("{lap}", String.valueOf(lap + 1))
                        .replace("{total}", String.valueOf(totalLaps));
                p.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(msg));

                // ELIMINATION MODE: Check for elimination at end of each lap
                if (type == RaceType.ELIMINATION && lap > currentLapForElimination) {
                    currentLapForElimination = lap;
                    eliminateLastPlace();
                }
            } else {
                finishPlayer(p);
            }
        } else {
            finishPlayer(p);
        }
    }

    private void eliminateLastPlace() {
        List<UUID> ranking = calculateRankings();
        if (ranking.size() <= 1)
            return;

        // Find the last non-finished, non-eliminated player
        UUID lastPlace = null;
        for (int i = ranking.size() - 1; i >= 0; i--) {
            UUID uuid = ranking.get(i);
            if (!finishOrder.contains(uuid) && !eliminatedPlayers.contains(uuid)) {
                lastPlace = uuid;
                break;
            }
        }

        if (lastPlace == null)
            return;

        eliminatedPlayers.add(lastPlace);
        Player eliminated = Bukkit.getPlayer(lastPlace);
        if (eliminated != null) {
            plugin.raceClientHook.disableRacePhysics(eliminated);
            // Remove boat and convert to spectator
            if (playerBoats.containsKey(lastPlace)) {
                Boat boat = playerBoats.remove(lastPlace);
                if (boat != null)
                    boat.remove();
            }

            plugin.restorePlayerScoreboard(eliminated);
            spectatorModes.put(lastPlace, SpectatorMode.FREE_FLY);
            configureSpectatorControls(eliminated);
            giveSpectatorItems(eliminated);
            eliminated.showTitle(Title.title(
                    Component.text("ELIMINATO", NamedTextColor.RED),
                    Component.text("Hai concluso il giro in ultima posizione!", NamedTextColor.GRAY)));
            eliminated.playSound(eliminated.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 0.5f);

            Bukkit.broadcast(
                    Component.text("☠ " + eliminated.getName() + " è stato eliminato!", NamedTextColor.RED));
        }

        // Check if race should end
        long remainingRacers = players.stream()
                .filter(uuid -> !finishOrder.contains(uuid) && !eliminatedPlayers.contains(uuid))
                .count();

        if (remainingRacers <= 1) {
            // Last player standing wins
            for (UUID uuid : players) {
                if (!finishOrder.contains(uuid) && !eliminatedPlayers.contains(uuid)) {
                    Player winner = Bukkit.getPlayer(uuid);
                    if (winner != null) {
                        finishPlayer(winner);
                    }
                    break;
                }
            }
        }
    }

    private void finishPlayer(Player p) {
        stopMusic(p);
        if (finishOrder.contains(p.getUniqueId()))
            return;
        plugin.raceClientHook.disableRacePhysics(p);
        finishOrder.add(p.getUniqueId());
        long timeMs = System.currentTimeMillis() - startTimes.get(p.getUniqueId());
        String timeStr = Utils.formatTime(timeMs);
        finishTimes.put(p.getUniqueId(), timeStr);
        finishTimesMs.put(p.getUniqueId(), timeMs);
        plugin.incrementStat(p.getUniqueId(), "races_played");
        boolean isWinner = finishOrder.size() == 1;
        if (isWinner) {
            plugin.incrementStat(p.getUniqueId(), "wins");
        }

        if (!bestTimes.containsKey(p.getUniqueId()) || timeMs < bestTimes.get(p.getUniqueId())) {
            bestTimes.put(p.getUniqueId(), timeMs);
            p.sendMessage(plugin.getMessage("new-pb").replaceText(b -> b.matchLiteral("{time}").replacement(timeStr)));

            // Check for server record
            if (currentRecordings.containsKey(p.getUniqueId())) {
                boolean isServerBest = true;
                for (Long t : bestTimes.values()) {
                    if (t < timeMs) {
                        isServerBest = false;
                        break;
                    }
                }
                if (isServerBest) {
                    bestGhost = currentRecordings.get(p.getUniqueId());
                    p.sendMessage(plugin.getMessage("new-server-record"));

                    // Discord notification for server record
                    if (!plugin.discordWebhookUrl.isEmpty()) {
                        plugin.discordWebhook.sendNewRecord(plugin.discordWebhookUrl, name, p.getName(), timeStr, true);
                    }
                }
            }
        }

        Component titleMain = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(plugin.getRawMessage("finished-title"));
        Component titleSub = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(plugin.getRawMessage("finished-subtitle").replace("{time}", timeStr));
        p.showTitle(Title.title(titleMain, titleSub));

        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1f, 1f);

        // Command Rewards
        int rank = finishOrder.size();
        if (plugin.rewardsEnabled && players.size() >= plugin.rewardsMinPlayers) {
            if (plugin.rewardCommands.containsKey(rank)) {
                List<String> commands = plugin.rewardCommands.get(rank);
                for (String cmd : commands) {
                    if (cmd == null || cmd.trim().isEmpty()) continue;
                    String finalCmd = cmd.replace("%player%", p.getName());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                    });
                }
            }
        }

        // VICTORY CELEBRATION: Spawn fireworks for winner
        if (isWinner) {
            spawnVictoryFireworks(p.getLocation());
            Bukkit.broadcast(Component.text(""));
            Bukkit.broadcast(Component.text("🏆 " + p.getName() + " VINCE LA GARA! 🏆", NamedTextColor.GOLD));
            Bukkit.broadcast(Component.text(""));
        }

        Boat boat = playerBoats.remove(p.getUniqueId());
        if (boat != null)
            boat.remove();
        spectatorModes.put(p.getUniqueId(), SpectatorMode.FREE_FLY);
        configureSpectatorControls(p);
        giveSpectatorItems(p);
        plugin.restorePlayerScoreboard(p);
        String broadcastMsg = plugin.getRawMessage("finish-broadcast").replace("{player}", p.getName())
                .replace("{arena}", name).replace("{time}", timeStr);
        Bukkit.broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(broadcastMsg));
        checkFinishCondition();
    }

    private void spawnVictoryFireworks(Location loc) {
        for (int i = 0; i < 5; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Location fireworkLoc = loc.clone().add(
                            ThreadLocalRandom.current().nextDouble(-3, 3),
                            ThreadLocalRandom.current().nextDouble(0, 2),
                            ThreadLocalRandom.current().nextDouble(-3, 3));
                    Firework fw = loc.getWorld().spawn(fireworkLoc, Firework.class);
                    FireworkMeta meta = fw.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder()
                            .withColor(Color.AQUA, Color.YELLOW, Color.WHITE)
                            .withFade(Color.BLUE)
                            .with(FireworkEffect.Type.BALL_LARGE)
                            .trail(true)
                            .flicker(true)
                            .build());
                    meta.setPower(1);
                    fw.setFireworkMeta(meta);
                }
            }.runTaskLater(plugin, i * 10L);
        }
    }

    public void respawnPlayer(Player p) {
        UUID uuid = p.getUniqueId();
        if (state != RaceState.ACTIVE || !players.contains(uuid) || isSpectator(uuid))
            return;
        int idx = playerCheckpoints.getOrDefault(uuid, 0);
        Location loc = (idx == 0) ? (!spawns.isEmpty() ? spawns.getFirst() : lobby) : checkpoints.get(idx - 1);
        if (loc == null)
            return;
        respawningPlayers.add(uuid);
        try {
            Boat oldBoat = playerBoats.remove(uuid);
            if (oldBoat != null)
                oldBoat.remove();
            p.teleport(loc);
            plugin.raceClientHook.enableRacePhysics(p);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            p.sendMessage(plugin.getMessage("respawn-success"));
            lastLocations.put(uuid, loc);
            Boat boat = Utils.spawnRandomBoat(loc);
            boat.addPassenger(p);
            boat.setInvulnerable(true);
            playerBoats.put(uuid, boat);

            syncGhostModeForPlayer(p, boat);
            for (UUID otherUUID : players) {
                if (!otherUUID.equals(uuid)) {
                    Player otherP = Bukkit.getPlayer(otherUUID);
                    if (otherP != null) {
                        otherP.hideEntity(plugin, boat);
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (otherP.isOnline() && boat.isValid())
                                    otherP.showEntity(plugin, boat);
                            }
                        }.runTaskLater(plugin, 1L);
                    }
                }
            }
        } finally {
            respawningPlayers.remove(uuid);
        }
    }

    private List<UUID> calculateRankings() {
        List<UUID> rankList = new ArrayList<>(players);
        rankList.removeAll(eliminatedPlayers);

        // Pre-build index lookup map for finish order (O(1) lookups during sort)
        Map<UUID, Integer> finishIndexMap = new HashMap<>(finishOrder.size());
        for (int i = 0; i < finishOrder.size(); i++) {
            finishIndexMap.put(finishOrder.get(i), i);
        }

        // Pre-calculate squared distance to current target per player (O(1) lookups during sort)
        Map<UUID, Double> distanceMap = new HashMap<>(rankList.size());
        for (UUID u : rankList) {
            if (!finishIndexMap.containsKey(u)) {
                int cp = playerCheckpoints.getOrDefault(u, 0);
                distanceMap.put(u, getDistanceToTarget(u, cp));
            }
        }

        rankList.sort((u1, u2) -> {
            Integer f1 = finishIndexMap.get(u1);
            Integer f2 = finishIndexMap.get(u2);
            if (f1 != null && f2 != null) return Integer.compare(f1, f2);
            if (f1 != null) return -1;
            if (f2 != null) return 1;

            int l1 = playerLaps.getOrDefault(u1, 1), l2 = playerLaps.getOrDefault(u2, 1);
            if (l1 != l2) return Integer.compare(l2, l1);

            int c1 = playerCheckpoints.getOrDefault(u1, 0), c2 = playerCheckpoints.getOrDefault(u2, 0);
            if (c1 != c2) return Integer.compare(c2, c1);

            double d1 = distanceMap.getOrDefault(u1, Double.MAX_VALUE);
            double d2 = distanceMap.getOrDefault(u2, Double.MAX_VALUE);
            return Double.compare(d1, d2);
        });

        return rankList;
    }

    private double getDistanceToTarget(UUID uuid, int cpIndex) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null)
            return Double.MAX_VALUE;
        Location target;
        if (cpIndex < checkpoints.size())
            target = checkpoints.get(cpIndex);
        else if (finishCenter != null)
            target = finishCenter;
        else
            target = finishPos1;
        if (target == null || target.getWorld() == null || !p.getWorld().equals(target.getWorld()))
            return Double.MAX_VALUE;

        double dx = p.getLocation().getX() - target.getX();
        double dy = p.getLocation().getY() - target.getY();
        double dz = p.getLocation().getZ() - target.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private void highlightNextTarget(Player p, UUID uuid) {
        int currentCpIndex = playerCheckpoints.getOrDefault(uuid, 0);
        Location target = null;
        if (currentCpIndex < checkpoints.size())
            target = checkpoints.get(currentCpIndex);
        else if (finishCenter != null)
            target = finishCenter;
        else
            target = finishPos1;
        if (target != null && target.getWorld() != null && target.getWorld().equals(p.getWorld()))
            p.spawnParticle(Particle.HAPPY_VILLAGER, target.getX(), target.getY() + 1.5, target.getZ(), 2, 0.2, 0.2, 0.2, 0);
    }

    private void createCage(Location spawn, UUID uuid) {
        Material cageMat = plugin.getPlayerCagePreference(uuid);
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                for (int y = 0; y <= 2; y++) {
                    if (Math.abs(x) <= 1 && Math.abs(z) <= 1)
                        continue;
                    Location b = spawn.clone().add(x, y, z);
                    if (b.getBlock().getType() == Material.AIR) {
                        b.getBlock().setType(cageMat);
                        glassBlocks.add(b);
                    }
                }
    }

    private void removeCages() {
        for (Location l : glassBlocks)
            if (l.getBlock().getType().name().contains("GLASS"))
                l.getBlock().setType(Material.AIR);
        glassBlocks.clear();
    }

    private void syncGhostMode() {
        for (UUID uuid : playerBoats.keySet()) {
            Boat b = playerBoats.get(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && b != null)
                syncGhostModeForPlayer(p, b);
        }
    }

    private void syncGhostModeForPlayer(Player p, Boat b) {
        for (UUID otherUUID : players) {
            Player otherP = Bukkit.getPlayer(otherUUID);
            if (otherP != null) {
                Team t = otherP.getScoreboard().getTeam("ghost");
                if (t != null) {
                    t.addEntry(p.getName());
                    t.addEntry(b.getUniqueId().toString());
                }
            }
        }
        for (UUID otherUUID : players) {
            if (!otherUUID.equals(p.getUniqueId())) {
                Player otherP = Bukkit.getPlayer(otherUUID);
                if (otherP != null) {
                    otherP.hideEntity(plugin, b);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (otherP.isOnline() && b.isValid())
                                otherP.showEntity(plugin, b);
                        }
                    }.runTaskLater(plugin, 1L);
                }
            }
        }
    }

    public void startMusic() {
        if (!plugin.musicEnabled)
            return;
        if (musicTask != null && !musicTask.isCancelled())
            musicTask.cancel();
        musicTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != RaceState.ACTIVE && state != RaceState.STARTING) {
                    this.cancel();
                    return;
                }
                for (UUID uuid : players) {
                    if (!finishOrder.contains(uuid)) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && p.isOnline())
                            p.playSound(p.getLocation(), plugin.musicSound, SoundCategory.MASTER, plugin.musicVolume,
                                    plugin.musicPitch);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, plugin.musicDuration * 20L);
    }

    public void stopMusic(Player p) {
        if (p != null)
            p.stopSound(plugin.musicSound, SoundCategory.MASTER);
    }

    public void stopAllMusic() {
        if (musicTask != null) {
            musicTask.cancel();
            musicTask = null;
        }
        for (UUID uuid : players)
            stopMusic(Bukkit.getPlayer(uuid));
    }

    private void checkAutoStart() {
        if (state != RaceState.LOBBY)
            return;
        if (players.size() >= minPlayers && allPlayersReady()) {
            if (autoStartTask == null) {
                lobbyCountdown = autoStartDelay;
                autoStartTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (state != RaceState.LOBBY || players.size() < minPlayers || !allPlayersReady()) {
                            cancelAutoStart();
                            return;
                        }
                        if (lobbyCountdown <= 0) {
                            startRace();
                            cancel();
                            return;
                        }
                        if (lobbyCountdown == 60 || lobbyCountdown == 30 || lobbyCountdown == 10
                                || lobbyCountdown <= 5) {
                            for (UUID uuid : players) {
                                Player p = Bukkit.getPlayer(uuid);
                                if (p != null) {
                                    p.sendMessage(plugin.getMessage("race-starting").replaceText(
                                            b -> b.matchLiteral("{time}").replacement(String.valueOf(lobbyCountdown))));
                                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                                }
                            }
                        }
                        updateLobbyScoreboard();
                        lobbyCountdown--;
                    }
                }.runTaskTimer(plugin, 0L, 20L);
            }
        } else {
            cancelAutoStart();
        }
    }

    private boolean allPlayersReady() {
        return !players.isEmpty() && readyPlayers.containsAll(players);
    }

    private void cancelAutoStart() {
        if (autoStartTask != null) {
            autoStartTask.cancel();
            autoStartTask = null;
            lobbyCountdown = -1;
            updateLobbyScoreboard();
        }
    }

    private void updateLobbyScoreboard() {
        if (state != RaceState.LOBBY)
            return;
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null)
                setupLobbyScoreboard(p);
        }
    }

    private void setupLobbyScoreboard(Player p) {
        ScoreboardManager m = Bukkit.getScoreboardManager();
        Scoreboard b = m.getNewScoreboard();
        Objective o = b.registerNewObjective("Lobby", Criteria.DUMMY,
                legacy(plugin.getScoreboardString("lobby.title", "&b&lVoxelKart")));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        try {
            o.numberFormat(NumberFormat.blank());
        } catch (Throwable ignored) {
        }
        String status;
        if (autoStartTask != null && lobbyCountdown >= 0) {
            status = plugin.getScoreboardString("lobby.status.countdown", "Inizio tra {countdown}s");
        } else if (players.size() < minPlayers) {
            status = plugin.getScoreboardString("lobby.status.waiting-players", "In attesa di giocatori...");
        } else {
            status = plugin.getScoreboardString("lobby.status.waiting-ready", "In attesa dei pronti...");
        }
        Map<String, String> values = new HashMap<>();
        values.put("{arena}", name);
        values.put("{players}", String.valueOf(players.size()));
        values.put("{min_players}", String.valueOf(minPlayers));
        values.put("{max_players}", String.valueOf(maxPlayers));
        values.put("{ready}", String.valueOf(readyPlayers.size()));
        values.put("{countdown}", String.valueOf(Math.max(0, lobbyCountdown)));
        values.put("{status}", replacePlaceholders(status, values));
        List<String> defaults = List.of("&7--------------------", "&eArena: &f{arena}",
                "&eGiocatori: &f{players}/{max_players}", "&eMinimo: &f{min_players}",
                "&ePronti: &f{ready}/{players}", "&eStato: &f{status}", "&7--------------------");
        addConfiguredLines(b, o, plugin.getScoreboardLines("lobby.lines", defaults), values);
        p.setScoreboard(b);
    }

    public void refreshLobbyScoreboard() {
        updateLobbyScoreboard();
    }

    private void setupRaceScoreboard(Player p) {
        ScoreboardManager m = Bukkit.getScoreboardManager();
        Scoreboard b = m.getNewScoreboard();
        Objective o = b.registerNewObjective("IceRace", Criteria.DUMMY,
                legacy(plugin.getScoreboardString("race.title", "&b&lVoxelKart")));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        try {
            o.numberFormat(NumberFormat.blank());
        } catch (Throwable ignored) {
        }
        Team ghost = b.registerNewTeam("ghost");
        ghost.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        List<String> defaults = new ArrayList<>(List.of("&7--------------------", "&eStatistiche:",
                "{stats}", "", "&e&lCLASSIFICA"));
        for (int i = 1; i <= 10; i++)
            defaults.add("{ranking_" + i + "}");
        addConfiguredLines(b, o, plugin.getScoreboardLines("race.lines", defaults), Map.of());
        p.setScoreboard(b);
    }

    private void addConfiguredLines(Scoreboard board, Objective objective, List<String> configured,
            Map<String, String> values) {
        int size = Math.min(15, configured.size());
        for (int i = 0; i < size; i++) {
            String line = replacePlaceholders(configured.get(i), values);
            String teamName = "line_" + i;
            if (line.equals("{stats}"))
                teamName = "stats";
            else if (line.matches("\\{ranking_([1-9]|10)\\}"))
                teamName = "rank_" + line.substring(9, line.length() - 1);
            Team team = board.registerNewTeam(teamName);
            String entry = "§" + Integer.toHexString(i);
            team.addEntry(entry);
            if (!line.equals("{stats}") && !line.startsWith("{ranking_"))
                team.prefix(legacy(line));
            objective.getScore(entry).setScore(size - i);
        }
    }

    private String replacePlaceholders(String input, Map<String, String> values) {
        String result = input == null ? "" : input;
        for (Map.Entry<String, String> value : values.entrySet())
            result = result.replace(value.getKey(), value.getValue());
        return result;
    }

    private Component legacy(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text == null ? "" : text);
    }

    private void updateRaceScoreboard(Player p, String time, double speed, int cp, int totalCps, int lap, int maxLaps,
            List<UUID> ranking) {
        Scoreboard b = p.getScoreboard();
        Team stats = b.getTeam("stats");
        if (stats != null) {
            String statText;
            if (time.equals("SPETTATORE")) {
                statText = plugin.getScoreboardString("race.spectator-stats", "&bSPETTATORE");
            } else {
                statText = plugin.getScoreboardString("race.stats-format",
                                "&f{time} &7| &b{speed} km/h &7| &aCP: {checkpoint}/{checkpoints}")
                        .replace("{time}", time)
                        .replace("{speed}", String.format("%.0f", speed))
                        .replace("{checkpoint}", String.valueOf(cp))
                        .replace("{checkpoints}", String.valueOf(totalCps));
                if (type == RaceType.LAP || type == RaceType.ELIMINATION)
                    statText += plugin.getScoreboardString("race.lap-format", " &7| &6G{lap}/{laps}")
                            .replace("{lap}", String.valueOf(lap))
                            .replace("{laps}", String.valueOf(maxLaps));
            }
            stats.suffix(legacy(statText));
        }
        UUID leaderUUID = (!ranking.isEmpty()) ? ranking.get(0) : null;
        for (int i = 0; i < 10; i++) {
            Team t = b.getTeam("rank_" + (i + 1));
            if (t != null) {
                if (i < ranking.size()) {
                    UUID uuid = ranking.get(i);
                    Player rp = Bukkit.getPlayer(uuid);
                    String pName = (rp != null) ? rp.getName() : "Sconosciuto";
                    int pLap = playerLaps.getOrDefault(uuid, 1);
                    String gapStr;
                    if (i == 0) {
                        gapStr = "§e1°";
                    } else {
                        long gap = calculateGapToLeader(uuid, leaderUUID);
                        if (gap == 0)
                            gapStr = "§7-.-";
                        else if (gap > 0)
                            gapStr = "§c+" + String.format("%.1f", gap / 1000.0) + "s";
                        else
                            gapStr = "§a" + String.format("%.1f", gap / 1000.0) + "s";
                    }
                    String entry;
                    if (uuid.equals(p.getUniqueId())) {
                        entry = String.format("§f%s §8// §aTu §eG%d", time, pLap);
                    } else {
                        entry = String.format("%s §8// §f%s §7G%d", gapStr, pName, pLap);
                    }
                    if (finishOrder.contains(uuid))
                        entry = "§a✔ " + pName + " §7(Arrivato)";
                    if (eliminatedPlayers.contains(uuid))
                        entry = "§c✘ " + pName + " §7(Eliminato)";
                    t.suffix(Component.text(entry));
                } else {
                    t.suffix(legacy(plugin.getScoreboardString("race.empty-ranking", "&7---")));
                }
            }
        }
    }

    private long calculateGapToLeader(UUID playerUUID, UUID leaderUUID) {
        if (playerUUID == null || leaderUUID == null)
            return 0;
        if (playerUUID.equals(leaderUUID))
            return 0;
        int pLap = playerLaps.getOrDefault(playerUUID, 1);
        int pCp = playerCheckpoints.getOrDefault(playerUUID, 0);
        int totalCPs = checkpoints.size();
        int globalIndex = ((pLap - 1) * totalCPs) + pCp - 1;
        if (globalIndex < 0)
            return 0;
        Map<Integer, Long> pTimes = checkpointTimestamps.get(playerUUID);
        Map<Integer, Long> lTimes = checkpointTimestamps.get(leaderUUID);
        if (pTimes == null || lTimes == null)
            return 0;
        if (!pTimes.containsKey(globalIndex) || !lTimes.containsKey(globalIndex))
            return 0;
        return pTimes.get(globalIndex) - lTimes.get(globalIndex);
    }

    public boolean isSetupComplete() {
        return !spawns.isEmpty() && !checkpoints.isEmpty() && finishBox != null && lobby != null;
    }

    public List<String> getSetupStatus() {
        List<String> status = new ArrayList<>();
        status.add(spawns.isEmpty() ? "&c✘ Partenze: nessuna" : "&a✔ Partenze: &f" + spawns.size() + " impostate");
        status.add(checkpoints.isEmpty() ? "&c✘ Checkpoint: nessuno" : "&a✔ Checkpoint: &f" + checkpoints.size() + " impostati");
        status.add(finishBox == null ? "&c✘ Traguardo: non impostato" : "&a✔ Traguardo: &fconfigurato");
        status.add(lobby == null ? "&c✘ Lobby pre-gara: non impostata" : "&a✔ Lobby pre-gara: &fconfigurata");
        status.add(mainLobby == null ? "&e! Lobby principale: &fspawn del mondo (predefinito)" : "&a✔ Lobby principale: &fconfigurata");
        status.add(leaderboardLocation == null ? "&e! Classifica: &fnon impostata" : "&a✔ Classifica: &fconfigurata");
        return status;
    }
}
