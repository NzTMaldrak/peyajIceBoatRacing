package peyaj.iceboatracing.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RacePhysics {

    // Original race behaviour: a boat can step up exactly one full block.
    public static final float STEP_HEIGHT = 1.0F;

    private static volatile boolean enabled;

    private RacePhysics() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isControlledRaceBoat(Entity entity) {
        if (!enabled || !(entity instanceof AbstractBoat)) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getVehicle() == entity;
    }

    /**
     * Enables the native step only when the collision in front of the boat is
     * made of ice. Walls and decorations made from other blocks remain solid.
     */
    public static boolean canClimbIce(Entity entity) {
        if (!isControlledRaceBoat(entity)) {
            return false;
        }

        Vec3 movement = entity.getDeltaMovement();
        if (movement.horizontalDistanceSqr() < 1.0E-8) {
            return false;
        }

        AABB box = entity.getBoundingBox();
        AABB path = box.expandTowards(movement.x, 0.0, movement.z).inflate(0.001);
        int minX = (int) Math.floor(path.minX);
        int maxX = (int) Math.floor(path.maxX);
        int minY = (int) Math.floor(box.minY + 0.001);
        int maxY = (int) Math.floor(box.minY + STEP_HEIGHT);
        int minZ = (int) Math.floor(path.minZ);
        int maxZ = (int) Math.floor(path.maxZ);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    if (entity.level().getBlockState(pos).is(BlockTags.ICE)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
