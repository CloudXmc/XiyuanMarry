package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.listener.PlayerLifecycleListener;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BondAttributeSchedulingTest {
    final UnifiedScheduler scheduler=mock(UnifiedScheduler.class);
    final MarriageService marriages=mock(MarriageService.class);
    final PlayerDirectory directory=mock(PlayerDirectory.class);
    final TaskHandle timer=mock(TaskHandle.class);
    final List<Consumer<Player>> queued=new ArrayList<>();
    Runnable tick;
    BondAttributeService service;
    MockedStatic<Bukkit> bukkit;
    @BeforeEach void setup() {
        bukkit=mockStatic(Bukkit.class);bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
        var plugin=mock(JavaPlugin.class);when(plugin.namespace()).thenReturn("xiyuanmarry");
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        UUID id=UUID.randomUUID();
        var actor=new PlayerSnapshot(id,id,"name:alice","Alice",60,null,System.currentTimeMillis());
        when(marriages.directory()).thenReturn(directory);when(directory.all()).thenReturn(List.of(actor));
        when(marriages.view()).thenReturn(new MarriageService.View(Map.of(),Map.of(),List.of(),Map.of()));
        doAnswer(c->{tick=c.getArgument(0);return timer;}).when(scheduler).runRepeatingAsync(any(),anyLong(),anyLong(),any());
        doAnswer(c->{queued.add(c.getArgument(1));return null;}).when(scheduler).player(any(),any());
        doAnswer(c->{queued.add(c.getArgument(1));return null;}).when(scheduler).player(any(),any(),any());
        service=new BondAttributeService(plugin,mock(ConfigurationManager.class),marriages,scheduler);
    }
    @AfterEach void finish(){bukkit.close();}
    @Test void laggingOwnerHasOnlyOneQueuedReconciliation() {
        service.start();tick.run();tick.run();tick.run();
        assertEquals(1,queued.size(),"区域尚未执行时，不得每秒堆积实体回调");
    }
    @Test void closeDoesNotSubmitTasksToTheDisabledPlugin() {
        service.start();service.close();
        assertEquals(1,queued.size(),"停服前应提交一次实体上下文清理");
        verify(timer).cancel();
    }
    @Test void closedServiceCannotRestartOrQueueReloadWork() {
        service.start();service.close();queued.clear();
        service.reload();service.start();
        assertTrue(queued.isEmpty());
        verify(scheduler,times(1)).runRepeatingAsync(any(),eq(1L),eq(1L),eq(TimeUnit.SECONDS));
    }
    @Test void startingTwiceDoesNotLeakAttributeTimer(){
        service.start();service.start();
        verify(scheduler,times(1)).runRepeatingAsync(any(),anyLong(),anyLong(),any());
    }
    @Test void reloadDoesNotDuplicateAlreadyQueuedOwnerWork(){
        service.start();tick.run();service.reload();tick.run();assertEquals(1,queued.size());
    }
    @Test void deadPlayerIsNotMarkedReconciledBeforeRespawn(){
        var player=mock(Player.class);when(player.isOnline()).thenReturn(true);when(player.isDead()).thenReturn(true);
        doAnswer(call->{fail("死亡玩家不得进入属性登记路径");return null;}).when(directory).live(any());
        service.start();tick.run();queued.getFirst().accept(player);
        // 死亡对象不应读取婚姻快照并登记 applied，否则复活后相同等级会被误判已应用。
        verify(directory,never()).live(any());
    }
    @Test void deathExplicitlyClearsThePlayersAttributeState() {
        var attributes=mock(BondAttributeService.class);var event=mock(PlayerDeathEvent.class);
        var player=mock(Player.class);when(event.getEntity()).thenReturn(player);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        new PlayerLifecycleListener(directory,mock(WeddingService.class),marriages,attributes).death(event);
        verify(attributes).refresh(player);
    }
}
