package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.*;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.*;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 驱动实际串行 IO 队列和 SQLite，验证事件发生、批处理与重载之间的边界。 */
class DailyTaskBatchFlowTest {
    @TempDir Path root;
    final Queue<Runnable> jobs = new ArrayDeque<>();
    final UnifiedScheduler scheduler = mock(UnifiedScheduler.class);
    final PlayerDirectory directory = mock(PlayerDirectory.class);
    final UUID actorId = UUID.randomUUID(), partnerId = UUID.randomUUID(), world = UUID.randomUUID();
    final TaskDefinition definition = new TaskDefinition("build", "PLACE_BLOCK", "共同建造", "STONE", 3, 20);
    ConfigurationManager config; DatabaseManager database; MarriageService marriages;
    IoDispatcher io; DailyTaskService tasks; Runnable flush;

    @BeforeEach void setup() {
        var plugin = mock(JavaPlugin.class); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var settings = DatabaseSettings.sqlite(root.resolve("tasks.db"));
        config = new ConfigurationManager(plugin);
        var main = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
        config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(), Map.of("config.yml", main),
                Map.of(), settings, new TaskCatalog(50, Collections.nCopies(30, definition), List.of())));
        database = new DatabaseManager(settings);
        database.use(r -> { assertTrue(r.createMarriage(actorId, partnerId, "NORMAL", System.currentTimeMillis()-1000)); return null; });
        when(scheduler.runAsync(any())).thenAnswer(c -> { jobs.add(c.getArgument(0)); return mock(TaskHandle.class); });
        when(scheduler.runRepeatingAsync(any(), anyLong(), anyLong(), any())).thenAnswer(c -> { flush=c.getArgument(0); return mock(TaskHandle.class); });
        when(directory.identity(partnerId)).thenAnswer(c -> player(partnerId, world, 50, System.currentTimeMillis()));
        io = new IoDispatcher(scheduler);
        marriages = new MarriageService(plugin, config, mock(MessageService.class), database, scheduler, io, directory, false);
        marriages.initializeAfterDatabase(); tasks = new DailyTaskService(marriages, config, scheduler);
    }
    PlayerSnapshot player(UUID id, UUID worldId, double x, long at) {
        return new PlayerSnapshot(id,id,"name:"+id,"Tester",60,new PlayerSnapshot.Point(worldId,x,64,0,0,0),at);
    }
    PlayerSnapshot actor() { return player(actorId,world,0,System.currentTimeMillis()); }
    void record(long amount) { tasks.record(actor(),"PLACE_BLOCK","STONE",amount); }
    void drain() { for(int n=0;n<20&&!jobs.isEmpty();n++)jobs.remove().run(); assertTrue(jobs.isEmpty()); }
    void assertEmpty() {
        assertTrue(database.<Boolean>use(r -> r.entries(DailyTaskLedger.BUCKET).isEmpty()),"失效批次不得创建任务");
        assertEquals(0L,database.<Long>use(r -> r.findByPlayer(actorId).bond()));
    }
    @AfterEach void cleanup() { tasks.close(); marriages.shutdown(); io.close(); database.close(); }

    @Test void successfulBatchAtExactDistanceRewardsOnce() {
        record(2); record(1); flush.run(); drain(); record(100); flush.run(); drain();
        assertEquals(20L,database.<Long>use(r -> r.findByPlayer(actorId).bond()));
        assertEquals(1,database.<Integer>use(r -> r.entries(DailyTaskLedger.BUCKET).size()));
    }
    @Test void queuedFlushCannotWriteAfterClose() { record(3); flush.run(); tasks.close(); drain(); assertEmpty(); }
    @Test void queuedFlushCannotWriteAfterServiceReload() {
        record(3); flush.run(); tasks.reload(); drain(); assertEmpty();
        record(3); flush.run(); drain(); assertEquals(20L,database.<Long>use(r -> r.findByPlayer(actorId).bond()));
    }
    @Test void pendingEventsAreDiscardedOnReload() { record(3); tasks.reload(); flush.run(); drain(); assertEmpty(); }
    void switchPool() {
        var s=database.currentSettings();
        assertTrue(database.switchTo(new DatabaseSettings(s.type(),s.file(),s.host(),s.port(),s.database(),
                s.username(),s.password(),s.parameters(),s.poolSize(),s.timeout()+1000)));
    }
    @Test void submittedBatchDoesNotWriteAfterSameFilePoolSwitch() {
        record(3); flush.run(); switchPool(); drain(); assertEmpty();
    }
    @Test void unflushedObservationDoesNotCrossDatabaseGeneration() {
        record(3); switchPool(); flush.run(); drain(); assertEmpty();
        record(3); flush.run(); drain(); assertEquals(20L,database.<Long>use(r -> r.findByPlayer(actorId).bond()));
    }
    @Test void configGenerationChangeInvalidatesSubmittedBatch() {
        record(3); flush.run(); var old=config.snapshot();
        config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(),old.files(),old.menus(),old.database(),old.tasks()));
        drain(); assertEmpty();
    }
    @Test void otherWorldAndBeyondRadiusDoNotCount() {
        when(directory.identity(partnerId)).thenReturn(player(partnerId,UUID.randomUUID(),0,System.currentTimeMillis()));
        record(3);
        when(directory.identity(partnerId)).thenReturn(player(partnerId,world,50.01,System.currentTimeMillis()));
        record(3); flush.run(); drain(); assertEmpty();
    }
    @Test void staleActorCannotCreditOrQualifyForExploration() {
        var stale=player(actorId,world,0,System.currentTimeMillis()-3000);
        assertFalse(tasks.nearbyPartner(stale));
        tasks.record(stale,"PLACE_BLOCK","STONE",3); flush.run(); drain(); assertEmpty();
    }
    @Test void futurePartnerCannotCreditOrQualifyForExploration() {
        when(directory.identity(partnerId)).thenReturn(player(partnerId,world,0,System.currentTimeMillis()+60_000));
        assertFalse(tasks.nearbyPartner(actor())); record(3); flush.run(); drain(); assertEmpty();
    }
    @Test void closedServiceDoesNotQualifyForExploration() { tasks.close(); assertFalse(tasks.nearbyPartner(actor())); }
}
