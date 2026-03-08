package peyaj.replay;

import org.bukkit.Location;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holds all data for a race replay.
 */
public class ReplayData implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String arenaName;
    private final long timestamp;
    private final List<String> playerNames;
    private final Map<String, Long> finishTimesMs;
    private final List<ReplayFrame> frames;
    private final int totalTicks;

    public ReplayData(String arenaName, long timestamp) {
        this.arenaName = arenaName;
        this.timestamp = timestamp;
        this.playerNames = new ArrayList<>();
        this.finishTimesMs = new HashMap<>();
        this.frames = new ArrayList<>();
        this.totalTicks = 0;
    }

    public String getArenaName() {
        return arenaName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<String> getPlayerNames() {
        return playerNames;
    }

    public Map<String, Long> getFinishTimesMs() {
        return finishTimesMs;
    }

    public List<ReplayFrame> getFrames() {
        return frames;
    }

    public int getTotalTicks() {
        return frames.size();
    }

    public void addPlayer(String name) {
        if (!playerNames.contains(name)) {
            playerNames.add(name);
        }
    }

    public void setFinishTime(String playerName, long timeMs) {
        finishTimesMs.put(playerName, timeMs);
    }

    public void addFrame(ReplayFrame frame) {
        frames.add(frame);
    }

    /**
     * Gets a unique ID for this replay based on arena and timestamp.
     */
    public String getReplayId() {
        return arenaName.toLowerCase() + "_" + timestamp;
    }

    /**
     * Represents a single frame of the replay (1 tick = 50ms).
     */
    public static class ReplayFrame implements Serializable {
        private static final long serialVersionUID = 1L;

        private final int tickNumber;
        private final Map<String, PlayerFrameData> playerData;

        public ReplayFrame(int tickNumber) {
            this.tickNumber = tickNumber;
            this.playerData = new HashMap<>();
        }

        public int getTickNumber() {
            return tickNumber;
        }

        public Map<String, PlayerFrameData> getPlayerData() {
            return playerData;
        }

        public void addPlayerData(String playerName, PlayerFrameData data) {
            playerData.put(playerName, data);
        }
    }

    /**
     * Holds position and state data for a single player in a single frame.
     */
    public static class PlayerFrameData implements Serializable {
        private static final long serialVersionUID = 1L;

        private final double x, y, z;
        private final float yaw, pitch;
        private final boolean finished;
        private final int currentCheckpoint;
        private final int currentLap;

        public PlayerFrameData(Location loc, boolean finished, int checkpoint, int lap) {
            this.x = loc.getX();
            this.y = loc.getY();
            this.z = loc.getZ();
            this.yaw = loc.getYaw();
            this.pitch = loc.getPitch();
            this.finished = finished;
            this.currentCheckpoint = checkpoint;
            this.currentLap = lap;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public float getYaw() {
            return yaw;
        }

        public float getPitch() {
            return pitch;
        }

        public boolean isFinished() {
            return finished;
        }

        public int getCurrentCheckpoint() {
            return currentCheckpoint;
        }

        public int getCurrentLap() {
            return currentLap;
        }

        public Location toLocation(org.bukkit.World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }
    }
}
