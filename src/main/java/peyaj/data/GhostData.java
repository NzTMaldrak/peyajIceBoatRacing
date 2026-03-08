package peyaj.data;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds ghost recording data for time trial replays.
 */
public class GhostData {
    public final List<Location> points = new ArrayList<>();
    public final String playerName;
    public final long timeMs;

    public GhostData(String playerName, long timeMs) {
        this.playerName = playerName;
        this.timeMs = timeMs;
    }
}
