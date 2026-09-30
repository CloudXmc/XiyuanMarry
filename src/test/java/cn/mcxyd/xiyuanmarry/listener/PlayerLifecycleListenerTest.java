package cn.mcxyd.xiyuanmarry.listener;

import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.service.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class PlayerLifecycleListenerTest {
    @Test void quitInvalidatesSessionBeforeSubmittingAsyncCleanup(){
        var directory=mock(PlayerDirectory.class);var weddings=mock(WeddingService.class);
        var marriages=mock(MarriageService.class);var attributes=mock(BondAttributeService.class);var player=mock(Player.class);
        UUID live=UUID.randomUUID(),stored=UUID.randomUUID();var loggedIn=new java.util.concurrent.atomic.AtomicBoolean(true);
        when(player.getUniqueId()).thenReturn(live);when(directory.lastKnownIdentity(live)).thenReturn(stored);
        doAnswer(call->{loggedIn.set(false);return null;}).when(directory).leave(live);
        // IO 工作线程可以立即开始，入队时旧登录必须已不可再通过身份/会话校验。
        doAnswer(call->{assertFalse(loggedIn.get());return null;}).when(marriages).leave(live,stored);
        new PlayerLifecycleListener(directory,weddings,marriages,attributes).quit(new PlayerQuitEvent(player,"quit"));
        verify(marriages).leave(live,stored);
    }
    @Test
    void quitAlwaysStopsWeddingEvenWhenDirectorySnapshotExpired() {
        var directory = mock(PlayerDirectory.class);
        var weddings = mock(WeddingService.class);
        var marriages = mock(MarriageService.class);
        var attributes = mock(BondAttributeService.class);
        var player = mock(Player.class);
        UUID live = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(live);
        when(directory.live(live)).thenReturn(null);
        when(marriages.view()).thenReturn(new MarriageService.View(java.util.Map.of(), java.util.Map.of(), java.util.List.of(), java.util.Map.of()));

        new PlayerLifecycleListener(directory, weddings, marriages, attributes).quit(new PlayerQuitEvent(player, "quit"));

        verify(weddings).leave(live);
        verify(directory).leave(live);
        verify(attributes).quit(player);
        verify(marriages).leave(live, live);
        var order=inOrder(directory,marriages);
        order.verify(directory).leave(live);
        order.verify(marriages).leave(live,live);
    }
}
