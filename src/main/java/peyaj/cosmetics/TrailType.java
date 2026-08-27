package peyaj.cosmetics;

import org.bukkit.Material;
import org.bukkit.Particle;

/**
 * Represents available particle trail types for boats.
 */
public enum TrailType {
    NONE("Nessuna", Material.BARRIER, null, null),
    SMOKE("Scarico", Material.CAMPFIRE, Particle.CAMPFIRE_COSY_SMOKE, null),
    FLAME("Fiamma", Material.BLAZE_POWDER, Particle.FLAME, "race.trail.flame"),
    HEARTS("Amore", Material.POPPY, Particle.HEART, "race.trail.hearts"),
    NOTES("Musica", Material.NOTE_BLOCK, Particle.NOTE, "race.trail.notes"),
    SPARKS("Scintille", Material.FIREWORK_ROCKET, Particle.FIREWORK, "race.trail.sparks"),
    MAGIC("Magia", Material.ENCHANTING_TABLE, Particle.ENCHANT, "race.trail.magic"),
    ENDER("Vuoto", Material.ENDER_PEARL, Particle.DRAGON_BREATH, "race.trail.ender"),
    RAINBOW("Arcobaleno", Material.NAME_TAG, Particle.DUST, "race.trail.rainbow"),
    // NEW TRAILS
    SOUL_FIRE("Fuoco delle anime", Material.SOUL_CAMPFIRE, Particle.SOUL_FIRE_FLAME, "race.trail.soulfire"),
    WATER("Acqua", Material.WATER_BUCKET, Particle.FALLING_WATER, "race.trail.water"),
    SNOW("Neve", Material.SNOWBALL, Particle.SNOWFLAKE, "race.trail.snow"),
    CHERRY("Fiori di ciliegio", Material.CHERRY_LEAVES, Particle.CHERRY_LEAVES, "race.trail.cherry"),
    ELECTRIC("Elettrica", Material.LIGHTNING_ROD, Particle.ELECTRIC_SPARK, "race.trail.electric"),
    SCULK("Sculk", Material.SCULK, Particle.SCULK_CHARGE_POP, "race.trail.sculk"),
    HONEY("Miele", Material.HONEY_BLOCK, Particle.DRIPPING_HONEY, "race.trail.honey"),
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
