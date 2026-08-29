package peyaj;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import peyaj.arena.RaceState;
import peyaj.cosmetics.EditMode;

public class RaceListener implements Listener {

    private final IceBoatRacing plugin;

    public RaceListener(IceBoatRacing plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p))
            return;

        // Cancel all inventory clicks for racers to prevent moving lobby/spectator
        // items
        if (plugin.isRacer(p.getUniqueId())) {
            e.setCancelled(true);
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null)
            return;

        if (clicked.getType() == Material.RED_DYE) {
            String displayName = clicked.getItemMeta().displayName() != null
                    ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                            .serialize(clicked.getItemMeta().displayName())
                    : "";
            if (displayName.contains("Ricomincia prova")) {
                RaceArena arena = plugin.getPlayerArena(p.getUniqueId());
                if (arena != null && arena.isTimeTrial()) {
                    arena.resetTimeTrial(p);
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            RaceArena arena = plugin.getPlayerArena(p.getUniqueId());
            if (arena != null && arena.getState() == RaceState.ACTIVE) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(org.bukkit.event.entity.FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p) {
            RaceArena arena = plugin.getPlayerArena(p.getUniqueId());
            if (arena != null && arena.getState() != RaceState.LOBBY) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent e) {
        if (plugin.isRacer(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        if (plugin.isRacer(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemDrop(org.bukkit.event.player.PlayerDropItemEvent e) {
        if (plugin.isRacer(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (plugin.isRacer(p.getUniqueId())) {
            String cmd = e.getMessage().toLowerCase();
            if (!cmd.startsWith("/race") && !cmd.startsWith("/iceboat") && !cmd.startsWith("/checkpoint")
                    && !cmd.startsWith("/cp") && !cmd.startsWith("/stuck")) {
                if (!p.hasPermission("race.admin")) {
                    e.setCancelled(true);
                    p.sendMessage(Component.text("Non puoi usare altri comandi mentre sei in gara!", NamedTextColor.RED));
                }
            }
        }
    }

    @EventHandler
    public void onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent e) {
        if (plugin.isRacer(e.getPlayer().getUniqueId())) {
            if (e.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL ||
                    e.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!plugin.inputMode.containsKey(p.getUniqueId()))
            return;

        String mode = plugin.inputMode.remove(p.getUniqueId());
        e.setCancelled(true);
        String input = e.getMessage();

        if (mode.equals("create_arena")) {
            if (input.equalsIgnoreCase("annulla") || input.equalsIgnoreCase("cancel")) {
                Bukkit.getScheduler().runTask(plugin,
                        () -> p.sendMessage(Component.text("Creazione dell'arena annullata.", NamedTextColor.YELLOW)));
                return;
            }
            String name = input.replace(" ", "_");
            if (name.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin,
                        () -> p.sendMessage(Component.text("Il nome dell'arena non può essere vuoto!", NamedTextColor.RED)));
                return;
            }
            if (plugin.getArena(name) != null) {
                Bukkit.getScheduler().runTask(plugin,
                        () -> p.sendMessage(Component.text("Questa arena esiste già!", NamedTextColor.RED)));
                return;
            }

            RaceArena arena = new RaceArena(name, plugin);
            plugin.addArena(name, arena);
            plugin.editorArena.put(p.getUniqueId(), name);
            plugin.editorMode.put(p.getUniqueId(), EditMode.SPAWN);

            Bukkit.getScheduler().runTask(plugin, () -> {
                p.sendMessage(Component.text("Arena '" + name + "' creata! Usa la bacchetta gara per configurarla.",
                        NamedTextColor.GREEN));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
            });
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player p))
            return;

        RaceArena arena = plugin.getPlayerArena(p.getUniqueId());
        if (arena == null)
            return;
        if (arena.isRespawning(p.getUniqueId()))
            return;
        if (!(e.getDismounted() instanceof Boat))
            return;
        if (arena.getState() == RaceState.ACTIVE && !arena.isSpectator(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onVehicleDamage(org.bukkit.event.vehicle.VehicleDamageEvent e) {
        if (e.getVehicle() instanceof Boat boat && !boat.getPassengers().isEmpty()) {
            if (boat.getPassengers().get(0) instanceof Player p) {
                if (plugin.isRacer(p.getUniqueId())) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onVehicleDestroy(org.bukkit.event.vehicle.VehicleDestroyEvent e) {
        if (e.getVehicle() instanceof Boat boat && !boat.getPassengers().isEmpty()) {
            if (boat.getPassengers().get(0) instanceof Player p) {
                if (plugin.isRacer(p.getUniqueId())) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        RaceArena arena = plugin.getPlayerArena(p.getUniqueId());
        if (arena != null) {
            arena.removePlayer(p);
        }
        plugin.editorArena.remove(p.getUniqueId());
        plugin.editorMode.remove(p.getUniqueId());
        plugin.activeVisualizers.remove(p.getUniqueId());
        plugin.inputMode.remove(p.getUniqueId());
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!e.isSneaking())
            return;
        RaceArena arena = plugin.getPlayerArena(p.getUniqueId());
        if (arena != null && arena.isFirstPersonSpectating(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> arena.stopFirstPersonSpectating(p));
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();

        if (item == null || item.getItemMeta() == null)
            return;

        // Check for race wand
        if (item.getItemMeta().getPersistentDataContainer().has(plugin.guiManager.raceWandKey,
                PersistentDataType.BYTE)) {
            e.setCancelled(true);
            handleWand(p, e.getAction(), e.getClickedBlock());
            return;
        }

        if (item.getItemMeta().getPersistentDataContainer().has(plugin.guiManager.readyKey,
                PersistentDataType.BYTE) && e.getAction().name().contains("RIGHT")) {
            RaceArena arena = plugin.getPlayerArena(p.getUniqueId());
            if (arena != null) {
                e.setCancelled(true);
                arena.toggleReady(p);
            }
            return;
        }

        // Check for reset run item
        if (item.getType() == Material.RED_DYE) {
            String displayName = item.getItemMeta().displayName() != null
                    ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                            .serialize(item.getItemMeta().displayName())
                    : "";
            if (displayName.contains("Ricomincia prova")) {
                RaceArena arena = plugin.getPlayerArena(p.getUniqueId());
                if (arena != null && arena.isTimeTrial()) {
                    e.setCancelled(true);
                    arena.resetTimeTrial(p);
                }
            }
        }

        // Spectator items
        if (plugin.isRacer(p.getUniqueId())) {
            RaceArena arena = plugin.getPlayerArena(p.getUniqueId());

            if (arena != null && arena.isSpectator(p.getUniqueId())) {
                if (e.getAction().name().contains("RIGHT")) {
                    if (item.getType() == Material.COMPASS) {
                        e.setCancelled(true);
                        plugin.guiManager.openSpectatorMenu(p, arena);
                    } else if (item.getType() == Material.BARRIER) {
                        e.setCancelled(true);
                        arena.removePlayer(p);
                    }
                }
                return;
            }

            if (arena != null && e.getAction().name().contains("RIGHT")) {
                if (arena.getState() == RaceState.ACTIVE && item.getType() == Material.RECOVERY_COMPASS) {
                    e.setCancelled(true);
                    p.performCommand("checkpoint");
                } else if ((arena.getState() == RaceState.LOBBY || arena.getState() == RaceState.STARTING)
                        && item.getType() == Material.COMPASS) {
                    e.setCancelled(true);
                    plugin.guiManager.openMainMenu(p);
                } else if (item.getType() == Material.BARRIER) {
                    e.setCancelled(true);
                    p.performCommand("race leave");
                }
            }
        }
    }

    private void handleWand(Player p, Action action, Block clickedBlock) {
        String arenaName = plugin.editorArena.get(p.getUniqueId());
        EditMode mode = plugin.editorMode.get(p.getUniqueId());

        if (arenaName == null || mode == null) {
            p.sendMessage(Component.text("Prima scegli un'arena da modificare nel pannello amministratore!", NamedTextColor.RED));
            return;
        }

        RaceArena arena = plugin.getArena(arenaName);
        if (arena == null) {
            p.sendMessage(Component.text("Arena '" + arenaName + "' non trovata.", NamedTextColor.RED));
            return;
        }

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (p.isSneaking() && clickedBlock != null) {
                // Shift+Right-Click: Remove point
                handleRemovePoint(p, arena, mode, clickedBlock);
            } else {
                // Right-Click: Cycle mode
                EditMode next = mode.next();
                plugin.editorMode.put(p.getUniqueId(), next);
                p.sendMessage(Component.text("Modalità bacchetta: " + next.displayName, next.color));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            }
            return;
        }

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            // Left-Click: Add point
            Location playerLocation = p.getLocation();
            Location loc = clickedBlock != null
                    ? new Location(clickedBlock.getWorld(), clickedBlock.getX() + 0.5,
                            clickedBlock.getY() + 1.0, clickedBlock.getZ() + 0.5,
                            playerLocation.getYaw(), playerLocation.getPitch())
                    : playerLocation;

            switch (mode) {
                case SPAWN -> {
                    arena.addSpawn(loc);
                    p.sendMessage(Component.text("Partenza aggiunta #" + arena.getSpawns().size(), NamedTextColor.GREEN));
                }
                case CHECKPOINT -> {
                    arena.addCheckpoint(loc);
                    p.sendMessage(
                            Component.text("Checkpoint aggiunto #" + arena.getCheckpoints().size(), NamedTextColor.RED));
                }
                case FINISH_1 -> {
                    arena.setFinishLine(loc, arena.getFinishPos2());
                    p.sendMessage(Component.text("Punto 1 del traguardo impostato", NamedTextColor.AQUA));
                }
                case FINISH_2 -> {
                    arena.setFinishLine(arena.getFinishPos1(), loc);
                    p.sendMessage(Component.text("Punto 2 del traguardo impostato", NamedTextColor.AQUA));
                }
                case LOBBY -> {
                    arena.setLobby(loc);
                    p.sendMessage(Component.text("Lobby pre-gara impostata", NamedTextColor.GOLD));
                }
                case MAIN_LOBBY -> {
                    arena.setMainLobby(loc);
                    p.sendMessage(Component.text("Lobby principale post-gara impostata", NamedTextColor.YELLOW));
                }
                case LEADERBOARD -> {
                    arena.setLeaderboardLocation(loc);
                    p.sendMessage(Component.text("Posizione dell'ologramma della classifica impostata", NamedTextColor.LIGHT_PURPLE));
                }
            }
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
            plugin.saveArenas();
        }
    }

    private void handleRemovePoint(Player p, RaceArena arena, EditMode mode, Block clickedBlock) {
        Location loc = clickedBlock.getLocation();
        boolean removed = false;

        switch (mode) {
            case SPAWN -> removed = arena.removeNodeAtBlock(arena.getSpawns(), loc);
            case CHECKPOINT -> removed = arena.removeNodeAtBlock(arena.getCheckpoints(), loc);
            default -> p.sendMessage(Component.text("Puoi rimuovere solo partenze e checkpoint!", NamedTextColor.RED));
        }

        if (removed) {
            p.sendMessage(Component.text("Punto rimosso!", NamedTextColor.YELLOW));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            plugin.saveArenas();
        } else {
            p.sendMessage(Component.text("Nessun punto trovato in questa posizione.", NamedTextColor.GRAY));
        }
    }
}
