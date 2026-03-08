package peyaj.cosmetics;

import org.bukkit.Material;
import org.bukkit.Particle;

/**
 * Represents available particle trail types for boats.
 */
public enum TrailType {
    NONE("None", Material.BARRIER, null, null),
    SMOKE("Exhaust", Material.CAMPFIRE, Particle.CAMPFIRE_COSY_SMOKE, null),
    FLAME("Flame", Material.BLAZE_POWDER, Particle.FLAME, "race.trail.flame"),
    HEARTS("Love", Material.POPPY, Particle.HEART, "race.trail.hearts"),
    NOTES("Music", Material.NOTE_BLOCK, Particle.NOTE, "race.trail.notes"),
    SPARKS("Sparks", Material.FIREWORK_ROCKET, Particle.FIREWORK, "race.trail.sparks"),
    MAGIC("Magic", Material.ENCHANTING_TABLE, Particle.ENCHANT, "race.trail.magic"),
    ENDER("Void", Material.ENDER_PEARL, Particle.DRAGON_BREATH, "race.trail.ender"),
    RAINBOW("Rainbow", Material.NAME_TAG, Particle.DUST, "race.trail.rainbow"),
    // NEW TRAILS
    SOUL_FIRE("Soul Fire", Material.SOUL_CAMPFIRE, Particle.SOUL_FIRE_FLAME, "race.trail.soulfire"),
    WATER("Water", Material.WATER_BUCKET, Particle.FALLING_WATER, "race.trail.water"),
    SNOW("Snow", Material.SNOWBALL, Particle.SNOWFLAKE, "race.trail.snow"),
    CHERRY("Cherry Blossom", Material.CHERRY_LEAVES, Particle.CHERRY_LEAVES, "race.trail.cherry"),
    ELECTRIC("Electric", Material.LIGHTNING_ROD, Particle.ELECTRIC_SPARK, "race.trail.electric"),
    SCULK("Sculk", Material.SCULK, Particle.SCULK_CHARGE_POP, "race.trail.sculk"),
    HONEY("Honey", Material.HONEY_BLOCK, Particle.DRIPPING_HONEY, "race.trail.honey"),
    LAVA("Lava", Material.LAVA_BUCKET, Particle.DRIPPING_LAVA, "race.trail.lava");

    public final String displayName;
    public final Material icon;
    public final Particle particle;
    public final String permission;

    TrailType(String displayName, Material icon, Particle particle, String permission) {
        this.displayName = displayName;
        this.icon = icon;
        this.particle = particle;
        this.permission = permission;
    }
}
