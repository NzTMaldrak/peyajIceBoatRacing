package peyaj.social;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages player parties for group racing.
 */
public class PartyManager {

    private final JavaPlugin plugin;
    private final Map<UUID, Party> playerParties = new HashMap<>();
    private final Map<UUID, Long> inviteCooldowns = new HashMap<>();

    private static final long INVITE_COOLDOWN_MS = 30000; // 30 seconds
    private static final long INVITE_EXPIRY_MS = 60000; // 1 minute

    public PartyManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates a new party with the player as leader.
     */
    public boolean createParty(Player player) {
        UUID uuid = player.getUniqueId();
        if (playerParties.containsKey(uuid)) {
            player.sendMessage(Component.text("You are already in a party! Leave first with /race party leave",
                    NamedTextColor.RED));
            return false;
        }

        Party party = new Party(uuid);
        playerParties.put(uuid, party);
        player.sendMessage(Component.text("✦ Party created! Invite players with /race party invite <player>",
                NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        return true;
    }

    /**
     * Invites a player to the sender's party.
     */
    public boolean invitePlayer(Player sender, Player target) {
        UUID senderUuid = sender.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        Party party = playerParties.get(senderUuid);
        if (party == null) {
            sender.sendMessage(
                    Component.text("You are not in a party! Create one with /race party create", NamedTextColor.RED));
            return false;
        }

        if (!party.isLeader(senderUuid)) {
            sender.sendMessage(Component.text("Only the party leader can invite players!", NamedTextColor.RED));
            return false;
        }

        if (party.getSize() >= 8) {
            sender.sendMessage(Component.text("Your party is full! (Max: 8 players)", NamedTextColor.RED));
            return false;
        }

        if (playerParties.containsKey(targetUuid)) {
            sender.sendMessage(Component.text(target.getName() + " is already in a party!", NamedTextColor.RED));
            return false;
        }

        // Check cooldown
        Long lastInvite = inviteCooldowns.get(targetUuid);
        if (lastInvite != null && System.currentTimeMillis() - lastInvite < INVITE_COOLDOWN_MS) {
            sender.sendMessage(
                    Component.text("Please wait before sending another invite to this player.", NamedTextColor.YELLOW));
            return false;
        }

        party.addInvite(targetUuid);
        inviteCooldowns.put(targetUuid, System.currentTimeMillis());

        sender.sendMessage(Component.text("✉ Invite sent to " + target.getName() + "!", NamedTextColor.GREEN));
        target.sendMessage(Component.text(""));
        target.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        target.sendMessage(Component.text("✦ Party Invite from " + sender.getName(), NamedTextColor.YELLOW));
        target.sendMessage(Component.text("  Type /race party accept to join!", NamedTextColor.AQUA));
        target.sendMessage(Component.text("  Expires in 60 seconds.", NamedTextColor.GRAY));
        target.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        target.sendMessage(Component.text(""));
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

        // Store pending invite reference
        scheduleInviteExpiry(party, targetUuid, senderUuid);

        return true;
    }

    private void scheduleInviteExpiry(Party party, UUID targetUuid, UUID senderUuid) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (party.hasInvite(targetUuid)) {
                party.removeInvite(targetUuid);
                Player target = Bukkit.getPlayer(targetUuid);
                if (target != null) {
                    target.sendMessage(Component.text("Your party invite has expired.", NamedTextColor.GRAY));
                }
            }
        }, INVITE_EXPIRY_MS / 50); // Convert to ticks
    }

    /**
     * Accepts a pending party invite.
     */
    public boolean acceptInvite(Player player) {
        UUID uuid = player.getUniqueId();

        if (playerParties.containsKey(uuid)) {
            player.sendMessage(Component.text("You are already in a party!", NamedTextColor.RED));
            return false;
        }

        // Find party with pending invite
        for (Party party : playerParties.values()) {
            if (party.hasInvite(uuid)) {
                if (party.addMember(uuid)) {
                    playerParties.put(uuid, party);

                    // Notify all party members
                    for (UUID memberUuid : party.getMembers()) {
                        Player member = Bukkit.getPlayer(memberUuid);
                        if (member != null) {
                            member.sendMessage(Component.text("✦ " + player.getName() + " joined the party!",
                                    NamedTextColor.GREEN));
                            member.playSound(member.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2f);
                        }
                    }
                    return true;
                } else {
                    player.sendMessage(Component.text("The party is full!", NamedTextColor.RED));
                    return false;
                }
            }
        }

        player.sendMessage(Component.text("You have no pending party invites!", NamedTextColor.RED));
        return false;
    }

    /**
     * Leaves the current party.
     */
    public boolean leaveParty(Player player) {
        UUID uuid = player.getUniqueId();
        Party party = playerParties.get(uuid);

        if (party == null) {
            player.sendMessage(Component.text("You are not in a party!", NamedTextColor.RED));
            return false;
        }

        if (party.isLeader(uuid)) {
            // Disband party
            for (UUID memberUuid : party.getMembers()) {
                playerParties.remove(memberUuid);
                Player member = Bukkit.getPlayer(memberUuid);
                if (member != null) {
                    member.sendMessage(Component.text("The party has been disbanded.", NamedTextColor.YELLOW));
                    member.playSound(member.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                }
            }
        } else {
            party.removeMember(uuid);
            playerParties.remove(uuid);

            player.sendMessage(Component.text("You left the party.", NamedTextColor.YELLOW));

            // Notify remaining members
            for (UUID memberUuid : party.getMembers()) {
                Player member = Bukkit.getPlayer(memberUuid);
                if (member != null) {
                    member.sendMessage(Component.text(player.getName() + " left the party.", NamedTextColor.GRAY));
                }
            }
        }

        return true;
    }

    /**
     * Kicks a player from the party (leader only).
     */
    public boolean kickPlayer(Player sender, Player target) {
        UUID senderUuid = sender.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        Party party = playerParties.get(senderUuid);
        if (party == null) {
            sender.sendMessage(Component.text("You are not in a party!", NamedTextColor.RED));
            return false;
        }

        if (!party.isLeader(senderUuid)) {
            sender.sendMessage(Component.text("Only the party leader can kick players!", NamedTextColor.RED));
            return false;
        }

        if (!party.isMember(targetUuid)) {
            sender.sendMessage(Component.text(target.getName() + " is not in your party!", NamedTextColor.RED));
            return false;
        }

        if (targetUuid.equals(senderUuid)) {
            sender.sendMessage(
                    Component.text("You cannot kick yourself! Use /race party leave to disband.", NamedTextColor.RED));
            return false;
        }

        party.removeMember(targetUuid);
        playerParties.remove(targetUuid);

        target.sendMessage(Component.text("You have been kicked from the party.", NamedTextColor.RED));
        target.playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1f);

        for (UUID memberUuid : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null) {
                member.sendMessage(
                        Component.text(target.getName() + " was kicked from the party.", NamedTextColor.YELLOW));
            }
        }

        return true;
    }

    /**
     * Gets the party a player is in.
     */
    public Party getParty(UUID uuid) {
        return playerParties.get(uuid);
    }

    /**
     * Checks if a player is in a party.
     */
    public boolean isInParty(UUID uuid) {
        return playerParties.containsKey(uuid);
    }

    /**
     * Sends a chat message to all party members.
     */
    public void sendPartyChat(Player sender, String message) {
        Party party = playerParties.get(sender.getUniqueId());
        if (party == null) {
            sender.sendMessage(Component.text("You are not in a party!", NamedTextColor.RED));
            return;
        }

        Component chatMessage = Component.text("[Party] ", NamedTextColor.AQUA)
                .append(Component.text(sender.getName() + ": ", NamedTextColor.WHITE))
                .append(Component.text(message, NamedTextColor.GRAY));

        for (UUID memberUuid : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null) {
                member.sendMessage(chatMessage);
            }
        }
    }

    /**
     * Gets party members for a player (for auto-joining arenas together).
     */
    public java.util.Set<UUID> getPartyMembers(UUID uuid) {
        Party party = playerParties.get(uuid);
        return party != null ? party.getMembers() : java.util.Collections.emptySet();
    }

    /**
     * Cleans up when a player disconnects.
     */
    public void handlePlayerQuit(UUID uuid) {
        Party party = playerParties.get(uuid);
        if (party != null) {
            if (party.isLeader(uuid)) {
                // Transfer leadership or disband
                if (party.getSize() > 1) {
                    // Transfer to next member
                    for (UUID memberUuid : party.getMembers()) {
                        if (!memberUuid.equals(uuid)) {
                            // Create new party with new leader
                            Party newParty = new Party(memberUuid);
                            for (UUID m : party.getMembers()) {
                                if (!m.equals(uuid) && !m.equals(memberUuid)) {
                                    newParty.addMember(m);
                                }
                            }
                            for (UUID m : newParty.getMembers()) {
                                playerParties.put(m, newParty);
                                Player member = Bukkit.getPlayer(m);
                                if (member != null) {
                                    member.sendMessage(Component.text("Party leader left. "
                                            + Bukkit.getOfflinePlayer(memberUuid).getName() + " is now the leader.",
                                            NamedTextColor.YELLOW));
                                }
                            }
                            break;
                        }
                    }
                }
            } else {
                party.removeMember(uuid);
            }
            playerParties.remove(uuid);
        }
    }
}
