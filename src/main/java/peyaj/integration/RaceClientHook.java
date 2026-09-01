package peyaj.integration;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import peyaj.IceBoatRacing;
import peyaj.RaceArena;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates the optional native boat-physics enhancement supplied by the
 * Fabric client mod. Vanilla clients can join and race without it.
 */
public final class RaceClientHook implements PluginMessageListener, Listener {

    public static final String CHANNEL = "iceboatracing:physics";
    public static final int REQUIRED_PROTOCOL = 1;

    private static final byte DISABLE = 0;
    private static final byte ENABLE = 1;
    private static final byte REQUEST_VERSION = 2;
    private static final byte VERSION = 3;

    private final IceBoatRacing plugin;
    private final Map<UUID, Integer> clients = new ConcurrentHashMap<>();
    private final Set<UUID> racePhysics = ConcurrentHashMap.newKeySet();
    private final PacketListenerCommon correctionListener;

    public RaceClientHook(IceBoatRacing plugin) {
        this.plugin = plugin;

        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);

        correctionListener = PacketEvents.getAPI().getEventManager().registerListener(
                new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
                    @Override
                    public void onPacketSend(PacketSendEvent event) {
                        if (event.getPacketType() == PacketType.Play.Server.VEHICLE_MOVE
                                && event.getUser() != null
                                && racePhysics.contains(event.getUser().getUUID())) {
                            // During the race the Fabric client and its input are authoritative.
                            // A vanilla correction would undo a valid native ice step.
                            event.setCancelled(true);
                        }
                    }
                });

        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduleVersionRequest(player);
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || message == null || message.length != 5 || message[0] != VERSION) {
            return;
        }

        int protocol = ByteBuffer.wrap(message, 1, Integer.BYTES).getInt();
        clients.put(player.getUniqueId(), protocol);

        if (protocol == REQUIRED_PROTOCOL) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                RaceArena arena = plugin.getPlayerArena(player.getUniqueId());
                if (arena != null && arena.isActiveRacer(player.getUniqueId())) {
                    enableRacePhysics(player);
                }
            });
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleVersionRequest(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        clients.remove(uuid);
        racePhysics.remove(uuid);
    }

    public void enableRacePhysics(Player player) {
        Integer protocol = clients.get(player.getUniqueId());
        if (protocol == null || protocol != REQUIRED_PROTOCOL) {
            // Optional enhancement: vanilla clients keep using normal,
            // server-authoritative boat physics.
            return;
        }

        racePhysics.add(player.getUniqueId());
        send(player, ENABLE);
    }

    public void disableRacePhysics(Player player) {
        racePhysics.remove(player.getUniqueId());
        if (player.isOnline() && clients.containsKey(player.getUniqueId())) {
            send(player, DISABLE);
        }
    }

    public void close() {
        for (UUID uuid : Set.copyOf(racePhysics)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                disableRacePhysics(player);
            }
        }
        racePhysics.clear();
        clients.clear();
        PacketEvents.getAPI().getEventManager().unregisterListener(correctionListener);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }

    private void scheduleVersionRequest(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                requestVersion(player);
            }
        }, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !clients.containsKey(player.getUniqueId())) {
                requestVersion(player);
            }
        }, 60L);
    }

    private void requestVersion(Player player) {
        if (player != null && player.isOnline()) {
            send(player, REQUEST_VERSION);
        }
    }

    private void send(Player player, byte opcode) {
        try {
            player.sendPluginMessage(plugin, CHANNEL, new byte[] { opcode });
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Impossibile comunicare con la mod client di "
                    + player.getName() + ": " + exception.getMessage());
        }
    }
}
