package peyaj.hologram;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Hologram {

    private final JavaPlugin plugin;
    private final String id;
    private final NamespacedKey key;
    private Location location;
    private List<String> lines = new ArrayList<>();
    private UUID textDisplayUuid;

    public Hologram(JavaPlugin plugin, String id, Location location) {
        this.plugin = plugin;
        this.id = id;
        this.key = new NamespacedKey(plugin, "hologram_id");
        this.location = location.clone();
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location.clone();
    }

    private Component cachedComponent = null;

    public synchronized void setLocation(Location newLoc) {
        if (newLoc == null || newLoc.getWorld() == null) return;
        if (this.location != null && this.location.equals(newLoc)) return;
        this.location = newLoc.clone();
        TextDisplay display = getOrSpawnDisplay();
        if (display != null && display.isValid()) {
            display.teleport(this.location);
        }
    }

    public synchronized void setLines(List<String> lines) {
        List<String> newLines = (lines != null) ? new ArrayList<>(lines) : new ArrayList<>();
        if (this.lines.equals(newLines) && cachedComponent != null) return;
        this.lines = newLines;
        this.cachedComponent = buildComponent(this.lines);
        TextDisplay display = getOrSpawnDisplay();
        if (display != null && display.isValid()) {
            display.text(this.cachedComponent);
        }
    }

    public synchronized void remove() {
        TextDisplay display = getDisplayEntity();
        if (display != null && display.isValid()) {
            display.remove();
        }
        textDisplayUuid = null;
    }

    public synchronized TextDisplay getOrSpawnDisplay() {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        // Try getting existing entity from stored UUID
        if (textDisplayUuid != null) {
            Entity entity = Bukkit.getEntity(textDisplayUuid);
            if (entity instanceof TextDisplay textDisplay && textDisplay.isValid()) {
                return textDisplay;
            }
        }

        // Search for existing tagged TextDisplay entity near the location
        for (Entity nearby : location.getWorld().getNearbyEntities(location, 2.0, 2.0, 2.0)) {
            if (nearby instanceof TextDisplay textDisplay) {
                String taggedId = textDisplay.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                if (Objects.equals(taggedId, id)) {
                    textDisplayUuid = textDisplay.getUniqueId();
                    textDisplay.setSeeThrough(true);
                    return textDisplay;
                }
            }
        }

        // Spawn new TextDisplay entity
        try {
            TextDisplay display = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
            display.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(true);
            display.setBackgroundColor(Color.fromARGB(100, 0, 0, 0)); // Sleek semi-transparent dark background
            display.setSeeThrough(true); // Ensures clear rendering through translucent blocks (ice, water, glass)
            display.text(buildComponent(lines));
            this.textDisplayUuid = display.getUniqueId();
            return display;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn TextDisplay hologram for " + id + ": " + e.getMessage());
            return null;
        }
    }

    private TextDisplay getDisplayEntity() {
        if (textDisplayUuid != null) {
            Entity entity = Bukkit.getEntity(textDisplayUuid);
            if (entity instanceof TextDisplay textDisplay) {
                return textDisplay;
            }
        }
        return null;
    }

    private Component buildComponent(List<String> textLines) {
        if (textLines.isEmpty()) {
            return Component.empty();
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < textLines.size(); i++) {
            builder.append(textLines.get(i));
            if (i < textLines.size() - 1) {
                builder.append("\n");
            }
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(builder.toString());
    }
}
