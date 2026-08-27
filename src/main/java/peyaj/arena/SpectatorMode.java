package peyaj.arena;

/**
 * Spectator camera modes for race viewing.
 */
public enum SpectatorMode {
    /** Free flying around the arena */
    FREE_FLY("Volo libero", "Vola liberamente nell'arena"),

    /** Automatically follow the race leader */
    FOLLOW_LEADER("Segui il leader", "La visuale segue il primo classificato"),

    /** Follow a specific player */
    FOLLOW_PLAYER("Segui pilota", "Scegli un pilota da seguire"),

    /** Cinematic auto-rotating view */
    CINEMATIC("Cinematica", "Inquadrature automatiche della gara");

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
