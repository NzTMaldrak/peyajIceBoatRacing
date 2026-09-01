package peyaj;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;

class RaceArenaSpectatorTest {

    @Test
    void hidesOnlySpectatorEntityAndKeepsTabEntry() {
        IceBoatRacing plugin = mock(IceBoatRacing.class);
        RaceArena arena = new RaceArena("test", plugin);
        Player viewer = mock(Player.class);
        Player spectator = mock(Player.class);
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(spectator.getUniqueId()).thenReturn(UUID.randomUUID());

        arena.hideSpectatorFrom(viewer, spectator);

        verify(viewer).hideEntity(plugin, spectator);
        verify(viewer, never()).hidePlayer(any(), any());
        verify(viewer, never()).unlistPlayer(any());
    }

    @Test
    void doesNotHideSpectatorFromThemselves() {
        IceBoatRacing plugin = mock(IceBoatRacing.class);
        RaceArena arena = new RaceArena("test", plugin);
        Player spectator = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(spectator.getUniqueId()).thenReturn(uuid);

        arena.hideSpectatorFrom(spectator, spectator);

        verify(spectator, never()).hideEntity(any(), any());
        verify(spectator, never()).hidePlayer(any(), any());
    }

    @Test
    void restoresDetachedFollowCamera() {
        RaceArena arena = new RaceArena("test", mock(IceBoatRacing.class));
        Player spectator = mock(Player.class);
        Player target = mock(Player.class);
        when(spectator.getGameMode()).thenReturn(GameMode.ADVENTURE);
        when(spectator.getSpectatorTarget()).thenReturn(null);

        arena.maintainFirstPersonSpectating(spectator, target);

        verify(spectator).setGameMode(GameMode.SPECTATOR);
        verify(spectator).setSpectatorTarget(target);
    }

    @Test
    void leavesStableFollowCameraUntouched() {
        RaceArena arena = new RaceArena("test", mock(IceBoatRacing.class));
        Player spectator = mock(Player.class);
        Player target = mock(Player.class);
        when(spectator.getGameMode()).thenReturn(GameMode.SPECTATOR);
        when(spectator.getSpectatorTarget()).thenReturn(target);

        arena.maintainFirstPersonSpectating(spectator, target);

        verify(spectator, never()).setGameMode(any());
        verify(spectator, never()).setSpectatorTarget(any());
    }
}
