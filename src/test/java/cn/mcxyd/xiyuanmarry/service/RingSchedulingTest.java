package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.Consumer;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class RingSchedulingTest {
    final UnifiedScheduler scheduler=mock(UnifiedScheduler.class);
    final PlayerDirectory directory=mock(PlayerDirectory.class);
    final MarriageService marriages=mock(MarriageService.class);
    final TaskHandle timer=mock(TaskHandle.class);
    final UUID live=UUID.randomUUID();
    final PlayerSnapshot actor=new PlayerSnapshot(live,live,"guest","Guest",60,null,1);
    final List<Consumer<Player>> queued=new ArrayList<>();
    final List<Runnable> retired=new ArrayList<>();
    Runnable tick;RingService rings;
    @BeforeEach void setup(){
        var plugin=mock(JavaPlugin.class);when(plugin.namespace()).thenReturn("xiyuanmarry");
        when(directory.all()).thenReturn(List.of(actor));when(directory.live(live)).thenReturn(actor);
        when(scheduler.runRepeatingAsync(any(),anyLong(),anyLong(),any())).thenAnswer(call->{tick=call.getArgument(0);return timer;});
        doAnswer(call->{queued.add(call.getArgument(1));return null;}).when(scheduler).player(any(),any());
        doAnswer(call->{queued.add(call.getArgument(1));retired.add(call.getArgument(2));return null;}).when(scheduler).player(any(),any(),any());
        rings=new RingService(plugin,marriages,mock(ConfigurationManager.class),directory,scheduler,mock(MessageService.class));
    }
    @AfterEach void close(){rings.close();}
    @Test void stalledRegionCannotAccumulateRingCallbacks(){rings.start();tick.run();tick.run();tick.run();assertEquals(1,queued.size());}
    @Test void closedServiceCannotRestartTimer(){rings.start();rings.close();rings.start();verify(scheduler,times(1)).runRepeatingAsync(any(),anyLong(),anyLong(),any());}
    @Test void startingTwiceDoesNotLeakFirstTimer(){rings.start();rings.start();verify(scheduler,times(1)).runRepeatingAsync(any(),anyLong(),anyLong(),any());}
    @Test void queuedCallbackAfterCloseDoesNotTouchPlayer(){rings.start();tick.run();rings.close();var player=mock(Player.class);queued.getFirst().accept(player);verifyNoInteractions(player);}
    @Test void retiredCallbackReleasesItsSlotForFutureSessions(){rings.start();tick.run();retired.getFirst().run();tick.run();assertEquals(2,queued.size());}
    @Test void callbackUsesCurrentIdentityInsteadOfCapturedIdentity(){
        rings.start();tick.run();when(directory.live(live)).thenReturn(null);
        // tick 现在会读取婚姻视图以清理偏好；这里只关心回调自身不触碰服务。
        clearInvocations(marriages);
        var player=mock(Player.class);queued.getFirst().accept(player);
        verify(player,never()).getInventory();verifyNoInteractions(marriages);
    }
    @Test void missingPartnerPointSkipsEntityInventorySafely(){
        UUID partnerId=UUID.randomUUID();
        var marriage=new MarriageRecord(live,partnerId,MarriageState.MARRIED,"NORMAL",1,0,0,"pair",1,0,0);
        when(marriages.view()).thenReturn(new MarriageService.View(Map.of(live,marriage,partnerId,marriage),Map.of(),List.of(marriage),Map.of()));
        when(directory.identity(partnerId)).thenReturn(new PlayerSnapshot(partnerId,partnerId,"guest:partner","Partner",60,null,1));
        rings.start();tick.run();
        var player=mock(Player.class);when(player.isOnline()).thenReturn(true);when(player.isDead()).thenReturn(false);
        assertDoesNotThrow(()->queued.getFirst().accept(player));
        verify(player,never()).getInventory();
    }
}
