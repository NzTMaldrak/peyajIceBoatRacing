package peyaj;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import peyaj.arena.RaceState;
import peyaj.arena.RaceType;
import peyaj.cosmetics.EditMode;
import peyaj.cosmetics.TrailType;

import java.util.*;
import java.util.stream.Collectors;

import java.util.Arrays;
import java.util.List;

public class GUIManager implements Listener {

    private final IceBoatRacing plugin;
    private final NamespacedKey arenaKey;
    private final NamespacedKey trailKey;
    private final NamespacedKey spectatorTargetKey;
    public final NamespacedKey raceWandKey;

    public GUIManager(IceBoatRacing plugin) {
        this.plugin = plugin;
        this.arenaKey = new NamespacedKey(plugin, "arena_key");
        this.trailKey = new NamespacedKey(plugin, "trail_key");
        this.spectatorTargetKey = new NamespacedKey(plugin, "spectator_target");
        this.raceWandKey = new NamespacedKey(plugin, "race_wand");
    }

    public void openMainMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("IceBoat Racing", NamedTextColor.AQUA));

        // Play Button
        inv.setItem(11, createItem(Material.OAK_BOAT, "&b&lGioca", "&7Entra in una gara!"));

        // Cosmetics Button
        inv.setItem(13, createItem(Material.DIAMOND, "&d&lCosmetici", "&7Gabbie e scie"));

        // Vote Button (Dynamic)
        if (plugin.isVoting) {
            inv.setItem(22, createItem(Material.PAPER, "&a&lVOTA ORA!", "&7Clicca per votare la mappa",
                    "&eTermina tra: " + plugin.votingTimeRemaining + "s"));
        }

        // Stats Button
        int wins = plugin.getStat(p.getUniqueId(), "wins");
        int played = plugin.getStat(p.getUniqueId(), "races_played");
        inv.setItem(15, createItem(Material.PAPER, "&e&lLe tue statistiche",
                "&7Vittorie: &a" + wins,
                "&7Gare: &f" + played,
                "",
                "&7Percentuale vittorie: &b" + (played > 0 ? (wins * 100 / played) : 0) + "%"));

        // Admin Button
        if (p.hasPermission("race.admin")) {
            inv.setItem(26, createItem(Material.COMMAND_BLOCK, "&c&lPannello amministratore",
                    "&7Gestisci piste e impostazioni"));
        }

        fillGlass(inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
    }

    public void openVoteMenu(Player p) {
        if (!plugin.isVoting) {
            p.sendMessage(Component.text("Non c'è alcuna votazione in corso.", NamedTextColor.RED));
            return;
        }

        int size = 27;
        Inventory inv = Bukkit.createInventory(null, size, Component.text("Vota la mappa", NamedTextColor.DARK_GREEN));

        for (RaceArena arena : plugin.getArenas().values()) {
            if (arena.getState() != RaceState.LOBBY)
                continue;

            int votes = plugin.getVoteCount(arena.getName());
            boolean playerVotedThis = plugin.playerVotes.containsKey(p.getUniqueId())
                    && plugin.playerVotes.get(p.getUniqueId()).equals(arena.getName());

            ItemStack item = createItem(Material.MAP, "&b&l" + arena.getName(),
                    "&7Tipo: &f" + arena.getType(),
                    "&7Giri: &f" + arena.getTotalLaps(),
                    "",
                    "&e&lVoti: " + votes,
                    playerVotedThis ? "&a&l[ IL TUO VOTO ]" : "&7Clicca per votare");

            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, arena.getName());
            if (playerVotedThis)
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);

            inv.addItem(item);
        }

        fillGlass(inv);
        p.openInventory(inv);
    }

    public void openCosmeticsHub(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Menu cosmetici", NamedTextColor.LIGHT_PURPLE));
        inv.setItem(11, createItem(Material.GLASS, "&d&lColori gabbia", "&7Cambia il colore del blocco di partenza"));
        inv.setItem(15, createItem(Material.FIREWORK_ROCKET, "&6&lScie di particelle", "&7Cambia le particelle di guida"));
        inv.setItem(26, createItem(Material.ARROW, "&cIndietro", "&7Torna al menu principale"));
        fillGlass(inv);
        p.openInventory(inv);
    }

    public void openSpectatorMenu(Player p, RaceArena arena) {
        List<Player> targets = arena.getSpectatablePlayers();
        int size = Math.max(9, Math.min(54, ((targets.size() + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(null, size,
                Component.text("Segui un pilota", NamedTextColor.DARK_AQUA));

        for (Player target : targets) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.displayName(Component.text(target.getName(), NamedTextColor.AQUA));
            meta.lore(List.of(Component.text("Click per visuale in prima persona", NamedTextColor.GRAY),
                    Component.text("SHIFT per tornare al volo libero", NamedTextColor.DARK_GRAY)));
            meta.getPersistentDataContainer().set(spectatorTargetKey, PersistentDataType.STRING,
                    target.getUniqueId().toString());
            head.setItemMeta(meta);
            inv.addItem(head);
        }

        if (targets.isEmpty()) {
            inv.setItem(4, createItem(Material.BARRIER, "&cNessun pilota disponibile"));
        }
        p.openInventory(inv);
    }

    public void openCageMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Scegli colore gabbia", NamedTextColor.LIGHT_PURPLE));
        inv.setItem(10, createCosmeticItem(Material.GLASS, "&fPredefinito (trasparente)"));
        inv.setItem(11, createCosmeticItem(Material.RED_STAINED_GLASS, "&cRosso"));
        inv.setItem(12, createCosmeticItem(Material.ORANGE_STAINED_GLASS, "&6Arancione"));
        inv.setItem(13, createCosmeticItem(Material.YELLOW_STAINED_GLASS, "&eGiallo"));
        inv.setItem(14, createCosmeticItem(Material.LIME_STAINED_GLASS, "&aVerde lime"));
        inv.setItem(15, createCosmeticItem(Material.LIGHT_BLUE_STAINED_GLASS, "&bAzzurro"));
        inv.setItem(16, createCosmeticItem(Material.MAGENTA_STAINED_GLASS, "&dMagenta"));
        inv.setItem(26, createItem(Material.ARROW, "&cIndietro", "&7Torna ai cosmetici"));
        fillGlass(inv);
        p.openInventory(inv);
    }

    public void openTrailMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 36, Component.text("Scegli scia di particelle", NamedTextColor.GOLD));
        TrailType current = plugin.getPlayerTrailPreference(p.getUniqueId());
        int slot = 10;
        for (TrailType trail : TrailType.values()) {
            boolean hasPerm = trail.permission == null || p.hasPermission(trail.permission);
            boolean isSelected = (trail == current);
            String status = isSelected ? "&a&lSELEZIONATA" : (hasPerm ? "&eClicca per selezionare" : "&cBloccata");
            Material mat = trail.icon;
            ItemStack item = createItem(mat, "&6&l" + trail.displayName, status);
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(trailKey, PersistentDataType.STRING, trail.name());
            if (isSelected)
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                    org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
            if ((slot + 1) % 9 == 0)
                slot += 2;
        }
        inv.setItem(31, createItem(Material.ARROW, "&cIndietro", "&7Torna ai cosmetici"));
        fillGlass(inv);
        p.openInventory(inv);
    }

    public void openAdminPanel(Player p) {
        if (!p.hasPermission("race.admin")) {
            p.sendMessage(Component.text("Non hai il permesso di accedere a questo menu.", NamedTextColor.RED));
            p.closeInventory();
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Pannello amministratore IceBoat", NamedTextColor.DARK_AQUA));
        inv.setItem(10, createItem(Material.EMERALD_BLOCK, "&a&lCrea arena", "&7Clicca per assegnare un nome"));
        inv.setItem(12, createItem(Material.CHEST_MINECART, "&e&lModifica arene", "&7Configura le arene esistenti"));
        inv.setItem(14, createItem(Material.BLAZE_ROD, "&6&lOttieni bacchetta", "&7Ricevi la bacchetta di configurazione"));
        inv.setItem(16, createItem(Material.REDSTONE_TORCH, "&c&lRicarica plugin", "&7Ricarica config.yml"));

        // Admin Vote Button
        inv.setItem(22, createItem(Material.BEACON, "&e&lAvvia votazione", "&7Forza l'avvio di una votazione"));

        // Setup Guide Book
        inv.setItem(8, createItem(Material.BOOK, "&b&lGuida configurazione", "&7Ricevi il libro di istruzioni"));

        inv.setItem(26, createItem(Material.ARROW, "&cIndietro", "&7Torna al menu principale"));
        fillGlass(inv);
        p.openInventory(inv);
    }

    public void openRaceTypeSelector(Player p, String arenaName) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Scegli tipo: " + arenaName, NamedTextColor.BLUE));
        ItemStack defaultType = createItem(Material.ICE, "&b&lDa punto a punto", "&7Gara classica dalla partenza all'arrivo.",
                "&7Senza giri.");
        ItemMeta dm = defaultType.getItemMeta();
        dm.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, arenaName);
        defaultType.setItemMeta(dm);

        ItemStack lapType = createItem(Material.CLOCK, "&e&lGara a giri", "&7Circuito su più giri.",
                "&7Partenza e arrivo coincidono.");
        ItemMeta lm = lapType.getItemMeta();
        lm.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, arenaName);
        lapType.setItemMeta(lm);

        inv.setItem(11, defaultType);
        inv.setItem(15, lapType);
        fillGlass(inv);
        p.openInventory(inv);
    }

    public void openArenaSelector(Player p, boolean adminMode) {
        int size = 54;
        String title = adminMode ? "Modifica arena" : "Scegli arena";
        Inventory inv = Bukkit.createInventory(null, size, Component.text(title, NamedTextColor.DARK_GRAY));
        for (RaceArena arena : plugin.getArenas().values()) {
            String status = (arena.getState() == RaceState.LOBBY) ? "&aAPERTA" : "&cIN CORSO";

            List<String> lore = new ArrayList<>();
            lore.add("&7Stato: " + status);
            lore.add("&7Tipo: &f" + arena.getType());
            lore.add("&7Giri: &f" + arena.getTotalLaps());

            if (adminMode) {
                lore.add("&eClicca per modificare");
            } else {
                if (arena.getState() == RaceState.LOBBY) {
                    // Check if lobby has players
                    if (arena.getPlayerCount() > 0) {
                        lore.add("&eClick sinistro per entrare &7(" + arena.getPlayerCount() + " in attesa)");
                    } else {
                        lore.add("&eClick sinistro per entrare");
                        lore.add("&dShift-Click per la prova a tempo");
                    }
                } else {
                    lore.add("&bClicca per osservare");
                }
            }

            ItemStack item = createItem(Material.ICE, "&b&l" + arena.getName(), lore.toArray(new String[0]));
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, arena.getName());
            item.setItemMeta(meta);
            inv.addItem(item);
        }
        inv.setItem(49, createItem(Material.ARROW, "&cIndietro", "&7Torna al menu"));
        p.openInventory(inv);
    }

    public void openArenaEditor(Player p, RaceArena arena) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("Modifica: " + arena.getName(), NamedTextColor.BLUE));
        boolean isVis = plugin.activeVisualizers.containsKey(p.getUniqueId())
                && plugin.activeVisualizers.get(p.getUniqueId()).equals(arena.getName());
        inv.setItem(10, createItem(isVis ? Material.ENDER_EYE : Material.ENDER_PEARL,
                "&b&lVisualizzatore: " + (isVis ? "&aATTIVO" : "&cDISATTIVO"),
                "&7Attiva o disattiva l'anteprima"));

        EditMode currentMode = plugin.editorMode.getOrDefault(p.getUniqueId(), EditMode.SPAWN);
        inv.setItem(12, createItem(Material.BLAZE_POWDER, "&6&lModalità bacchetta",
                "&7Attuale: " + currentMode.color + currentMode.displayName, "&eClicca per cambiare modalità"));

        boolean ready = arena.isSetupComplete();
        inv.setItem(13, createItem(ready ? Material.EMERALD : Material.REDSTONE_BLOCK,
                ready ? "&a&lConfigurazione: PRONTA" : "&c&lConfigurazione: INCOMPLETA",
                "&7Clicca per aprire la lista di controllo"));

        inv.setItem(14, createItem(Material.CLOCK, "&e&lGiri: &f" + arena.getTotalLaps(), "&aClick sinistro: +1",
                "&cClick destro: -1"));
        inv.setItem(16, createItem(Material.PLAYER_HEAD, "&e&lGiocatori minimi: &f" + arena.minPlayers,
                "&aClick sinistro: +1", "&cClick destro: -1"));

        inv.setItem(28, createItem(Material.COMPASS, "&aTeletrasportati alla lobby", ""));
        inv.setItem(29, createItem(Material.BEACON, "&aTeletrasportati alla partenza", ""));
        inv.setItem(30, createItem(Material.ARMOR_STAND, "&d&lImposta classifica", "&7Posiziona l'ologramma ai tuoi piedi"));
        inv.setItem(31, createItem(Material.REPEATER, "&aRitardo avvio automatico: " + arena.autoStartDelay + "s",
                "&eClicca per aggiungere 10s"));

        inv.setItem(32, createItem(Material.LEVER, "&a&lForza avvio", "&7Avvia subito la gara"));

        inv.setItem(33, createItem(Material.BLAZE_ROD, "&6Ottieni bacchetta", "&7Equipaggia lo strumento"));
        inv.setItem(34, createItem(Material.TNT, "&c&lElimina arena", "&7Shift-Click per confermare"));
        inv.setItem(49, createItem(Material.ARROW, "&cTorna all'elenco", ""));

        fillGlass(inv);
        p.openInventory(inv);
        plugin.editorArena.put(p.getUniqueId(), arena.getName());
    }

    public void openArenaSetupStatusMenu(Player p, RaceArena arena) {
        Inventory inv = Bukkit.createInventory(null, 36,
                Component.text("Configurazione: " + arena.getName(), NamedTextColor.DARK_AQUA));

        List<String> statusLines = arena.getSetupStatus();
        Material icon = arena.isSetupComplete() ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK;
        String statusTitle = arena.isSetupComplete() ? "&a&lARENA PRONTA PER LE GARE" : "&c&lARENA INCOMPLETA";

        inv.setItem(13, createItem(icon, statusTitle, statusLines.toArray(new String[0])));

        // Direct Quick Actions
        inv.setItem(19, createItem(Material.OAK_DOOR, "&aImposta lobby pre-gara", "&7Clicca per impostarla ai tuoi piedi"));
        inv.setItem(21, createItem(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, "&aImposta traguardo", "&7Equipaggia la bacchetta per definire l'area"));
        inv.setItem(23, createItem(Material.TARGET, "&aImposta checkpoint", "&7Equipaggia la bacchetta per impostarli"));
        inv.setItem(25, createItem(Material.ARMOR_STAND, "&dImposta classifica", "&7Posiziona l'ologramma ai tuoi piedi"));

        inv.setItem(31, createItem(Material.ARROW, "&cTorna all'editor", ""));
        fillGlass(inv);
        p.openInventory(inv);
    }

    // --- HELPER: GIVE BOOK ---
    private void giveSetupBook(Player p) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        // Convert components to legacy strings for compatibility with BookMeta
        // setTitle/setAuthor
        meta.setTitle(LegacyComponentSerializer.legacySection()
                .serialize(Component.text("Guida configurazione arena", NamedTextColor.AQUA)));
        meta.setAuthor(LegacyComponentSerializer.legacySection()
                .serialize(Component.text("IceBoatRacing", NamedTextColor.YELLOW)));

        Component p1 = Component.text("§lGuida configurazione\n\n")
                .append(Component.text("1. Usa la §lBacchetta gara§r\n   (/race admin wand)\n\n"))
                .append(Component.text(
                        "2. §lShift + click destro§r\n   per cambiare modalità:\n   - Partenza\n   - Checkpoint\n   - Traguardo\n   - Lobby\n\n"));

        Component p2 = Component.text("§lPassaggi principali:\n\n")
                .append(Component.text("1. Posiziona le §aPartenze§r delle barche.\n\n"))
                .append(Component.text("2. Posiziona i §cCheckpoint§r lungo la pista.\n\n"))
                .append(Component.text("3. Imposta il §bTraguardo§r con due punti.\n"));

        Component p3 = Component.text("§lCompletamento:\n\n")
                .append(Component.text("4. Imposta la §6Lobby§r di attesa.\n\n"))
                .append(Component.text("5. Attiva il visualizzatore per vedere i punti.\n\n"))
                .append(Component.text("6. Regola giri e giocatori nel menu."));

        meta.addPages(p1, p2, p3);
        book.setItemMeta(meta);

        if (p.getInventory().firstEmpty() != -1) {
            p.getInventory().addItem(book);
        } else {
            p.getWorld().dropItem(p.getLocation(), book);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        p.sendMessage(Component.text("Hai ricevuto la guida alla configurazione!", NamedTextColor.GREEN));
    }

    // --- EVENT HANDLING ---

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p))
            return;
        Component titleComp = e.getView().title();
        String title = LegacyComponentSerializer.legacyAmpersand().serialize(titleComp);

        if (!title.contains("IceBoat") && !title.contains("Scegli arena") && !title.contains("Modifica arena")
                && !title.contains("Modifica:") && !title.contains("Scegli colore") && !title.contains("Scegli tipo")
                && !title.contains("Menu cosmetici") && !title.contains("Scegli scia")
                && !title.contains("Vota la mappa") && !title.contains("Configurazione:")
                && !title.contains("Segui un pilota"))
            return;

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR
                || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE)
            return;

        if (title.contains("Segui un pilota")) {
            if (clicked.hasItemMeta() && clicked.getItemMeta().getPersistentDataContainer()
                    .has(spectatorTargetKey, PersistentDataType.STRING)) {
                String uuidText = clicked.getItemMeta().getPersistentDataContainer()
                        .get(spectatorTargetKey, PersistentDataType.STRING);
                try {
                    Player target = Bukkit.getPlayer(UUID.fromString(uuidText));
                    RaceArena arena = plugin.getPlayerArena(p.getUniqueId());
                    if (target != null && arena != null && arena.isSpectator(p.getUniqueId())) {
                        arena.startFirstPersonSpectating(p, target);
                    }
                } catch (IllegalArgumentException ignored) {
                }
                p.closeInventory();
            }
            return;
        }

        // Main Menu
        if (title.contains("IceBoat Racing")) {
            if (clicked.getType() == Material.OAK_BOAT)
                openArenaSelector(p, false);
            else if (clicked.getType() == Material.DIAMOND)
                openCosmeticsHub(p);
            else if (clicked.getType() == Material.COMMAND_BLOCK)
                openAdminPanel(p);
            else if (clicked.getType() == Material.PAPER && plugin.isVoting)
                openVoteMenu(p);
            return;
        }

        // Vote Menu
        if (title.contains("Vota la mappa")) {
            if (clicked.hasItemMeta()
                    && clicked.getItemMeta().getPersistentDataContainer().has(arenaKey, PersistentDataType.STRING)) {
                String arenaName = clicked.getItemMeta().getPersistentDataContainer().get(arenaKey,
                        PersistentDataType.STRING);
                plugin.castVote(p, arenaName);
                openVoteMenu(p);
            }
            return;
        }

        // Cosmetics Hub
        if (title.contains("Menu cosmetici")) {
            if (clicked.getType() == Material.ARROW) {
                openMainMenu(p);
                return;
            }
            if (clicked.getType() == Material.GLASS)
                openCageMenu(p);
            if (clicked.getType() == Material.FIREWORK_ROCKET)
                openTrailMenu(p);
            return;
        }

        // Cosmetics: Cage
        if (title.contains("Scegli colore gabbia")) {
            if (clicked.getType() == Material.ARROW) {
                openCosmeticsHub(p);
                return;
            }
            Material blockMat = convertPaneToBlock(clicked.getType());
            if (blockMat != null) {
                plugin.setPlayerCagePreference(p.getUniqueId(), blockMat);
                p.sendMessage(Component.text("Colore della gabbia impostato!", NamedTextColor.GREEN));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                p.closeInventory();
            }
            return;
        }

        // Cosmetics: Trail
        if (title.contains("Scegli scia")) {
            if (clicked.getType() == Material.ARROW) {
                openCosmeticsHub(p);
                return;
            }
            if (clicked.hasItemMeta()
                    && clicked.getItemMeta().getPersistentDataContainer().has(trailKey, PersistentDataType.STRING)) {
                String trailName = clicked.getItemMeta().getPersistentDataContainer().get(trailKey,
                        PersistentDataType.STRING);
                try {
                    TrailType trail = TrailType.valueOf(trailName);
                    if (trail.permission != null && !p.hasPermission(trail.permission)) {
                        p.sendMessage(Component.text("Non hai il permesso per usare questa scia.", NamedTextColor.RED));
                        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1f);
                        return;
                    }
                    plugin.setPlayerTrailPreference(p.getUniqueId(), trail);
                    p.sendMessage(Component.text("Scia impostata: " + trail.displayName, NamedTextColor.GREEN));
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    openTrailMenu(p);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return;
        }

        // Admin Panel
        if (title.contains("Pannello amministratore")) {
            if (clicked.getType() == Material.ARROW) {
                openMainMenu(p);
                return;
            }
            if (clicked.getType() == Material.EMERALD_BLOCK) {
                p.closeInventory();
                plugin.inputMode.put(p.getUniqueId(), "create_arena");
                p.sendMessage(Component.text("---------------------------------------", NamedTextColor.GREEN));
                p.sendMessage(Component.text("Scrivi in chat il nome della nuova arena.", NamedTextColor.YELLOW));
                p.sendMessage(Component.text("Scrivi 'annulla' per interrompere.", NamedTextColor.GRAY));
                p.sendMessage(Component.text("---------------------------------------", NamedTextColor.GREEN));
            } else if (clicked.getType() == Material.CHEST_MINECART)
                openArenaSelector(p, true);
            else if (clicked.getType() == Material.BLAZE_ROD) {
                p.performCommand("race admin wand");
                p.closeInventory();
            } else if (clicked.getType() == Material.REDSTONE_TORCH) {
                plugin.reload();
                p.sendMessage(Component.text("Plugin ricaricato!", NamedTextColor.GREEN));
                p.closeInventory();
            } else if (clicked.getType() == Material.BEACON) {
                p.performCommand("race admin startvote");
                p.closeInventory();
            } else if (clicked.getType() == Material.BOOK) {
                giveSetupBook(p);
                p.closeInventory();
            }
            return;
        }

        // Race Type Selector
        if (title.contains("Scegli tipo:")) {
            if (clicked.hasItemMeta()
                    && clicked.getItemMeta().getPersistentDataContainer().has(arenaKey, PersistentDataType.STRING)) {
                String arenaName = clicked.getItemMeta().getPersistentDataContainer().get(arenaKey,
                        PersistentDataType.STRING);
                RaceArena arena = plugin.getArena(arenaName);
                if (arena != null) {
                    if (clicked.getType() == Material.ICE) {
                        arena.setType(RaceType.DEFAULT);
                        arena.setTotalLaps(1);
                    } else if (clicked.getType() == Material.CLOCK) {
                        arena.setType(RaceType.LAP);
                        arena.setTotalLaps(3);
                    }
                    plugin.saveArenas();
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                    giveSetupBook(p);
                    openArenaEditor(p, arena);
                }
            }
            return;
        }

        // Arena Selector
        if (title.contains("Scegli arena") || title.contains("Modifica arena")) {
            if (clicked.getType() == Material.ARROW) {
                if (title.contains("Modifica"))
                    openAdminPanel(p);
                else
                    openMainMenu(p);
                return;
            }

            if (clicked.hasItemMeta()
                    && clicked.getItemMeta().getPersistentDataContainer().has(arenaKey, PersistentDataType.STRING)) {
                String arenaName = clicked.getItemMeta().getPersistentDataContainer().get(arenaKey,
                        PersistentDataType.STRING);

                // Logic: Handle Shift-Click (Time Trial) vs Left-Click (Join/Spectate)
                if (title.contains("Modifica arena")) {
                    RaceArena arena = plugin.getArena(arenaName);
                    if (arena != null)
                        openArenaEditor(p, arena);
                } else {
                    // Check for active race conflict first
                    RaceArena arena = plugin.getArena(arenaName);

                    if (e.isShiftClick()) {
                        // Time Trial Request
                        if (arena != null) {
                            if (arena.getState() != RaceState.LOBBY) {
                                p.sendMessage(Component.text("Non puoi avviare una prova a tempo durante una gara.",
                                        NamedTextColor.RED));
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                            } else {
                                arena.addPlayer(p, true); // true = Time Trial
                            }
                        }
                    } else {
                        if (arena != null) {
                            if (arena.getState() == RaceState.LOBBY) {
                                p.performCommand("race join " + arenaName);
                            } else {
                                arena.addSpectator(p);
                            }
                        }
                    }
                    p.closeInventory();
                }
            }
            return;
        }

        // Arena Editor
        if (title.contains("Modifica:")) {
            String arenaName = plugin.editorArena.get(p.getUniqueId());
            if (arenaName == null) {
                p.closeInventory();
                return;
            }
            RaceArena arena = plugin.getArena(arenaName);
            if (arena == null) {
                p.closeInventory();
                return;
            }

            if (clicked.getType() == Material.EMERALD || clicked.getType() == Material.REDSTONE_BLOCK) {
                openArenaSetupStatusMenu(p, arena);
            } else if (clicked.getType() == Material.ENDER_EYE || clicked.getType() == Material.ENDER_PEARL) {
                p.performCommand("race admin visualize " + arenaName);
                openArenaEditor(p, arena);
            } else if (clicked.getType() == Material.BLAZE_POWDER) {
                EditMode current = plugin.editorMode.getOrDefault(p.getUniqueId(), EditMode.SPAWN);
                plugin.editorMode.put(p.getUniqueId(), current.next());
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 2f);
                openArenaEditor(p, arena);
            } else if (clicked.getType() == Material.CLOCK) {
                int change = e.isLeftClick() ? 1 : -1;
                arena.setTotalLaps(Math.max(1, arena.getTotalLaps() + change));
                plugin.saveArenas();
                openArenaEditor(p, arena);
            } else if (clicked.getType() == Material.PLAYER_HEAD) {
                int change = e.isLeftClick() ? 1 : -1;
                arena.minPlayers = Math.max(1, arena.minPlayers + change);
                plugin.saveArenas();
                openArenaEditor(p, arena);
            } else if (clicked.getType() == Material.COMPASS) {
                if (arena.getLobby() != null)
                    p.teleport(arena.getLobby());
            } else if (clicked.getType() == Material.BEACON) {
                if (!arena.getSpawns().isEmpty())
                    p.teleport(arena.getSpawns().getFirst());
            } else if (clicked.getType() == Material.ARMOR_STAND) {
                arena.setLeaderboardLocation(p.getLocation().add(0, 1.5, 0));
                plugin.saveArenas();
                p.sendMessage(Component.text("Classifica aggiornata.", NamedTextColor.LIGHT_PURPLE));
            } else if (clicked.getType() == Material.REPEATER) {
                arena.autoStartDelay += 10;
                if (arena.autoStartDelay > 60)
                    arena.autoStartDelay = 10;
                plugin.saveArenas();
                openArenaEditor(p, arena);
            } else if (clicked.getType() == Material.LEVER) {
                p.performCommand("race start " + arenaName);
                p.closeInventory();
            } else if (clicked.getType() == Material.BLAZE_ROD) {
                p.performCommand("race admin wand");
                p.closeInventory();
            } else if (clicked.getType() == Material.TNT) {
                if (e.isShiftClick()) {
                    p.performCommand("race admin delete " + arenaName);
                    p.closeInventory();
                } else {
                    p.sendMessage(Component.text("Usa Shift-Click sulla TNT per eliminare l'arena.", NamedTextColor.RED));
                }
            } else if (clicked.getType() == Material.ARROW) {
                openArenaSelector(p, true);
            }
            return;
        }

        // Setup Progress Checklist Menu
        if (title.contains("Configurazione:")) {
            String arenaName = plugin.editorArena.get(p.getUniqueId());
            if (arenaName == null) {
                p.closeInventory();
                return;
            }
            RaceArena arena = plugin.getArena(arenaName);
            if (arena == null) {
                p.closeInventory();
                return;
            }

            if (clicked.getType() == Material.OAK_DOOR) {
                arena.setLobby(p.getLocation());
                plugin.saveArenas();
                p.sendMessage(Component.text("Lobby pre-gara impostata nella tua posizione!", NamedTextColor.GREEN));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                openArenaSetupStatusMenu(p, arena);
            } else if (clicked.getType() == Material.ARMOR_STAND) {
                arena.setLeaderboardLocation(p.getLocation().add(0, 1.5, 0));
                plugin.saveArenas();
                p.sendMessage(Component.text("Posizione dell'ologramma della classifica impostata!", NamedTextColor.LIGHT_PURPLE));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                openArenaSetupStatusMenu(p, arena);
            } else if (clicked.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
                plugin.editorMode.put(p.getUniqueId(), EditMode.FINISH_1);
                p.performCommand("race admin wand");
                p.sendMessage(Component.text("Bacchetta impostata su TRAGUARDO. Clicca i blocchi con i due tasti.", NamedTextColor.YELLOW));
                p.closeInventory();
            } else if (clicked.getType() == Material.TARGET) {
                plugin.editorMode.put(p.getUniqueId(), EditMode.CHECKPOINT);
                p.performCommand("race admin wand");
                p.sendMessage(Component.text("Bacchetta impostata su CHECKPOINT. Usa il click sinistro sui blocchi.", NamedTextColor.YELLOW));
                p.closeInventory();
            } else if (clicked.getType() == Material.ARROW) {
                openArenaEditor(p, arena);
            }
        }
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(name).decoration(TextDecoration.ITALIC, false));
            if (lore.length > 0) {
                List<Component> loreComponents = Arrays.stream(lore)
                        .map(s -> LegacyComponentSerializer.legacyAmpersand().deserialize(s)
                                .decoration(TextDecoration.ITALIC, false))
                        .collect(Collectors.toList());
                meta.lore(loreComponents);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCosmeticItem(Material mat, String name) {
        return createItem(mat, name, "&7Clicca per selezionare");
    }

    private Material convertPaneToBlock(Material pane) {
        String name = pane.name();
        if (name.contains("STAINED_GLASS_PANE")) {
            return Material.valueOf(name.replace("_PANE", ""));
        } else if (name.equals("GLASS_PANE")) {
            return Material.GLASS;
        }
        if (name.contains("GLASS"))
            return pane;
        return null;
    }

    private void fillGlass(Inventory inv) {
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null)
                inv.setItem(i, glass);
        }
    }
}
