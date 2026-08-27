package peyaj;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import peyaj.arena.RaceType;
import peyaj.cosmetics.EditMode;
import peyaj.replay.ReplayData;

import java.util.List;

public class RaceCommand implements CommandExecutor {

    private final IceBoatRacing plugin;

    public RaceCommand(IceBoatRacing plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Solo i giocatori possono usare questo comando.");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        if (cmd.equals("checkpoint")
                || (args.length >= 1 && (args[0].equalsIgnoreCase("cp") || args[0].equalsIgnoreCase("checkpoint")))) {
            handleCheckpointRespawn(p);
            return true;
        }
        if (cmd.equals("racequit")) {
            handleLeave(p);
            return true;
        }
        if (cmd.equals("iceboat") && args.length == 0) {
            plugin.guiManager.openMainMenu(p);
            return true;
        }

        if (args.length < 1) {
            p.sendMessage(plugin.getMessage("race-usage"));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "start" -> handleStart(p, args);
            case "stop" -> handleStop(p, args);
            case "vote" -> handleVote(p);
            case "join" -> handleJoin(p, args);
            case "leave" -> handleLeave(p);
            case "admin" -> handleAdmin(p, args);
            case "replay" -> handleReplay(p, args);
            default -> p.sendMessage(plugin.getMessage("race-usage"));
        }
        return true;
    }

    private void handleCheckpointRespawn(Player p) {
        RaceArena arena = plugin.getPlayerArena(p.getUniqueId());
        if (arena == null) {
            p.sendMessage(plugin.getMessage("not-in-race"));
            return;
        }
        arena.respawnPlayer(p);
    }

    private void handleStart(Player p, String[] args) {
        if (!p.hasPermission("race.admin")) {
            p.sendMessage(plugin.getMessage("no-permission"));
            return;
        }
        if (args.length < 2) {
            p.sendMessage(Component.text("Utilizzo: /race start <arena>", NamedTextColor.RED));
            return;
        }
        String arenaName = args[1];
        RaceArena arena = plugin.getArena(arenaName);
        if (arena == null) {
            p.sendMessage(plugin.getMessage("invalid-arena"));
            return;
        }
        arena.startRace();
        p.sendMessage(Component.text("Gara avviata forzatamente su " + arenaName, NamedTextColor.GREEN));
    }

    private void handleStop(Player p, String[] args) {
        if (!p.hasPermission("race.admin")) {
            p.sendMessage(plugin.getMessage("no-permission"));
            return;
        }
        if (args.length < 2) {
            p.sendMessage(Component.text("Utilizzo: /race stop <arena>", NamedTextColor.RED));
            return;
        }
        String arenaName = args[1];
        RaceArena arena = plugin.getArena(arenaName);
        if (arena == null) {
            p.sendMessage(plugin.getMessage("invalid-arena"));
            return;
        }
        arena.stopRace();
        p.sendMessage(Component.text("Gara interrotta su " + arenaName, NamedTextColor.YELLOW));
    }

    private void handleVote(Player p) {
        if (!plugin.isVoting) {
            p.sendMessage(Component.text("Non c'è alcuna votazione in corso.", NamedTextColor.RED));
            return;
        }
        plugin.guiManager.openVoteMenu(p);
    }

    private void handleJoin(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(Component.text("Utilizzo: /race join <arena>", NamedTextColor.RED));
            return;
        }
        if (plugin.isRacer(p.getUniqueId())) {
            p.sendMessage(Component.text("Sei già in un'arena! Prima usa /race leave.", NamedTextColor.RED));
            return;
        }
        
        String arenaName = args[1];
        RaceArena arena = plugin.getArena(arenaName);
        if (arena == null) {
            p.sendMessage(plugin.getMessage("invalid-arena"));
            return;
        }
        arena.addPlayer(p);
    }

    private void handleLeave(Player p) {
        RaceArena arena = plugin.getPlayerArena(p.getUniqueId());
        if (arena == null) {
            p.sendMessage(plugin.getMessage("not-in-race"));
            return;
        }
        arena.removePlayer(p);
        p.sendMessage(plugin.getMessage("arena-left"));
    }



    private void handleReplay(Player p, String[] args) {
        if (!p.hasPermission("race.replay") && !p.hasPermission("race.use")) {
            p.sendMessage(plugin.getMessage("no-permission"));
            return;
        }
        if (args.length < 2) {
            p.sendMessage(Component.text("Utilizzo: /race replay <list|watch|stop> [arena] [indice]", NamedTextColor.RED));
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "list" -> {
                if (args.length < 3) {
                    p.sendMessage(Component.text("Utilizzo: /race replay list <arena>", NamedTextColor.RED));
                    return;
                }
                String arenaName = args[2];
                RaceArena arena = plugin.getArena(arenaName);
                if (arena == null) {
                    p.sendMessage(plugin.getMessage("invalid-arena"));
                    return;
                }
                List<ReplayData> replays = plugin.replayManager.getReplays(arenaName);
                if (replays.isEmpty()) {
                    p.sendMessage(Component.text("Nessun replay disponibile per " + arenaName, NamedTextColor.YELLOW));
                    return;
                }
                p.sendMessage(Component.text("--- Replay di " + arenaName + " ---", NamedTextColor.GOLD));
                for (int i = 0; i < replays.size(); i++) {
                    ReplayData r = replays.get(i);
                    String date = new java.text.SimpleDateFormat("MM/dd HH:mm")
                            .format(new java.util.Date(r.getTimestamp()));
                    p.sendMessage(
                            Component.text("  [" + i + "] " + date + " - " + r.getPlayerNames().size() + " piloti",
                                    NamedTextColor.AQUA));
                }
                p.sendMessage(Component.text("Usa /race replay watch <arena> <indice> per guardarlo", NamedTextColor.GRAY));
            }
            case "watch" -> {
                if (args.length < 4) {
                    p.sendMessage(Component.text("Utilizzo: /race replay watch <arena> <indice>", NamedTextColor.RED));
                    return;
                }
                String arenaName = args[2];
                RaceArena arena = plugin.getArena(arenaName);
                if (arena == null) {
                    p.sendMessage(plugin.getMessage("invalid-arena"));
                    return;
                }
                int index;
                try {
                    index = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    p.sendMessage(Component.text("Indice non valido.", NamedTextColor.RED));
                    return;
                }
                List<ReplayData> replays = plugin.replayManager.getReplays(arenaName);
                if (index < 0 || index >= replays.size()) {
                    p.sendMessage(Component.text("Indice del replay fuori intervallo.", NamedTextColor.RED));
                    return;
                }
                ReplayData replay = replays.get(index);
                plugin.replayManager.startPlayback(p, replay,
                        arena.getSpawns().isEmpty() ? p.getWorld() : arena.getSpawns().get(0).getWorld());
            }
            case "stop" -> plugin.replayManager.stopPlayback(p);
            default -> p.sendMessage(Component.text("Azione sconosciuta. Usa list, watch oppure stop.", NamedTextColor.RED));
        }
    }

    private void handleAdmin(Player p, String[] args) {
        if (!p.hasPermission("race.admin")) {
            p.sendMessage(plugin.getMessage("no-permission"));
            return;
        }
        if (args.length < 2) {
            p.sendMessage(Component.text("Utilizzo: /race admin <wand|startvote|delete|visualize|reload|setmainlobby>",
                    NamedTextColor.RED));
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "wand" -> {
                ItemStack wand = new ItemStack(Material.BLAZE_ROD);
                ItemMeta meta = wand.getItemMeta();
                meta.displayName(Component.text("§b§lBacchetta gara §7(Clicca per cambiare modalità)"));
                meta.lore(List.of(
                        Component.text("§7Click sinistro: aggiungi punto"),
                        Component.text("§7Click destro: cambia modalità"),
                        Component.text("§7Shift + destro: rimuovi punto")));
                meta.getPersistentDataContainer().set(plugin.guiManager.raceWandKey, PersistentDataType.BYTE, (byte) 1);
                wand.setItemMeta(meta);
                p.getInventory().addItem(wand);
                p.sendMessage(Component.text("Bacchetta gara ricevuta!", NamedTextColor.GREEN));

                // Check if in editor mode
                if (!plugin.editorArena.containsKey(p.getUniqueId())) {
                    p.sendMessage(Component.text("Consiglio: apri il pannello amministratore e scegli l'arena da modificare.",
                            NamedTextColor.YELLOW));
                }
            }
            case "startvote" -> {
                int duration = 60;
                if (args.length >= 3) {
                    try {
                        duration = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                plugin.startVotingRound(duration);
                p.sendMessage(Component.text("Votazione avviata per " + duration + " secondi!", NamedTextColor.GREEN));
            }
            case "delete" -> {
                if (args.length < 3) {
                    p.sendMessage(Component.text("Utilizzo: /race admin delete <arena>", NamedTextColor.RED));
                    return;
                }
                String arenaName = args[2];
                if (plugin.getArena(arenaName) == null) {
                    p.sendMessage(plugin.getMessage("invalid-arena"));
                    return;
                }
                plugin.removeArena(arenaName);
                plugin.saveArenas();
                p.sendMessage(Component.text("Arena " + arenaName + " eliminata.", NamedTextColor.YELLOW));
            }
            case "visualize" -> {
                if (args.length < 3) {
                    p.sendMessage(Component.text("Utilizzo: /race admin visualize <arena>", NamedTextColor.RED));
                    return;
                }
                String arenaName = args[2];
                if (plugin.getArena(arenaName) == null) {
                    p.sendMessage(plugin.getMessage("invalid-arena"));
                    return;
                }
                if (plugin.activeVisualizers.containsKey(p.getUniqueId())
                        && plugin.activeVisualizers.get(p.getUniqueId()).equals(arenaName)) {
                    plugin.activeVisualizers.remove(p.getUniqueId());
                    p.sendMessage(Component.text("Visualizzatore disattivato per " + arenaName, NamedTextColor.YELLOW));
                } else {
                    plugin.activeVisualizers.put(p.getUniqueId(), arenaName);
                    p.sendMessage(Component.text("Visualizzatore attivato per " + arenaName, NamedTextColor.GREEN));
                }
            }
            case "reload" -> {
                plugin.reload();
                p.sendMessage(Component.text("Configurazione ricaricata!", NamedTextColor.GREEN));
            }
            case "setmainlobby" -> {
                Location loc = p.getLocation();
                // Set main lobby for all arenas
                for (RaceArena arena : plugin.getArenas().values()) {
                    arena.setMainLobby(loc);
                }
                plugin.saveArenas();
                p.sendMessage(Component.text("Lobby principale impostata per tutte le arene!", NamedTextColor.GREEN));
            }
            default -> p.sendMessage(Component.text("Azione amministrativa sconosciuta.", NamedTextColor.RED));
        }
    }
}
