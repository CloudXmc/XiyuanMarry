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

class DailyTaskReadFlowTest {
    @TempDir Path root;
    final Queue<Runnable> jobs=new ArrayDeque<>();
    final UnifiedScheduler scheduler=mock(UnifiedScheduler.class);
    final TaskHandle timer=mock(TaskHandle.class);
    final MessageService messages=mock(MessageService.class);
    final UUID actorId=UUID.randomUUID(),partnerId=UUID.randomUUID();
    final PlayerSnapshot actor=new PlayerSnapshot(actorId,actorId,"name:alice","Alice",60,null,1);
    final List<DailyTask> replies=new ArrayList<>();
    final List<Throwable> failures=new ArrayList<>();
    ConfigurationManager config; DatabaseManager database; MarriageService marriages;
    IoDispatcher io; DailyTaskService tasks;

    @BeforeEach void setup(){
        var plugin=mock(JavaPlugin.class);when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var settings=DatabaseSettings.sqlite(root.resolve("tasks.db"));
        config=new ConfigurationManager(plugin);
        var main=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
        var yaml=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/tasks.yml").toFile());
        config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(),Map.of("config.yml",main),Map.of(),settings,TaskCatalog.parse(yaml,20)));
        database=new DatabaseManager(settings);seedMarriage();
        when(scheduler.runAsync(any())).thenAnswer(call->{jobs.add(call.getArgument(0));return mock(TaskHandle.class);});
        when(scheduler.runRepeatingAsync(any(),anyLong(),anyLong(),any())).thenReturn(timer);
        io=new IoDispatcher(scheduler);
        var directory=mock(PlayerDirectory.class);when(directory.live(actorId)).thenReturn(actor);
        marriages=new MarriageService(plugin,config,messages,database,scheduler,io,directory,false);
        marriages.initializeAfterDatabase();tasks=new DailyTaskService(marriages,config,scheduler);
    }
    void seedMarriage(){database.use(r->{assertTrue(r.createMarriage(actorId,partnerId,"NORMAL",System.currentTimeMillis()-1000));return null;});}
    void query(){tasks.load(actor,replies::add,failures::add);}
    void drain(){for(int i=0;i<20&&!jobs.isEmpty();i++)jobs.remove().run();assertTrue(jobs.isEmpty());}
    void assertNoTask(){assertTrue(database.<Boolean>use(r->r.entries(DailyTaskLedger.BUCKET).isEmpty()),"旧查询不得初始化任务记录");}
    void publishReload(){var old=config.snapshot();config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(),old.files(),old.menus(),old.database(),old.tasks()));tasks.reload();}
    @AfterEach void cleanup(){tasks.close();marriages.shutdown();io.close();database.close();jobs.clear();}

    @Test void successfulReadInitializesTaskAndCanBeRepeated(){
        query();drain();query();drain();assertEquals(2,replies.size());assertTrue(failures.isEmpty());
        assertEquals(replies.getFirst(),replies.getLast());
        assertEquals(1,database.<Integer>use(r->r.entries(DailyTaskLedger.BUCKET).size()));
    }
    @Test void damagedRecordReportsFriendlyErrorAndCanBeReadAfterManualRepair(){
        var player=mock(org.bukkit.entity.Player.class);
        doAnswer(c->{jobs.add(()->c.<java.util.function.Consumer<org.bukkit.entity.Player>>getArgument(1).accept(player));return null;})
            .when(scheduler).player(eq(actorId),any());
        String id=database.use(r->r.findByPlayer(actorId).id());
        database.use(r->{r.put(DailyTaskLedger.BUCKET,id,"null");return null;});
        query();drain();assertTrue(replies.isEmpty());assertEquals(1,failures.size());
        Throwable reason=failures.getFirst();while(reason.getCause()!=null)reason=reason.getCause();
        assertEquals("task-data-invalid",assertInstanceOf(RuleViolation.class,reason).key());
        verify(messages).send(player,"task-data-invalid");
        assertEquals("null",database.<String>use(r->r.get(DailyTaskLedger.BUCKET,id)));
        var restored=new DailyTask(id,0,config.snapshot().tasks().forDay(0),0,false);
        database.use(r->{r.put(DailyTaskLedger.BUCKET,id,new com.google.gson.Gson().toJson(restored));return null;});
        query();drain();assertEquals(List.of(restored),replies);
    }
    @Test void queuedReadAfterCloseDoesNotCreateTask(){
        query();tasks.close();drain();assertNoTask();assertTrue(replies.isEmpty());assertEquals(1,failures.size());
    }
    @Test void queuedReadAfterReloadDoesNotCreateTask(){
        query();publishReload();drain();assertNoTask();assertTrue(replies.isEmpty());assertEquals(1,failures.size());
        query();drain();assertEquals(1,replies.size(),"新代次允许重新查询");
    }
    @Test void queuedReadAfterSwitchCannotWriteReplacementDatabase(){
        query();assertTrue(database.switchTo(DatabaseSettings.sqlite(root.resolve("replacement.db"))));seedMarriage();
        drain();assertNoTask();assertTrue(replies.isEmpty());assertEquals(1,failures.size());
    }
    @Test void invalidatedReadStillFinishesFailureCallback(){
        query();publishReload();drain();assertEquals(1,failures.size(),"调用方必须收到终止回调来释放请求");
    }
    @Test void nullActorFailsGracefullyWithoutIo(){
        assertDoesNotThrow(()->tasks.load(null,replies::add,failures::add));
        assertEquals(1,failures.size());assertTrue(jobs.isEmpty());assertNoTask();
    }
    @Test void closedServiceRejectsNewReadsBeforeSubmitting(){
        tasks.close();query();assertTrue(jobs.isEmpty());assertEquals(1,failures.size());assertNoTask();
    }
    @Test void explicitServiceReloadInvalidatesQueuedReadWithoutChangingConfiguration(){
        query();tasks.reload();drain();assertNoTask();assertTrue(replies.isEmpty());assertEquals(1,failures.size());
    }
    @Test void departedPlayerCannotInitializeTaskFromQueuedRead(){
        query();when(marriages.directory().live(actorId)).thenReturn(null);
        drain();assertNoTask();assertTrue(replies.isEmpty());assertEquals(1,failures.size());
    }
    @Test void identityChangedBeforeIoDoesNotInitializeOldIdentityTask(){
        query();when(marriages.directory().live(actorId)).thenReturn(new PlayerSnapshot(actorId,UUID.randomUUID(),"other","Other",60,null,1));
        drain();assertNoTask();assertTrue(replies.isEmpty());assertEquals(1,failures.size());
    }
    @Test void unavailableDatabaseRejectsReadWithoutIo(){
        database.close();assertDoesNotThrow(this::query);assertTrue(jobs.isEmpty());assertEquals(1,failures.size());
    }
}
