package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.listener.PlayerLifecycleListener;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.*;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.configuration.file.YamlConfiguration;
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

/** 使用真实身份目录和 SQLite，手动控制异步事务与实体消息的先后顺序。 */
class NormalMarriageFlowTest {
    @TempDir Path root;
    final UnifiedScheduler scheduler=mock(UnifiedScheduler.class);
    final MessageService messages=mock(MessageService.class);
    final Queue<Runnable> async=new ArrayDeque<>(), owners=new ArrayDeque<>();
    final Map<UUID,Player> online=new HashMap<>();
    // Location 只弱引用 World；测试模拟服务端注册表，在用例结束前保留已加载世界。
    final List<World> loadedWorlds=new ArrayList<>();
    ConfigurationManager config; DatabaseManager database; IoDispatcher io;
    PlayerDirectory directory; MarriageService marriages;
    Player alice,bob; PlayerSnapshot a,b;
    DatabaseSettings original; String proposal;

    @BeforeEach void setup(){
        var plugin=mock(JavaPlugin.class);when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        config=spy(new ConfigurationManager(plugin));
        var files=new HashMap<String,YamlConfiguration>();
        for(String name:List.of("config.yml","messages.yml"))
            files.put(name,YamlConfiguration.loadConfiguration(Path.of("src/main/resources",name).toFile()));
        original=DatabaseSettings.sqlite(root.resolve("original.db"));
        config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(),files,Map.of(),original,null));
        database=new DatabaseManager(original);
        when(scheduler.runAsync(any())).thenAnswer(c->{async.add(c.getArgument(0));return mock(TaskHandle.class);});
        when(scheduler.repeatEntity(any(),any(),anyLong())).thenReturn(mock(TaskHandle.class));
        doAnswer(c->{UUID id=c.getArgument(0);Consumer<Player> task=c.getArgument(1);
            owners.add(()->{Player p=online.get(id);if(p!=null)task.accept(p);});return null;
        }).when(scheduler).player(any(),any());
        directory=new PlayerDirectory(config,scheduler);io=new IoDispatcher(scheduler);
        marriages=new MarriageService(plugin,config,messages,database,scheduler,io,directory);
        alice=player("Alice");bob=player("Bob");
        a=directory.live(alice.getUniqueId());b=directory.live(bob.getUniqueId());
        proposal=marriages.json().toJson(new Proposal(UUID.randomUUID(),a.id(),b.id(),"NORMAL",System.currentTimeMillis()+60_000));
    }
    Player player(String name){
        var p=mock(Player.class);var world=mock(World.class);
        loadedWorlds.add(world);
        when(world.getUID()).thenReturn(UUID.randomUUID());when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.getName()).thenReturn(name);when(p.getLocation()).thenReturn(new Location(world,0,70,0));
        when(p.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(72_000);
        online.put(p.getUniqueId(),p);directory.join(p);return p;
    }
    void seed(){database.use(r->{r.put("proposals",b.id().toString(),proposal);return null;});}
    void invoke(String op){switch(op){case "propose"->marriages.propose(a,b,"NORMAL");case "accept"->marriages.accept(b);case "deny"->marriages.deny(b);default->throw new AssertionError(op);}}
    Player actor(String op){return op.equals("propose")?alice:bob;}
    void drain(){while(!async.isEmpty())async.remove().run();while(!owners.isEmpty())owners.remove().run();}
    void unchanged(boolean seeded){
        assertTrue(database.<Boolean>use(r->r.findAll().isEmpty()));
        assertEquals(seeded?proposal:null,database.use(r->r.get("proposals",b.id().toString())));
        assertEquals(0L,database.<Long>use(r->MarriageService.number(r,"daily",marriages.today()+":"+a.id())));
    }
    @AfterEach void close(){marriages.shutdown();directory.close();io.close();database.close();loadedWorlds.clear();}

    @ParameterizedTest @ValueSource(strings={"propose","accept","deny"})
    void queuedRequestCannotWriteReplacementDatabase(String op){
        if(!op.equals("propose"))seed();invoke(op);
        database.switchTo(DatabaseSettings.sqlite(root.resolve("replacement.db")));
        if(!op.equals("propose"))seed();drain();
        unchanged(!op.equals("propose"));
        verify(messages).send(actor(op),"marriage-request-stale");
        database.switchTo(original);unchanged(!op.equals("propose"));
    }
    @ParameterizedTest @ValueSource(strings={"propose","accept","deny"})
    void queuedRequestCannotSurviveConfigurationReload(String op){
        if(!op.equals("propose"))seed();invoke(op);
        var before=config.snapshot();
        config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(),before.files(),before.menus(),before.database(),before.tasks()));
        drain();unchanged(!op.equals("propose"));
        verify(messages).send(actor(op),"marriage-request-stale");
    }
    @ParameterizedTest @ValueSource(strings={"propose","accept","deny"})
    void queuedRequestCannotSurviveActorReconnect(String op){
        if(!op.equals("propose"))seed();invoke(op);
        var actor=actor(op);directory.leave(actor.getUniqueId());directory.join(actor);
        drain();unchanged(!op.equals("propose"));
    }
    @Test void queuedProposalCannotTargetAReplacementLogin(){
        marriages.propose(a,b,"NORMAL");directory.leave(b.liveId());player("Bob");
        drain();unchanged(false);
    }
    @Test void queuedProposalCannotSurviveTargetReconnectWithSameUuid(){
        marriages.propose(a,b,"NORMAL");directory.leave(b.liveId());directory.join(bob);
        drain();unchanged(false);
    }
    @Test void queuedLogoutCleanupCannotDeleteProposalInReplacementDatabase(){
        seed();marriages.leave(a.liveId(),a.id());
        database.switchTo(DatabaseSettings.sqlite(root.resolve("replacement.db")));seed();
        drain();unchanged(true);database.switchTo(original);unchanged(true);
    }
    @Test void nullModeReportsArgumentErrorAndConsumesNothing(){
        marriages.propose(a,b,null);drain();unchanged(false);
        verify(messages).send(alice,"invalid-argument");
    }
    @Test void insufficientPlaytimeFeedbackContainsConfiguredMinutes(){
        when(alice.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(0);a=directory.capture(alice);
        marriages.propose(a,b,"NORMAL");drain();unchanged(false);
        verify(messages).send(alice,"minimum-online","minutes",30L);
    }
    @Test void newProposalCannotOverwriteMalformedTargetRecord(){
        proposal="{broken";seed();marriages.propose(a,b,"NORMAL");drain();unchanged(true);
    }
    @Test void denyingMalformedProposalPreservesItForAdministratorReview(){
        proposal="{broken";seed();marriages.deny(b);drain();unchanged(true);
        verify(messages).send(bob,"request-missing");
    }
    @Test void refreshedSnapshotWithinSameLoginStillAllowsNormalMarriage(){
        marriages.propose(a,b,"NORMAL");directory.capture(alice);directory.capture(bob);drain();
        marriages.accept(directory.capture(bob));drain();
        var married=database.use(r->r.findByPlayer(a.id()));
        assertNotNull(married);assertEquals(MarriageState.MARRIED,married.state());
        assertEquals("NORMAL",married.type());assertTrue(married.marriedAt()>0);
        assertNull(database.use(r->r.get("proposals",b.id().toString())));
        assertTrue(database.<Boolean>use(r->r.entries("weddings").isEmpty()));
    }

    @Test void offlineProposerCanStillBeAcceptedWithinProposalExpiry(){
        marriages.propose(a,b,"NORMAL");
        drain();
        directory.leave(a.liveId());
        marriages.accept(b);
        drain();
        var married=database.use(r->r.findByPlayer(b.id()));
        assertNotNull(married);
        assertEquals(MarriageState.MARRIED,married.state());
        assertEquals("NORMAL",married.type());
        assertNull(database.use(r->r.get("proposals",b.id().toString())));
    }
    @Test void realQuitEventKeepsProposalForOfflineAcceptance(){
        marriages.propose(a,b,"NORMAL");
        drain();
        new PlayerLifecycleListener(directory,mock(WeddingService.class),marriages,mock(BondAttributeService.class))
            .quit(new PlayerQuitEvent(alice,"quit"));
        drain();
        marriages.accept(b);
        drain();
        assertEquals(MarriageState.MARRIED,database.use(r->r.findByPlayer(b.id()).state()));
        assertNull(database.use(r->r.get("proposals",b.id().toString())));
    }
    @Test void realQuitEventKeepsWeddingProposalForOfflineAcceptance(){
        marriages.propose(a,b,"WEDDING");
        drain();
        new PlayerLifecycleListener(directory,mock(WeddingService.class),marriages,mock(BondAttributeService.class))
            .quit(new PlayerQuitEvent(alice,"quit"));
        drain();
        marriages.accept(b);
        drain();
        var engaged=database.use(r->r.findByPlayer(b.id()));
        assertNotNull(engaged);
        assertEquals(MarriageState.ENGAGED,engaged.state());
        assertEquals("WEDDING",engaged.type());
        assertNull(database.use(r->r.get("proposals",b.id().toString())));
    }
    @Test void logoutKeepsPendingProposalUntilItExpires(){
        seed();var carol=directory.live(player("Carol").getUniqueId());var dan=directory.live(player("Dan").getUniqueId());
        String other=marriages.json().toJson(new Proposal(UUID.randomUUID(),carol.id(),dan.id(),"NORMAL",System.currentTimeMillis()+60_000));
        database.use(r->{r.put("proposals",dan.id().toString(),other);return null;});
        marriages.leave(a.liveId(),a.id());drain();
        assertEquals(proposal,database.use(r->r.get("proposals",b.id().toString())));
        assertEquals(other,database.use(r->r.get("proposals",dan.id().toString())));
    }
    @Test void reconnectCannotOverwriteExistingPendingProposal(){
        seed();var carol=directory.live(player("Carol").getUniqueId());
        marriages.leave(a.liveId(),a.id());
        directory.leave(a.liveId());directory.join(alice);
        var refreshed=directory.live(a.liveId());
        marriages.propose(refreshed,carol,"NORMAL");
        drain();
        assertEquals(proposal,database.use(r->r.get("proposals",b.id().toString())));
        assertNull(database.use(r->r.get("proposals",carol.id().toString())));
    }
    @Test void queuedBlockCannotWriteReplacementDatabase(){
        marriages.block(a,b.id(),true);
        database.switchTo(DatabaseSettings.sqlite(root.resolve("block-replacement.db")));
        drain();
        assertNull(database.use(r->r.get("blocks",a.id()+":"+b.id())));
        verify(messages).send(alice,"marriage-request-stale");
    }
    @Test void queuedBlockCannotSurviveActorReconnect(){
        marriages.block(a,b.id(),true);
        directory.leave(a.liveId());directory.join(alice);
        drain();
        assertNull(database.use(r->r.get("blocks",a.id()+":"+b.id())));
    }
    @ParameterizedTest @ValueSource(strings={"block","unblock"})
    void blockChangesCannotSurviveReload(String action){
        boolean blocked=action.equals("block");String key=a.id()+":"+b.id();
        if(!blocked)database.use(r->{r.put("blocks",key,"1");return null;});
        marriages.block(a,b.id(),blocked);var old=config.snapshot();
        config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(),old.files(),old.menus(),old.database(),old.tasks()));
        drain();assertEquals(blocked?null:"1",database.use(r->r.get("blocks",key)));
        verify(messages).send(alice,"marriage-request-stale");
        verify(messages,never()).send(alice,"block-set");
    }
    @ParameterizedTest @ValueSource(strings={"database","reconnect","identity","shutdown"})
    void oldUnblockCannotRemoveCurrentPreference(String change){
        String key=a.id()+":"+b.id();database.use(r->{r.put("blocks",key,"1");return null;});
        marriages.block(a,b.id(),false);
        switch(change){
            case "database"->{database.switchTo(DatabaseSettings.sqlite(root.resolve("unblock.db")));database.use(r->{r.put("blocks",key,"1");return null;});}
            case "reconnect"->{directory.leave(a.liveId());directory.join(alice);}
            case "identity"->{when(alice.getName()).thenReturn("DifferentAlice");directory.capture(alice);}
            case "shutdown"->marriages.shutdown();
        }
        drain();assertEquals("1",database.use(r->r.get("blocks",key)));
        verify(messages,never()).send(alice,"block-set");
    }
    @Test void offlineTargetCanStillBeBlockedAndUnblocked(){
        directory.leave(b.liveId());String key=a.id()+":"+b.id();
        marriages.block(a,b.id(),true);drain();assertEquals("1",database.use(r->r.get("blocks",key)));
        marriages.block(a,b.id(),false);drain();assertNull(database.use(r->r.get("blocks",key)));
        verify(messages,times(2)).send(alice,"block-set");
    }
    @Test void blockingRequiresNoMarriageAndImmediatelyRejectsProposal(){
        marriages.block(b,a.id(),true);drain();marriages.propose(a,b,"NORMAL");drain();unchanged(false);
        verify(messages).send(alice,"blocked");
    }
    @Test void rejectedBlockReleasesBusyForFreshOperation(){
        marriages.block(a,b.id(),true);directory.leave(a.liveId());directory.join(alice);drain();
        marriages.block(directory.live(a.liveId()),b.id(),true);drain();
        assertEquals("1",database.use(r->r.get("blocks",a.id()+":"+b.id())));
        verify(messages).send(alice,"block-set");verify(messages,never()).send(alice,"busy");
    }
    @Test void missingBlockSnapshotAndTargetDoNotWriteInvalidKeys(){
        assertDoesNotThrow(()->{marriages.block(null,b.id(),true);marriages.block(a,null,true);});drain();
        assertTrue(database.<Boolean>use(r->r.entries("blocks").isEmpty()));verify(messages).send(alice,"invalid-argument");
    }
    @Test void quitCleansOfflineNameProposalBeforeProfileRegistrationCompletes(){
        seed();assertNotEquals(a.id(),a.liveId());assertTrue(marriages.view().profiles().isEmpty());
        new PlayerLifecycleListener(directory,mock(WeddingService.class),marriages,mock(BondAttributeService.class))
            .quit(new PlayerQuitEvent(alice,"quit"));drain();
        assertEquals(proposal,database.use(r->r.get("proposals",b.id().toString())));assertNull(directory.live(a.liveId()));
    }
    @Test void quitUsesCurrentIdentityInsteadOfOlderPersistedProfile(){
        seed();UUID olderId=UUID.randomUUID();
        database.use(r->{r.put("profiles",olderId.toString(),marriages.json().toJson(new PlayerProfile(olderId,"old-key","Alice",a.liveId())));return null;});
        marriages.submit(null,r->null,x->{});drain();
        new PlayerLifecycleListener(directory,mock(WeddingService.class),marriages,mock(BondAttributeService.class))
            .quit(new PlayerQuitEvent(alice,"quit"));drain();
        assertEquals(proposal,database.use(r->r.get("proposals",b.id().toString())));
        assertNotNull(database.use(r->r.get("profiles",olderId.toString())));
    }
    @Test void quitStillFindsOfflineIdentityWhenGameplaySnapshotHasExpired(){
        seed();var staleDirectory=spy(directory);doReturn(null).when(staleDirectory).live(a.liveId());
        new PlayerLifecycleListener(staleDirectory,mock(WeddingService.class),marriages,mock(BondAttributeService.class))
            .quit(new PlayerQuitEvent(alice,"quit"));drain();
        assertEquals(proposal,database.use(r->r.get("proposals",b.id().toString())));
        assertNull(directory.session(a.liveId()));
    }
    @Test void queuedAcceptanceCannotUseProposersNewLogin(){
        seed();marriages.accept(b);directory.leave(a.liveId());directory.join(alice);
        marriages.leave(a.liveId(),a.id());drain();
        assertTrue(database.<Boolean>use(r->r.findAll().isEmpty()));
        assertEquals(proposal,database.use(r->r.get("proposals",b.id().toString())));
    }
    @ParameterizedTest @ValueSource(strings={"propose","accept","deny"})
    void reloadQueuedFirstCannotApplyOldRequestToNewDatabase(String op) throws Exception {
        if(!op.equals("propose"))seed();
        var before=config.snapshot();var replacement=DatabaseSettings.sqlite(root.resolve("reload.db"));
        if(!op.equals("propose"))try(var candidate=new DatabaseManager(replacement)){
            candidate.use(r->{r.put("proposals",b.id().toString(),proposal);return null;});
        }
        doReturn(new ConfigurationManager.Snapshot(UUID.randomUUID(),before.files(),before.menus(),replacement,before.tasks())).when(config).prepare();
        marriages.reload(null);invoke(op);drain();
        assertEquals(replacement,database.currentSettings());unchanged(!op.equals("propose"));
        verify(messages).send(actor(op),"marriage-request-stale");
    }
    @ParameterizedTest @ValueSource(strings={"propose","accept","deny"})
    void shutdownBeforeQueueDrainPreventsMutation(String op){
        if(!op.equals("propose"))seed();invoke(op);marriages.shutdown();drain();
        unchanged(!op.equals("propose"));
    }
    @Test void rejectionReleasesBusyAndAllowsFreshProposal(){
        marriages.propose(a,b,"NORMAL");var before=config.snapshot();
        config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(),before.files(),before.menus(),before.database(),before.tasks()));
        drain();unchanged(false);marriages.propose(a,b,"NORMAL");drain();
        assertNotNull(database.use(r->r.get("proposals",b.id().toString())));
        assertEquals(1L,database.<Long>use(r->MarriageService.number(r,"daily",marriages.today()+":"+a.id())));
    }
    @ParameterizedTest @ValueSource(strings={"cooldown","blocked","already-married","minimum-online"})
    void acceptanceRevalidatesEligibilityBeforeConsumingProposal(String reason){
        seed();marriages.accept(b);
        switch(reason){
            case "cooldown"->database.use(r->{r.put("cooldowns",a.id().toString(),Long.toString(System.currentTimeMillis()+60_000));return null;});
            case "blocked"->database.use(r->{r.put("blocks",b.id()+":"+a.id(),"1");return null;});
            case "already-married"->database.use(r->r.createMarriage(a.id(),UUID.randomUUID(),"NORMAL",System.currentTimeMillis()));
            case "minimum-online"->{when(alice.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(0);directory.capture(alice);}
        }
        drain();assertNull(database.use(r->r.findByPlayer(b.id())));
        assertEquals(proposal,database.use(r->r.get("proposals",b.id().toString())));
        if(reason.equals("minimum-online"))verify(messages).send(bob,reason,"minutes",30L);
        else verify(messages).send(bob,reason);
    }
    @Test void dailyLimitCannotBeResetByRejectingAndResending(){
        for(int i=0;i<3;i++){marriages.propose(a,b,"NORMAL");drain();marriages.deny(b);drain();}
        marriages.propose(a,b,"NORMAL");drain();
        assertNull(database.use(r->r.get("proposals",b.id().toString())));
        assertEquals(3L,database.<Long>use(r->MarriageService.number(r,"daily",marriages.today()+":"+a.id())));
        verify(messages).send(alice,"daily-limit");
    }
    @Test void duplicateAcceptQueuedBeforeCommitCreatesOneMarriage(){
        seed();marriages.accept(b);marriages.accept(b);drain();
        var married=database.use(r->r.findByPlayer(a.id()));assertNotNull(married);
        marriages.accept(b);drain();
        assertEquals(married.id(),database.use(r->r.findByPlayer(b.id()).id()));
        assertEquals(1,database.<Integer>use(r->r.findAll().size()));
        verify(messages).send(bob,"busy");verify(messages).send(bob,"request-missing");
    }
    @ParameterizedTest @ValueSource(strings={"accept","deny"})
    void expiredProposalCannotMarryOrReportSuccessfulRejection(String op){
        proposal=marriages.json().toJson(new Proposal(UUID.randomUUID(),a.id(),b.id(),"NORMAL",System.currentTimeMillis()-1));
        seed();invoke(op);drain();
        assertTrue(database.<Boolean>use(r->r.findAll().isEmpty()));
        verify(messages).send(bob,"request-missing");
        verify(messages,never()).send(bob,"proposal-denied");
    }
}
