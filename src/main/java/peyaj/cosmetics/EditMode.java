package peyaj.cosmetics;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Represents the edit mode for the arena wand tool.
 */
public enum EditMode {
    SPAWN("Spawn Points", NamedTextColor.GREEN),
    CHECKPOINT("Checkpoints", NamedTextColor.RED),
    FINISH_1("Finish Pos 1", NamedTextColor.AQUA),
    FINISH_2("Finish Pos 2", NamedTextColor.AQUA),
    LOBBY("Pre-Lobby", NamedTextColor.GOLD),
    MAIN_LOBBY("Main Lobby", NamedTextColor.YELLOW),
    LEADERBOARD("Leaderboard Holo", NamedTextColor.LIGHT_PURPLE);

    public final String displayName;
    public final NamedTextColor color;

    EditMode(String displayName, NamedTextColor color) {
        this.displayName = displayName;
        this.color = color;
    }

    public EditMode next() {
        int idx = this.ordinal() + 1;
        if (idx >= values().length)
            idx = 0;
        return values()[idx];
    }
}
