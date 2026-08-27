package peyaj.integration;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
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

/** Coordinates the native boat physics implemented by the 26.2 Fabric client mod. */
public final class RaceClientHook implements PluginMessageListener, Listener {

    public static final String CHANNEL = "iceboatracing:physics";
    public static final int REQUIRED_PROTOCOL = 1;
    public static final String FABRIC_API_URL = "https://modrinth.com/mod/fabric-api/version/NqwNSxwA";
    public static final String FABRIC_INSTALLER_URL = "https://fabricmc.net/use/installer/";
    public static final String CLIENT_MOD_NAME = "IceBoatRacing-Client-1.0.0+26.2.jar";
    public static final String CLIENT_MOD_URL = "https://raw.githubusercontent.com/realpeyaj/peyajIceBoatRacing/main/downloads/IceBoatRacing-Client-26.2.jar";

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

    public boolean canEnterRace(Player player) {
        Integer protocol = clients.get(player.getUniqueId());
        if (protocol != null && protocol == REQUIRED_PROTOCOL) {
            return true;
        }

        requestVersion(player);
        showRequirement(player, protocol);
        return false;
    }

    public void enableRacePhysics(Player player) {
        Integer protocol = clients.get(player.getUniqueId());
        if (protocol == null || protocol != REQUIRED_PROTOCOL) {
            showRequirement(player, protocol);
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

    private void showRequirement(Player player, Integer protocol) {
        player.sendMessage(Component.empty());
        if (protocol == null) {
            player.sendMessage(Component.text("Per partecipare alla gara serve la mod client IceBoatRacing per Minecraft 26.2.",
                    NamedTextColor.RED));
        } else {
            player.sendMessage(Component.text("La mod client IceBoatRacing non è aggiornata per questo server.",
                    NamedTextColor.RED));
        }
        player.sendMessage(downloadLink("[1] Scarica la mod IceBoatRacing per 26.2", CLIENT_MOD_URL));
        player.sendMessage(downloadLink("[2] Scarica Fabric API per 26.2", FABRIC_API_URL));
        player.sendMessage(downloadLink("[3] Installa Fabric Loader 26.2", FABRIC_INSTALLER_URL));
        player.sendMessage(Component.text("Inserisci " + CLIENT_MOD_NAME + " nella cartella mods del profilo Fabric.",
                NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Dopo aver riavviato Minecraft verrà riconosciuta automaticamente.",
                NamedTextColor.GREEN));
        player.sendMessage(Component.empty());
    }

    private Component downloadLink(String label, String url) {
        return Component.text(label, NamedTextColor.AQUA)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text("Clicca per aprire il download", NamedTextColor.GREEN)));
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
