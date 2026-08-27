package peyaj.cosmetics;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Represents the edit mode for the arena wand tool.
 */
public enum EditMode {
    SPAWN("Punti di partenza", NamedTextColor.GREEN),
    CHECKPOINT("Checkpoints", NamedTextColor.RED),
    FINISH_1("Traguardo punto 1", NamedTextColor.AQUA),
    FINISH_2("Traguardo punto 2", NamedTextColor.AQUA),
    LOBBY("Lobby pre-gara", NamedTextColor.GOLD),
    MAIN_LOBBY("Lobby principale", NamedTextColor.YELLOW),
    LEADERBOARD("Ologramma classifica", NamedTextColor.LIGHT_PURPLE);

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
