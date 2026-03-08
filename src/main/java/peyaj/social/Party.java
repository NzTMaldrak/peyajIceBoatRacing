package peyaj.social;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a party of players who can join races together.
 */
public class Party {
    private final UUID leader;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> pendingInvites = new HashSet<>();
    private boolean privateJoin = false;
    private final long createdAt;

    public Party(UUID leader) {
        this.leader = leader;
        this.members.add(leader);
        this.createdAt = System.currentTimeMillis();
    }

    public UUID getLeader() {
        return leader;
    }

    public Set<UUID> getMembers() {
        return new HashSet<>(members);
    }

    public int getSize() {
        return members.size();
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    public boolean addMember(UUID uuid) {
        if (members.size() >= 8)
            return false; // Max party size
        pendingInvites.remove(uuid);
        return members.add(uuid);
    }

    public boolean removeMember(UUID uuid) {
        if (uuid.equals(leader))
            return false; // Can't remove leader
        return members.remove(uuid);
    }

    public void addInvite(UUID uuid) {
        pendingInvites.add(uuid);
    }

    public boolean hasInvite(UUID uuid) {
        return pendingInvites.contains(uuid);
    }

    public void removeInvite(UUID uuid) {
        pendingInvites.remove(uuid);
    }

    public Set<UUID> getPendingInvites() {
        return new HashSet<>(pendingInvites);
    }

    public boolean isPrivateJoin() {
        return privateJoin;
    }

    public void setPrivateJoin(boolean privateJoin) {
        this.privateJoin = privateJoin;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
