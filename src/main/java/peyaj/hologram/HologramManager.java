package peyaj.hologram;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HologramManager {

    private final JavaPlugin plugin;
    private final Map<String, Hologram> holograms = new ConcurrentHashMap<>();

    public HologramManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Hologram getHologram(String id) {
        if (id == null) return null;
        return holograms.get(id.toLowerCase());
    }

    public Hologram createOrUpdateHologram(String id, Location location, List<String> lines) {
        if (id == null || location == null) return null;
        String key = id.toLowerCase();
        Hologram holo = holograms.computeIfAbsent(key, k -> new Hologram(plugin, id, location));
        holo.setLocation(location);
        holo.setLines(lines);
        return holo;
    }

    public void removeHologram(String id) {
        if (id == null) return;
        Hologram holo = holograms.remove(id.toLowerCase());
        if (holo != null) {
            holo.remove();
        }
    }

    public void removeAll() {
        for (Hologram holo : holograms.values()) {
            holo.remove();
        }
        holograms.clear();
    }
}
