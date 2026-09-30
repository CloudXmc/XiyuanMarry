package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.*;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import org.bukkit.*;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 所有关系写入使用真实 SQLite；只模拟实体和调度以排列请求提交顺序。 */
class MarriageMutationFlowTest {
    @TempDir Path root;
    final UnifiedScheduler scheduler=mock(UnifiedScheduler.class);
    final MessageService messages=mock(MessageService.class);
    final ConsoleCommandSender console=mock(ConsoleCommandSender.class);
    final Queue<Runnable> async=new ArrayDeque<>(), owners=new ArrayDeque<>();
    final Map<UUID,Player> online=new HashMap<>();
    // Paper Location keeps World through a weak reference; retain mock worlds for the test lifecycle.
    final List<World> loadedWorlds=new ArrayList<>();
    ConfigurationManager config;DatabaseManager database;IoDispatcher io;
    PlayerDirectory directory;MarriageService marriages;Player alice,bob;PlayerSnapshot a,b;DatabaseSettings original;

    @BeforeEach void setup(){
        var plugin=mock(JavaPlugin.class);when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        config=spy(new ConfigurationManager(plugin));var files=new HashMap<String,YamlConfiguration>();
        for(String name:List.of("config.yml","messages.yml"))files.put(name,YamlConfiguration.loadConfiguration(Path.of("src/main/resources",name).toFile()));
        original=DatabaseSettings.sqlite(root.resolve("original.db"));
        config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(),files,Map.of(),original,null));database=new DatabaseManager(original);
        when(scheduler.runAsync(any())).thenAnswer(c->{async.add(c.getArgument(0));return mock(TaskHandle.class);});
        when(scheduler.repeatEntity(any(),any(),anyLong())).thenReturn(mock(TaskHandle.class));
        when(scheduler.runGlobal(any())).thenAnswer(c->{owners.add(c.getArgument(0));return mock(TaskHandle.class);});
        doAnswer(c->{UUID id=c.getArgument(0);Consumer<Player> callback=c.getArgument(1);owners.add(()->{if(online.containsKey(id))callback.accept(online.get(id));});return null;}).when(scheduler).player(any(),any());
        directory=new PlayerDirectory(config,scheduler);io=new IoDispatcher(scheduler);marriages=new MarriageService(plugin,config,messages,database,scheduler,io,directory);
        alice=player("Alice");bob=player("Bob");a=directory.live(alice.getUniqueId());b=directory.live(bob.getUniqueId());
    }
    Player player(String name){
        var p=mock(Player.class);var world=mock(World.class);when(world.getUID()).thenReturn(UUID.randomUUID());
        loadedWorlds.add(world);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.getName()).thenReturn(name);
        when(p.getLocation()).thenReturn(new Location(world,0,70,0));when(p.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(72_000);
        online.put(p.getUniqueId(),p);directory.join(p);return p;
    }
    void drain(){
        try(var bukkit=mockStatic(Bukkit.class)){bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            while(!async.isEmpty())async.remove().run();while(!owners.isEmpty())owners.remove().run();}
    }
    MarriageRecord record(){return database.use(r->r.findByPlayer(a.id()));}
    MarriageRecord seed(String action){
        database.use(r->{r.deleteMarriage(a.id());
            if(action.equals("cancel"))r.createEngagement(a.id(),b.id(),"WEDDING",System.currentTimeMillis());
            else if(!action.equals("force"))r.createMarriage(a.id(),b.id(),"NORMAL",System.currentTimeMillis());
            if(action.equals("withdraw"))r.requestDivorce(a.id(),System.currentTimeMillis()+60_000);return null;});
        marriages.submit(null,r->null,x->{});drain();return record();
    }
    void invoke(String action){invoke(action,a.liveId());}
    void invoke(String action,UUID admin){
        switch(action){case "divorce"->marriages.divorce(a,false);case "withdraw"->marriages.divorce(a,true);case "cancel"->marriages.cancel(a);
            case "admin-divorce"->marriages.admin(admin,"divorce",a.id(),null,0);
            case "clear","setexp","setlevel","force"->marriages.admin(admin,action,a.id(),b.id(),3);
            default->throw new AssertionError(action);}
    }
    void republish(){var old=config.snapshot();config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(),old.files(),old.menus(),old.database(),old.tasks()));}
    @AfterEach void close(){marriages.shutdown();directory.close();io.close();database.close();loadedWorlds.clear();}

    @ParameterizedTest @ValueSource(strings={"divorce","withdraw","cancel","admin-divorce","clear","setexp","setlevel","force"})
    void queuedMutationCannotWriteReplacementDatabase(String action){
        MarriageRecord before=seed(action);invoke(action);
        database.switchTo(DatabaseSettings.sqlite(root.resolve("replacement.db")));
        database.use(r->{if(!action.equals("force")){
            if(action.equals("cancel"))r.createEngagement(a.id(),b.id(),"WEDDING",System.currentTimeMillis());
            else r.createMarriage(a.id(),b.id(),"NORMAL",System.currentTimeMillis());
            if(action.equals("withdraw"))r.requestDivorce(a.id(),System.currentTimeMillis()+60_000);}
            return null;});
        MarriageRecord replacement=record();drain();assertEquals(replacement,record());
        verify(messages).send(alice,"relationship-request-stale");
        database.switchTo(original);assertEquals(before,record());
    }
    @ParameterizedTest @ValueSource(strings={"divorce","withdraw","cancel","admin-divorce","clear","setexp","setlevel","force"})
    void queuedMutationCannotSurviveConfigurationReload(String action){
        var before=seed(action);invoke(action);republish();drain();assertEquals(before,record());
        verify(messages).send(alice,"relationship-request-stale");
    }
    @ParameterizedTest @ValueSource(strings={"divorce","withdraw","cancel","admin-divorce","clear","setexp","setlevel","force"})
    void queuedMutationCannotSurviveActorReconnect(String action){
        var before=seed(action);invoke(action);directory.leave(a.liveId());directory.join(alice);drain();assertEquals(before,record());
    }
    @ParameterizedTest @ValueSource(strings={"divorce","withdraw","cancel","admin-divorce","clear","setexp","setlevel"})
    void queuedMutationCannotAffectNewRelationshipWithSamePartner(String action){
        var old=seed(action);invoke(action);
        database.use(r->{r.deleteMarriage(a.id());
            if(action.equals("cancel"))r.createEngagement(a.id(),b.id(),"WEDDING",System.currentTimeMillis());
            else r.createMarriage(a.id(),b.id(),"NORMAL",System.currentTimeMillis());
            if(action.equals("withdraw"))r.requestDivorce(a.id(),System.currentTimeMillis()+120_000);return null;});
        var replacement=record();assertNotEquals(old.id(),replacement.id());drain();assertEquals(replacement,record());
    }
    @Test void oldWithdrawalCannotCancelAReplacementDivorceRequest(){
        var before=seed("withdraw");marriages.divorce(a,true);
        database.use(r->{r.withdrawDivorce(a.id());r.requestDivorce(a.id(),before.divorceAt()+60_000);return null;});
        var renewed=record();drain();assertEquals(renewed,record());
    }
    @ParameterizedTest @ValueSource(strings={"admin-divorce","clear","setexp","setlevel","force"})
    void consoleQueuedMutationAlsoRejectsChangedGeneration(String action){
        var before=seed(action);invoke(action,null);republish();drain();assertEquals(before,record());
        verify(messages).send(console,"relationship-request-stale");
    }
    @Test void profileRegistrationCannotWriteAnotherDatabase(){
        marriages.register(a);database.switchTo(DatabaseSettings.sqlite(root.resolve("profiles.db")));drain();
        assertNull(database.use(r->r.get("profiles",a.id().toString())));
    }
    @Test void staleProfileRegistrationCannotOverwriteNewLogin(){
        marriages.register(a);directory.leave(a.liveId());directory.join(alice);
        database.use(r->{r.put("profiles",a.id().toString(),"new-session-marker");return null;});drain();
        assertEquals("new-session-marker",database.use(r->r.get("profiles",a.id().toString())));
    }
    @Test void nullPlayerMutationSnapshotsAreIgnored(){
        assertDoesNotThrow(()->{marriages.divorce(null,false);marriages.divorce(null,true);marriages.cancel(null);marriages.register(null);});
        assertTrue(async.isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"divorce","withdraw","cancel","admin-divorce","clear","setexp","setlevel","force"})
    void validMutationsStillWorkWhenLiveAndStorageUuidsDiffer(String action){
        var before=seed(action);assertNotEquals(a.id(),a.liveId());invoke(action);drain();
        switch(action){
            case "divorce"->{assertEquals(MarriageState.DIVORCE_PENDING,record().state());assertEquals(before.id(),record().id());}
            case "withdraw"->{assertEquals(MarriageState.MARRIED,record().state());assertEquals(0,record().divorceAt());}
            case "cancel","admin-divorce","clear"->assertNull(record());
            case "setexp"->assertEquals(3,record().bond());
            case "setlevel"->assertEquals(250,record().bond());
            case "force"->{assertEquals(MarriageState.MARRIED,record().state());assertEquals("ADMIN",record().type());}
        }
    }
    @ParameterizedTest @ValueSource(strings={"admin-divorce","clear","setexp","setlevel","force"})
    void consoleCanOperateOnOfflineTargets(String action){
        seed(action);directory.leave(a.liveId());directory.leave(b.liveId());invoke(action,null);drain();
        verify(messages).send(console,"admin-success");
        if(action.equals("clear")||action.equals("admin-divorce"))assertNull(record());
        else assertNotNull(record());
    }
    @Test void administratorRelationshipIsIndependentFromTargetRelationship(){
        seed("divorce");var admin=player("Admin");
        marriages.admin(admin.getUniqueId(),"setexp",a.id(),null,42);drain();
        assertEquals(42,record().bond());verify(messages).send(admin,"admin-success");
    }
    @Test void missingConsoleTargetHasFriendlyFeedback(){
        marriages.admin(null,"divorce",a.id(),null,0);drain();
        verify(messages).send(console,"not-married");
    }
    @Test void staleProfileIsNotReportedAsConsoleAdminFailure(){
        marriages.register(a);republish();drain();verifyNoInteractions(messages);
        assertNull(database.use(r->r.get("profiles",a.id().toString())));
    }
    @Test void currentRegistrationDoesNotBlockPlayerOperations(){
        seed("divorce");marriages.register(a);marriages.divorce(a,false);drain();
        assertNotNull(database.use(r->r.get("profiles",a.id().toString())));
        assertEquals(MarriageState.DIVORCE_PENDING,record().state());
        verify(messages,never()).send(alice,"busy");
    }
    @ParameterizedTest @ValueSource(strings={"divorce","cancel","admin-divorce","clear","setexp","setlevel"})
    void requestWithoutVisibleRelationshipCannotMutateLaterRelationship(String action){
        invoke(action);database.use(r->{if(action.equals("cancel"))r.createEngagement(a.id(),b.id(),"WEDDING",System.currentTimeMillis());
            else r.createMarriage(a.id(),b.id(),"NORMAL",System.currentTimeMillis());return null;});
        var created=record();drain();assertEquals(created,record());
    }
    @Test void queuedBondCannotWriteReplacementDatabase(){
        var originalRecord=seed("divorce");marriages.addBond(a.id(),20);
        database.switchTo(DatabaseSettings.sqlite(root.resolve("bond-replacement.db")));
        database.use(r->r.createMarriage(a.id(),b.id(),"NORMAL",System.currentTimeMillis()));
        var replacement=record();drain();assertEquals(replacement,record());
        database.switchTo(original);assertEquals(originalRecord,record());
    }
    @Test void queuedBondCannotSurviveConfigurationReload(){
        var before=seed("divorce");marriages.addBond(a.id(),20);republish();drain();assertEquals(before,record());
        verifyNoInteractions(messages);
    }
    @Test void queuedBondCannotCreditRemarriageWithSamePartner(){
        var old=seed("divorce");marriages.addBond(a.id(),20);
        database.use(r->{r.deleteMarriage(a.id());return r.createMarriage(a.id(),b.id(),"NORMAL",System.currentTimeMillis());});
        var replacement=record();assertNotEquals(old.id(),replacement.id());drain();assertEquals(replacement,record());
    }
    @Test void bondWithoutCapturedMarriageCannotCreditLaterMarriage(){
        marriages.addBond(a.id(),20);database.use(r->r.createMarriage(a.id(),b.id(),"NORMAL",System.currentTimeMillis()));
        var created=record();drain();assertEquals(created,record());
    }
    @Test void engagementCannotBankBondForLaterWeddingCompletion(){
        seed("cancel");marriages.addBond(a.id(),20);
        database.use(r->r.completeMarriage(a.id(),b.id(),"WEDDING",System.currentTimeMillis()));
        var married=record();drain();assertEquals(married,record());
    }
    @Test void bondRejectsExpiredDivorceEvenBeforeMaintenance(){
        seed("divorce");database.use(r->r.requestDivorce(a.id(),System.currentTimeMillis()-1));
        marriages.submit(null,r->null,x->{});drain();var before=record();
        marriages.addBond(a.id(),20);drain();assertEquals(before,record());
    }
    @Test void independentBondAwardsAccumulateForSameOfflineMarriage(){
        var before=seed("divorce");directory.leave(a.liveId());directory.leave(b.liveId());
        marriages.addBond(a.id(),5);marriages.addBond(b.id(),7);drain();
        assertEquals(before.id(),record().id());assertEquals(12,record().bond());assertEquals(12,record().totalBond());
        assertEquals(record(),marriages.view().byPlayer().get(a.id()));verifyNoInteractions(messages);
    }
    @Test void bondInvalidInputsDoNotEnterQueue(){
        seed("divorce");marriages.addBond(null,20);marriages.addBond(a.id(),0);marriages.addBond(a.id(),-1);
        assertTrue(async.isEmpty());assertEquals(0,record().bond());
    }
    @Test void shutdownDiscardsQueuedBond(){
        var before=seed("divorce");marriages.addBond(a.id(),20);marriages.shutdown();drain();assertEquals(before,record());
    }
    @Test void withdrawalCannotUndoExpiredCoolingPeriod(){
        seed("withdraw");database.use(r->{r.withdrawDivorce(a.id());r.requestDivorce(a.id(),System.currentTimeMillis()-1);return null;});
        marriages.submit(null,r->null,x->{});drain();marriages.divorce(a,true);drain();
        assertTrue(record()==null||record().state()==MarriageState.DIVORCE_PENDING);
        verify(messages,never()).send(alice,"divorce-withdrawn","hours",24L);
    }
}
