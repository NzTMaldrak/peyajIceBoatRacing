package peyaj.arena;

/**
 * Spectator camera modes for race viewing.
 */
public enum SpectatorMode {
    /** Free flying around the arena */
    FREE_FLY("Free Fly", "Fly freely around the arena"),

    /** Automatically follow the race leader */
    FOLLOW_LEADER("Follow Leader", "Camera follows 1st place"),

    /** Follow a specific player */
    FOLLOW_PLAYER("Follow Player", "Pick a racer to follow"),

    /** Cinematic auto-rotating view */
    CINEMATIC("Cinematic", "Epic cinematic camera angles");

    public final String displayName;
    public final String description;

    SpectatorMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Gets the next mode in the cycle.
     */
    public SpectatorMode next() {
        int idx = this.ordinal() + 1;
        if (idx >= values().length)
            idx = 0;
        return values()[idx];
    }
}
