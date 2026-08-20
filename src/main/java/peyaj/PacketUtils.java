package peyaj;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PacketUtils {

    // Spawn a fake boat for a specific player via PacketEvents
    public static int spawnFakeBoat(Player observer, Location loc, UUID uuid) {
        if (observer == null || !observer.isOnline() || loc == null) {
            return -1;
        }

        int entityId = ThreadLocalRandom.current().nextInt(100000, 999999);

        Vector3d position = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(uuid),
                EntityTypes.OAK_BOAT,
                position,
                loc.getPitch(),
                loc.getYaw(),
                loc.getYaw(),
                0,
                Optional.empty()
        );

        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(observer, spawnPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return entityId;
    }

    // Move the fake boat (Teleport packet) via PacketEvents
    public static void moveFakeBoat(Player observer, int entityId, Location loc) {
        if (observer == null || !observer.isOnline() || loc == null || entityId <= 0) {
            return;
        }

        Vector3d position = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
        WrapperPlayServerEntityTeleport teleportPacket = new WrapperPlayServerEntityTeleport(
                entityId,
                position,
                loc.getYaw(),
                loc.getPitch(),
                true
        );

        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(observer, teleportPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Destroy the fake boat via PacketEvents
    public static void destroyFakeEntity(Player observer, int entityId) {
        if (observer == null || !observer.isOnline() || entityId <= 0) {
            return;
        }

        WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);

        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(observer, destroyPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
