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
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (TextDisplay display : new ArrayList<>(world.getEntitiesByClass(TextDisplay.class))) {
                if (isThisHologram(display)) {
                    display.remove();
                }
            }
        }
        textDisplayUuid = null;
    }

    public synchronized TextDisplay getOrSpawnDisplay() {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        // Make sure the configured chunk is loaded before looking for persisted displays.
        if (!location.getChunk().isLoaded()) {
            location.getChunk().load();
        }

        TextDisplay selected = null;

        // Prefer the entity already tracked by this instance.
        if (textDisplayUuid != null) {
            Entity entity = Bukkit.getEntity(textDisplayUuid);
            if (entity instanceof TextDisplay textDisplay && textDisplay.isValid() && isThisHologram(textDisplay)) {
                selected = textDisplay;
            }
        }

        // Find persisted copies globally. Keep one and remove every duplicate with the same ID.
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (TextDisplay display : new ArrayList<>(world.getEntitiesByClass(TextDisplay.class))) {
                if (!isThisHologram(display))
                    continue;
                if (selected == null) {
                    selected = display;
                } else if (!selected.getUniqueId().equals(display.getUniqueId())) {
                    display.remove();
                }
            }
        }

        if (selected != null && selected.isValid()) {
            textDisplayUuid = selected.getUniqueId();
            selected.setBillboard(Display.Billboard.CENTER);
            selected.setShadowed(true);
            selected.setBackgroundColor(Color.fromARGB(100, 0, 0, 0));
            selected.setSeeThrough(true);
            selected.teleport(location);
            selected.text(cachedComponent != null ? cachedComponent : buildComponent(lines));
            return selected;
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

    private boolean isThisHologram(TextDisplay display) {
        String taggedId = display.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return Objects.equals(taggedId, id);
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
