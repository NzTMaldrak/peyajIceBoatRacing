package peyaj.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import peyaj.IceBoatRacing;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Applies temporary anti-cheat exemptions only while a player is in a race. */
public final class AntiCheatHook {

    private final IceBoatRacing plugin;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final boolean grimAvailable;
    private final boolean spartanAvailable;

    public AntiCheatHook(IceBoatRacing plugin) {
        this.plugin = plugin;
        this.grimAvailable = Bukkit.getPluginManager().getPlugin("GrimAC") != null;
        this.spartanAvailable = Bukkit.getPluginManager().getPlugin("Spartan") != null;

        if (grimAvailable)
            plugin.getLogger().info("Integrazione GrimAC attiva per le gare.");
        if (spartanAvailable)
            plugin.getLogger().info("Integrazione Spartan attiva per le gare.");
    }

    public void exempt(Player player) {
        if ((!grimAvailable && !spartanAvailable) || attachments.containsKey(player.getUniqueId()))
            return;

        PermissionAttachment attachment = player.addAttachment(plugin);
        if (grimAvailable) {
            // grim.disabled is the official temporary exemption. The other two
            // permissions also prevent corrections while Grim refreshes its cache.
            attachment.setPermission("grim.disabled", true);
            attachment.setPermission("grim.nosetback", true);
            attachment.setPermission("grim.nomodifypacket", true);
        }
        if (spartanAvailable) {
            attachment.setPermission("spartan.bypass", true);
        }
        attachments.put(player.getUniqueId(), attachment);
        player.recalculatePermissions();
        refreshGrimPermissions(player);
    }

    public void releaseLater(UUID uuid) {
        if (!attachments.containsKey(uuid))
            return;

        // Keep the exemption for the exit teleport and inventory/game-mode restore.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isRacer(uuid))
                release(uuid);
        }, 5L);
    }

    public void release(UUID uuid) {
        PermissionAttachment attachment = attachments.remove(uuid);
        if (attachment == null)
            return;

        try {
            attachment.remove();
        } catch (IllegalArgumentException ignored) {
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.recalculatePermissions();
            refreshGrimPermissions(player);
        }
    }

    public void clearAll() {
        for (UUID uuid : attachments.keySet().toArray(UUID[]::new))
            release(uuid);
    }

    /**
     * Grim caches permission states. Refresh through its API via reflection so this
     * plugin remains optional and can still load when Grim is not installed.
     */
    private void refreshGrimPermissions(Player player) {
        if (!grimAvailable)
            return;

        Plugin grim = Bukkit.getPluginManager().getPlugin("GrimAC");
        if (grim == null)
            return;

        try {
            ClassLoader loader = grim.getClass().getClassLoader();
            Class<?> providerClass = Class.forName("ac.grim.grimac.api.GrimAPIProvider", true, loader);
            Object api = providerClass.getMethod("get").invoke(null);
            Object grimUser = api.getClass().getMethod("getGrimUser", UUID.class)
                    .invoke(api, player.getUniqueId());
            if (grimUser != null) {
                grimUser.getClass().getMethod("updatePermissions").invoke(grimUser);
                return;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Fall through to the legacy Grim API used by older installations.
        }

        refreshLegacyGrimPermissions(grim, player);
    }

    private void refreshLegacyGrimPermissions(Plugin grim, Player player) {
        try {
            Class<?> apiClass = Class.forName("ac.grim.grimac.GrimAPI", true, grim.getClass().getClassLoader());
            Field instanceField = apiClass.getField("INSTANCE");
            Object api = instanceField.get(null);
            Object manager = api.getClass().getMethod("getPlayerDataManager").invoke(api);

            for (Method method : manager.getClass().getMethods()) {
                if (!method.getName().equals("getPlayer") || method.getParameterCount() != 1)
                    continue;

                Class<?> parameter = method.getParameterTypes()[0];
                Object argument;
                if (parameter == UUID.class)
                    argument = player.getUniqueId();
                else if (parameter.isInstance(player))
                    argument = player;
                else
                    continue;

                Object grimPlayer = method.invoke(manager, argument);
                if (grimPlayer != null)
                    grimPlayer.getClass().getMethod("updatePermissions").invoke(grimPlayer);
                return;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // The permission attachment remains active even if this Grim version has
            // no compatible refresh method.
        }
    }
}
