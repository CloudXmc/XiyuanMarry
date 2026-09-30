package cn.mcxyd.xiyuanmarry.listener;

import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.scheduler.TaskHandle;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import cn.mcxyd.xiyuanmarry.service.*;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.Material;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 驱动真实监听器和延迟回调，验证统计结果与玩家生命周期。 */
class CoupleTaskListenerTest {
    final MarriageService marriages = mock(MarriageService.class);
    final PlayerDirectory directory = mock(PlayerDirectory.class);
    final DailyTaskService tasks = mock(DailyTaskService.class);
    final UnifiedScheduler scheduler = mock(UnifiedScheduler.class);
    final Player player = mock(Player.class);
    final UUID id = UUID.randomUUID(), worldId = UUID.randomUUID();
    final PlayerSnapshot snapshot = new PlayerSnapshot(id,id,"test","Test",60,
            new PlayerSnapshot.Point(worldId,0,64,0,0,0),System.currentTimeMillis());
    final List<Runnable> delayed = new ArrayList<>(), repeating = new ArrayList<>();
    final List<TaskHandle> repeatingHandles = new ArrayList<>();
    CoupleTaskListener listener;
    MockedStatic<Bukkit> bukkit;

    @BeforeEach void setup() {
        bukkit = mockStatic(Bukkit.class);
        when(marriages.directory()).thenReturn(directory);
        when(directory.capture(player)).thenReturn(snapshot);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world,0,64,0));
        when(scheduler.repeatEntity(eq(player),any(),anyLong())).thenAnswer(call -> {
            Runnable callback=call.getArgument(1);
            TaskHandle handle=mock(TaskHandle.class);
            repeating.add(callback); repeatingHandles.add(handle); return handle;
        });
        when(scheduler.runEntityLater(eq(player),any(),anyLong())).thenAnswer(call -> {
            delayed.add(call.getArgument(1)); return mock(TaskHandle.class);
        });
        when(scheduler.runEntity(eq(player),any())).thenAnswer(call -> {
            delayed.add(call.getArgument(1));return mock(TaskHandle.class);
        });
        doAnswer(call -> {
            Consumer<Player> action=call.getArgument(1);
            delayed.add(() -> action.accept(player)); return null;
        }).when(scheduler).player(eq(id),any(),any());
        listener=new CoupleTaskListener(marriages,tasks,scheduler);
    }
    @AfterEach void cleanup() { listener.close(); bukkit.close(); }
    void join() { var event=mock(PlayerJoinEvent.class); when(event.getPlayer()).thenReturn(player); listener.join(event); }
    void death() { var event=mock(PlayerDeathEvent.class); when(event.getEntity()).thenReturn(player); listener.death(event); }
    void quit() { var event=mock(PlayerQuitEvent.class); when(event.getPlayer()).thenReturn(player); listener.quit(event); }
    void trade(int statistic) {
        when(player.getStatistic(Statistic.TRADED_WITH_VILLAGER)).thenReturn(statistic);
        var event=mock(PlayerTradeEvent.class); when(event.getPlayer()).thenReturn(player); listener.traded(event);
    }
    void confirm(int statistic) {
        when(player.getStatistic(Statistic.TRADED_WITH_VILLAGER)).thenReturn(statistic);
        var callbacks=List.copyOf(delayed); delayed.clear(); callbacks.forEach(Runnable::run);
    }

    @Test void deathKeepsSamplerAndRespawnResumesWithoutAnotherJoin() {
        join(); death();
        verify(repeatingHandles.getFirst(),never()).cancel();
        when(player.isDead()).thenReturn(true); repeating.getFirst().run();
        verify(directory,never()).capture(player);
        when(player.isDead()).thenReturn(false); repeating.getFirst().run();
        verify(directory).capture(player);
    }
    @Test void quitAndDisableCancelSamplers() {
        join(); quit(); verify(repeatingHandles.getFirst()).cancel();
        join(); listener.close(); verify(repeatingHandles.getLast()).cancel();
    }
    @Test void deadPlayersDoNotReadWorldOrAddExploration() {
        join(); when(player.isDead()).thenReturn(true); repeating.getFirst().run();
        verify(player,never()).getLocation(); verifyNoInteractions(tasks);
    }
    @Test void multipleTradesBeforeNextTickCountEachStatisticIncrementOnce() {
        trade(10); trade(11); confirm(12);
        verify(tasks,times(1)).record(snapshot,"TRADE","*",2);
        verifyNoMoreInteractions(tasks);
    }
    @Test void laterConfirmationStartsFromNewBaseline() {
        trade(10); confirm(11); trade(11); confirm(12);
        verify(tasks,times(2)).record(snapshot,"TRADE","*",1);
        verifyNoMoreInteractions(tasks);
    }
    @Test void largeSuccessfulBatchIsNotTruncatedToOneStack() {
        trade(10); trade(74); confirm(110);
        verify(tasks,times(1)).record(snapshot,"TRADE","*",100);
        verifyNoMoreInteractions(tasks);
    }
    @Test void failedTradeDoesNotCount() { trade(10); confirm(10); verifyNoInteractions(tasks); }
    @Test void resetStatisticDoesNotCount() { trade(10); confirm(0); verifyNoInteractions(tasks); }
    @Test void callbackAfterReloadDoesNotCreditOldTrades() {
        trade(10); listener.reload(); confirm(11); verifyNoInteractions(tasks);
    }
    @Test void callbackAfterDeathDoesNotCreditDeadPlayer() {
        trade(10); death(); when(player.isDead()).thenReturn(true); confirm(11); verifyNoInteractions(tasks);
    }
    @Test void callbackAfterCloseDoesNotCount() {
        trade(10); listener.close(); confirm(11); verifyNoInteractions(tasks);
    }
    @Test void oldCallbackCannotConsumeNewSessionTradeWithSameBaseline() {
        trade(10); quit(); join(); trade(10); confirm(11);
        verify(tasks,times(1)).record(snapshot,"TRADE","*",1);
        verifyNoMoreInteractions(tasks);
    }
    @Test void smeltingBeforeReloadCannotCreditNewConfiguration() {
        var e=mock(FurnaceExtractEvent.class);when(e.getPlayer()).thenReturn(player);
        when(e.getItemType()).thenReturn(Material.IRON_INGOT);when(e.getItemAmount()).thenReturn(16);
        listener.smelt(e);listener.reload();confirm(0);verifyNoInteractions(tasks);
    }
    @Test void smeltingCountsActualExtractedAmount() {
        var e=mock(FurnaceExtractEvent.class);when(e.getPlayer()).thenReturn(player);
        when(e.getItemType()).thenReturn(Material.IRON_INGOT);when(e.getItemAmount()).thenReturn(16);
        listener.smelt(e);confirm(0);verify(tasks).record(snapshot,"SMELT","IRON_INGOT",16);
    }
    @Test void bootstrapStartsOnlinePlayerOnceAndCloseInvalidatesPendingBootstrap() {
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
        listener.bootstrap();assertTrue(repeating.isEmpty());confirm(0);assertEquals(1,repeating.size());
        join();listener.bootstrap();confirm(0);assertEquals(1,repeating.size());
        quit();listener.bootstrap();listener.close();confirm(0);assertEquals(1,repeating.size());
    }
}
