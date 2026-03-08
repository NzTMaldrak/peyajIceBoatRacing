package peyaj.arena;

/**
 * Represents the type/mode of a race.
 */
public enum RaceType {
    /** Point-to-point race from start to finish */
    DEFAULT,
    /** Multi-lap circuit race */
    LAP,
    /** Elimination mode - last place eliminated each lap */
    ELIMINATION
}
