package peyaj.data;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Snapshot of the player state that IceBoatRacing temporarily replaces.
 */
public final class PlayerSessionData {

    private final ItemStack[] storageContents;
    private final ItemStack[] armorContents;
    private final ItemStack[] extraContents;
    private final int heldItemSlot;
    private final GameMode gameMode;
    private final Scoreboard scoreboard;
    private final boolean allowFlight;
    private final boolean flying;
    private final boolean invulnerable;
    private final boolean collidable;
    private final boolean invisible;
    private final boolean canPickupItems;

    public PlayerSessionData(Player player) {
        this.storageContents = cloneContents(player.getInventory().getStorageContents());
        this.armorContents = cloneContents(player.getInventory().getArmorContents());
        this.extraContents = cloneContents(player.getInventory().getExtraContents());
        this.heldItemSlot = player.getInventory().getHeldItemSlot();
        this.gameMode = player.getGameMode();
        this.scoreboard = player.getScoreboard();
        this.allowFlight = player.getAllowFlight();
        this.flying = player.isFlying();
        this.invulnerable = player.isInvulnerable();
        this.collidable = player.isCollidable();
        this.invisible = player.isInvisible();
        this.canPickupItems = player.getCanPickupItems();
    }

    public void restore(Player player) {
        player.getInventory().clear();
        player.getInventory().setStorageContents(cloneContents(storageContents));
        player.getInventory().setArmorContents(cloneContents(armorContents));
        player.getInventory().setExtraContents(cloneContents(extraContents));
        player.getInventory().setHeldItemSlot(heldItemSlot);

        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(flying && (allowFlight || gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR));
        player.setInvulnerable(invulnerable);
        player.setCollidable(collidable);
        player.setInvisible(invisible);
        player.setCanPickupItems(canPickupItems);
        player.setScoreboard(scoreboard);
        player.updateInventory();
    }

    public void restoreScoreboard(Player player) {
        player.setScoreboard(scoreboard);
    }

    public Scoreboard getScoreboard() {
        return scoreboard;
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }
}
