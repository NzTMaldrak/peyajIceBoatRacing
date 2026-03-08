package peyaj.arena;

/**
 * Represents the current state of a race arena.
 */
public enum RaceState {
    /** Waiting for players in the lobby */
    LOBBY,
    /** Countdown in progress, race about to start */
    STARTING,
    /** Race is actively running */
    ACTIVE
}
