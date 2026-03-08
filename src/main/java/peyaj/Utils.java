package peyaj;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import peyaj.cosmetics.TrailType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Utils {

    public static void tickVisualizers(IceBoatRacing plugin) {
        for (Map.Entry<UUID, String> entry : plugin.activeVisualizers.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline())
                continue;
            RaceArena arena = plugin.getArena(entry.getValue());
            if (arena == null)
                continue;

            // Draw Spawns
            for (Location loc : arena.getSpawns()) {
                if (loc != null && loc.getWorld() != null && loc.getWorld().equals(p.getWorld())) {
                    drawCircle(p, loc, 1.0, Color.GREEN);
                }
            }
            // Draw Checkpoints
            double radius = plugin.checkpointRadius;
            int index = 1;
            for (Location loc : arena.getCheckpoints()) {
                if (loc != null && loc.getWorld() != null && loc.getWorld().equals(p.getWorld())) {
                    drawCircle(p, loc, radius, Color.RED);
                    p.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1.5, 0), 1, 0, 0, 0, 0);
                }
                index++;
            }
            // Draw Finish Box
            BoundingBox box = arena.getFinishBox();
            if (box != null && arena.getFinishPos1() != null && arena.getFinishPos1().getWorld() != null
                    && arena.getFinishPos1().getWorld().equals(p.getWorld())) {
                drawBox(p, box);
            }
            // Draw Lobby
            Location lobby = arena.getLobby();
            if (lobby != null && lobby.getWorld() != null && lobby.getWorld().equals(p.getWorld())) {
                drawCircle(p, lobby, 0.5, Color.YELLOW);
            }
            // Draw Main Lobby
            Location mainLobby = arena.getMainLobby();
            if (mainLobby != null && mainLobby.getWorld() != null && mainLobby.getWorld().equals(p.getWorld())) {
                drawCircle(p, mainLobby, 0.5, Color.ORANGE);
            }
            // Draw Leaderboard
            Location lb = arena.getLeaderboardLocation();
            if (lb != null && lb.getWorld() != null && lb.getWorld().equals(p.getWorld())) {
                drawCircle(p, lb, 0.3, Color.PURPLE);
            }
        }
    }

    public static void drawLine(Player p, Location p1, Location p2, Color color) {
        Vector start = p1.toVector();
        Vector direction = p2.toVector().subtract(start).normalize().multiply(0.3);
        double distance = p1.distance(p2);
        for (double d = 0; d < distance; d += 0.3) {
            Location point = start.clone().add(direction.clone().multiply(d / 0.3)).toLocation(p1.getWorld());
            p.spawnParticle(Particle.DUST, point, 1, new Particle.DustOptions(color, 0.8f));
        }
    }

    public static void drawCircle(Player p, Location center, double radius, Color color) {
        for (int i = 0; i < 20; i++) {
            double angle = 2 * Math.PI * i / 20;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
            p.spawnParticle(Particle.DUST, point, 1, new Particle.DustOptions(color, 1f));
        }
    }

    public static void drawBox(Player p, BoundingBox box) {
        double step = 0.5;
        double minX = box.getMinX(), maxX = box.getMaxX();
        double minY = box.getMinY(), maxY = box.getMaxY();
        double minZ = box.getMinZ(), maxZ = box.getMaxZ();
        for (double x = minX; x <= maxX; x += step) {
            p.spawnParticle(Particle.DUST, new Location(p.getWorld(), x, minY, minZ), 1,
                    new Particle.DustOptions(Color.AQUA, 1f));
            p.spawnParticle(Particle.DUST, new Location(p.getWorld(), x, minY, maxZ), 1,
                    new Particle.DustOptions(Color.AQUA, 1f));
            p.spawnParticle(Particle.DUST, new Location(p.getWorld(), x, maxY, minZ), 1,
                    new Particle.DustOptions(Color.AQUA, 1f));
            p.spawnParticle(Particle.DUST, new Location(p.getWorld(), x, maxY, maxZ), 1,
                    new Particle.DustOptions(Color.AQUA, 1f));
        }
        for (double y = minY; y <= maxY; y += step) {
            p.spawnParticle(Particle.DUST, new Location(p.getWorld(), minX, y, minZ), 1,
                    new Particle.DustOptions(Color.AQUA, 1f));
            p.spawnParticle(Particle.DUST, new Location(p.getWorld(), maxX, y, minZ), 1,
                    new Particle.DustOptions(Color.AQUA, 1f));
        }
    }

    public static Team createTeam(Scoreboard b, String name, String suffix) {
        Team t = b.registerNewTeam(name);
        t.suffix(Component.text(suffix));
        return t;
    }

    // --- BOAT TYPE (deprecated in 1.21.2 but still functional) ---
    @SuppressWarnings("deprecation")
    public static void assignRandomBoatType(Boat boat) {
        Boat.Type[] types = {
                Boat.Type.OAK, Boat.Type.SPRUCE, Boat.Type.BIRCH,
                Boat.Type.JUNGLE, Boat.Type.ACACIA, Boat.Type.DARK_OAK,
                Boat.Type.MANGROVE, Boat.Type.CHERRY, Boat.Type.BAMBOO
        };
        boat.setBoatType(types[ThreadLocalRandom.current().nextInt(types.length)]);
    }

    // --- TIME FORMATTING ---
    public static String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        int minutes = (int) (totalSeconds / 60);
        int seconds = (int) (totalSeconds % 60);
        int ms = (int) ((millis % 1000) / 10);
        return String.format("%d:%02d.%02d", minutes, seconds, ms);
    }

    public static boolean lineSegmentIntersectsSphere(Location p1, Location p2, Location sphereCenter, double radius) {
        if (p1 == null || p2 == null || sphereCenter == null)
            return false;
        if (p1.getWorld() == null || !p1.getWorld().equals(p2.getWorld())
                || !p1.getWorld().equals(sphereCenter.getWorld()))
            return false;
        Vector d = p2.toVector().subtract(p1.toVector());
        Vector f = p1.toVector().subtract(sphereCenter.toVector());
        double a = d.dot(d);
        double b = 2 * f.dot(d);
        double c = f.dot(f) - radius * radius;
        double discriminant = b * b - 4 * a * c;
        if (discriminant < 0)
            return false;
        discriminant = Math.sqrt(discriminant);
        double t1 = (-b - discriminant) / (2 * a);
        double t2 = (-b + discriminant) / (2 * a);
        return (t1 >= 0 && t1 <= 1) || (t2 >= 0 && t2 <= 1);
    }

    // --- TRAIL LOGIC WITH ALL NEW TRAILS ---
    public static void spawnTrailParticles(Player p, Boat boat, TrailType trail) {
        if (trail == null || trail == TrailType.NONE || trail.particle == null)
            return;
        Location loc = boat.getLocation().clone().add(0, 0.3, 0);

        switch (trail) {
            case RAINBOW -> {
                // Rainbow uses colored dust
                float hue = (System.currentTimeMillis() % 3000) / 3000f;
                java.awt.Color awtColor = java.awt.Color.getHSBColor(hue, 1f, 1f);
                Color color = Color.fromRGB(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue());
                p.getWorld().spawnParticle(Particle.DUST, loc, 3, 0.2, 0.1, 0.2, 0,
                        new Particle.DustOptions(color, 1.2f));
            }
            case ELECTRIC -> {
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 3, 0.3, 0.2, 0.3, 0.05);
            }
            case SCULK -> {
                p.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, loc, 2, 0.2, 0.1, 0.2, 0);
            }
            case HONEY -> {
                p.getWorld().spawnParticle(Particle.DRIPPING_HONEY, loc.add(0, 0.5, 0), 2, 0.3, 0.1, 0.3, 0);
            }
            case LAVA -> {
                p.getWorld().spawnParticle(Particle.DRIPPING_LAVA, loc.add(0, 0.5, 0), 2, 0.3, 0.1, 0.3, 0);
                p.getWorld().spawnParticle(Particle.LAVA, loc, 1, 0.2, 0.1, 0.2, 0);
            }
            case CHERRY -> {
                p.getWorld().spawnParticle(Particle.CHERRY_LEAVES, loc.add(0, 0.5, 0), 4, 0.3, 0.2, 0.3, 0.02);
            }
            case SNOW -> {
                p.getWorld().spawnParticle(Particle.SNOWFLAKE, loc.add(0, 0.5, 0), 4, 0.3, 0.3, 0.3, 0.02);
            }
            case WATER -> {
                p.getWorld().spawnParticle(Particle.FALLING_WATER, loc.add(0, 0.5, 0), 3, 0.2, 0.1, 0.2, 0);
                p.getWorld().spawnParticle(Particle.SPLASH, loc, 2, 0.3, 0.1, 0.3, 0.1);
            }
            case SOUL_FIRE -> {
                p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 3, 0.2, 0.1, 0.2, 0.02);
            }
            default -> {
                // Standard particle trails
                p.getWorld().spawnParticle(trail.particle, loc, 3, 0.2, 0.1, 0.2, 0.02);
            }
        }
    }
}