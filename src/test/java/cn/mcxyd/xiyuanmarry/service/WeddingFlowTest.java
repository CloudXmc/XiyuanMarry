package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.*;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 手动排列 Entity/Async/Global 回调；数据库使用实际 SQLite 事务。 */
class WeddingFlowTest {
    record Owner(UUID live, Consumer<Player> action, Runnable retired) {}
    record Timeout(Runnable action, TaskHandle handle) {}
    @TempDir Path root;
    final UnifiedScheduler scheduler = mock(UnifiedScheduler.class);
    final PlayerDirectory directory = mock(PlayerDirectory.class);
    final MessageService messages = mock(MessageService.class);
    final Queue<Runnable> ioTasks = new ArrayDeque<>();
    final Queue<Owner> owners = new ArrayDeque<>();
    final List<Timeout> timeouts = new ArrayList<>();
    final List<CompletableFuture<Boolean>> transfers = new ArrayList<>();
    final List<Double> transferX = new ArrayList<>();
    final Map<UUID,Player> livePlayers = new HashMap<>();
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    final PlayerSnapshot.Point point = new PlayerSnapshot.Point(UUID.randomUUID(), 10, 70, 10, 0, 0);
    ConfigurationManager config; DatabaseManager database; IoDispatcher io;
    MarriageService marriages; WeddingService weddings; PlayerSnapshot first, second;

    @BeforeEach void setup() {
        var plugin = mock(JavaPlugin.class); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        config = new ConfigurationManager(plugin);
        var files = new HashMap<String,YamlConfiguration>();
        for (var name : List.of("config.yml", "messages.yml"))
            files.put(name, YamlConfiguration.loadConfiguration(Path.of("src/main/resources",name).toFile()));
        var settings = DatabaseSettings.sqlite(root.resolve("weddings.db"));
        config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(),files,Map.of(),settings,null));
        database = new DatabaseManager(settings);
        when(scheduler.runAsync(any())).thenAnswer(c -> { ioTasks.add(c.getArgument(0)); return mock(TaskHandle.class); });
        when(scheduler.runGlobalLater(any(),anyLong())).thenAnswer(c -> {
            var t = new Timeout(c.getArgument(0),mock(TaskHandle.class)); timeouts.add(t); return t.handle();
        });
        doAnswer(c -> { owners.add(new Owner(c.getArgument(0),c.getArgument(1),null)); return null; }).when(scheduler).player(any(),any());
        doAnswer(c -> { owners.add(new Owner(c.getArgument(0),c.getArgument(1),c.getArgument(2))); return null; }).when(scheduler).player(any(),any(),any());
        when(scheduler.teleportAsync(any(),any(UUID.class),anyDouble(),anyDouble(),anyDouble(),anyFloat(),anyFloat()))
            .thenAnswer(c -> { var f = new CompletableFuture<Boolean>(); transfers.add(f); transferX.add(c.getArgument(2)); return f; });
        first = player(a,"Alice"); second = player(b,"Bob");
        when(directory.all()).thenReturn(List.of(first,second));
        io = new IoDispatcher(scheduler);
        marriages = new MarriageService(plugin,config,messages,database,scheduler,io,directory);
        weddings = new WeddingService(marriages,config,scheduler);
        seed(System.currentTimeMillis());
    }
    PlayerSnapshot player(UUID id,String name) {
        var p = mock(Player.class); when(p.getUniqueId()).thenReturn(id); when(p.isOnline()).thenReturn(true);
        var s = new PlayerSnapshot(id,id,"name:"+name,name,60,point,System.currentTimeMillis());
        livePlayers.put(id,p); when(directory.live(id)).thenReturn(s); when(directory.identity(id)).thenReturn(s);
        when(directory.session(id)).thenReturn(1L);
        when(directory.capture(p)).thenReturn(s); return s;
    }
    void seed(long created) {
        database.use(r -> {
            r.deleteMarriage(a); assertTrue(r.createEngagement(a,b,"WEDDING",created));
            var m = r.findByPlayer(a); var plan = WeddingPlan.empty();
            for (var role : List.of("location","nx","nl","ly")) plan = plan.point(role,point);
            r.put("weddings",m.id(),marriages.json().toJson(plan)); return null;
        });
        marriages.submit(null,r -> null,x -> {}); drainIo();
    }
    MarriageRecord record() { return database.use(r -> r.findByPlayer(a)); }
    void drainIo() { for (int i=0;i<100 && !ioTasks.isEmpty();i++) ioTasks.remove().run(); assertTrue(ioTasks.isEmpty()); }
    void run(Owner job) { var p=livePlayers.get(job.live()); if (p!=null && p.isOnline()) job.action().accept(p); else if(job.retired()!=null) job.retired().run(); }
    void drainOwners() { for(int i=0;i<100 && !owners.isEmpty();i++) run(owners.remove()); assertTrue(owners.isEmpty()); }
    int start() { int offset=transfers.size(); weddings.start(first); drainIo(); drainOwners(); return offset; }
    void arrive(int offset) { transfers.get(offset).complete(true); transfers.get(offset+1).complete(true); drainOwners(); }
    void ready() { arrive(start()); assertTrue(weddings.pending(a)); assertTrue(weddings.pending(b)); }
    void consent() { weddings.oath(first); weddings.oath(second); }
    @AfterEach void close() { if(weddings!=null)weddings.clear(); if(marriages!=null)marriages.shutdown(); if(io!=null)io.close(); if(database!=null)database.close(); }

    @Test void bothConsentsCompleteOnlyOnce() {
        ready(); consent(); weddings.oath(second); drainIo(); drainOwners();
        assertEquals(MarriageState.MARRIED,record().state()); assertFalse(weddings.pending(a));
        verify(timeouts.getFirst().handle()).cancel();
    }
    @Test void startReportsDatabaseNotReadyInsteadOfSilentlyIgnoringClick() {
        var unavailable = new MarriageService(mock(JavaPlugin.class),config,messages,database,scheduler,io,directory,false);
        var unavailableWedding = new WeddingService(unavailable,config,scheduler);
        unavailableWedding.start(first);
        drainOwners();
        verify(messages).send(livePlayers.get(a),"database-not-ready");
        unavailableWedding.close();
        unavailable.shutdown();
    }
    @Test void nullPointCannotCorruptWeddingPlan() {
        var invalid = new PlayerSnapshot(a,a,"name:Alice","Alice",60,null,System.currentTimeMillis());
        weddings.setPoint(invalid,"location");
        drainOwners();
        verify(messages).send(livePlayers.get(a),"wedding-location-required");
        assertEquals(point,database.use(r->weddings.plan(r,record().id()).points().get("location")));
    }
    @Test void queuedWeddingPointCannotApplyAfterPlayerReconnect() {
        var replacement = new PlayerSnapshot.Point(point.world(),99,70,99,0,0);
        weddings.setPoint(new PlayerSnapshot(a,a,"name:Alice","Alice",60,replacement,System.currentTimeMillis()),"location");
        when(directory.session(a)).thenReturn(2L);
        drainIo(); drainOwners();
        assertEquals(point,database.use(r->weddings.plan(r,record().id()).points().get("location")));
        verify(messages).send(livePlayers.get(a),"relationship-request-stale");
    }
    @Test void staleWeddingEntryPointsIgnoreNullSnapshots() {
        assertDoesNotThrow(() -> {
            weddings.respond(null,true);
            weddings.respond(null,"missing",false);
            weddings.oath(null);
            weddings.oathFromChat(null);
            weddings.leave(null);
        });
        assertFalse(weddings.pending(null));
        assertTrue(database.<Boolean>use(r -> weddings.pendingInvitations(r,null).isEmpty()));
    }
    @Test void oathAwayFromStationDoesNotCancelTheCeremony() {
        ready();
        var wrong=new PlayerSnapshot(a,a,"name:Alice","Alice",60,
                new PlayerSnapshot.Point(point.world(),100,70,100,0,0),System.currentTimeMillis());
        weddings.oath(wrong);
        drainOwners();
        verify(messages).send(livePlayers.get(a),"oath-position-required");
        weddings.oath(first); weddings.oath(second); drainIo(); drainOwners();
        assertEquals(MarriageState.MARRIED,record().state());
    }
    @Test void acceptingMiskeyedProposalCannotMarryOtherPlayers() {
        var third=player(UUID.randomUUID(),"Carol");
        String raw=marriages.json().toJson(new Proposal(UUID.randomUUID(),b,third.id(),"WEDDING",System.currentTimeMillis()+60_000));
        database.use(r->{r.deleteMarriage(a);r.put("proposals",a.toString(),raw);return null;});
        marriages.accept(first);drainIo();drainOwners();
        assertTrue(database.<Boolean>use(r->r.findAll().isEmpty()));
        assertEquals(raw,database.use(r->r.get("proposals",a.toString())));
        verify(messages).send(livePlayers.get(a),"request-missing");
    }
    @Test void acceptingUnknownProposalModeCannotCreateUnusableEngagement() {
        String raw=marriages.json().toJson(new Proposal(UUID.randomUUID(),b,a,"ADMIN",System.currentTimeMillis()+60_000));
        database.use(r->{r.deleteMarriage(a);r.put("proposals",a.toString(),raw);return null;});
        marriages.accept(first);drainIo();drainOwners();
        assertTrue(database.<Boolean>use(r->r.findAll().isEmpty()));
        assertEquals(raw,database.use(r->r.get("proposals",a.toString())));
        verify(messages).send(livePlayers.get(a),"request-missing");
    }
    @Test void validWeddingProposalOnlyEngagesUntilBothVows() {
        database.use(r->{r.deleteMarriage(a);return null;});
        marriages.propose(first,second,"WEDDING");drainIo();drainOwners();
        marriages.accept(second);drainIo();drainOwners();
        assertEquals(MarriageState.ENGAGED,record().state());
        assertEquals("WEDDING",record().type());assertEquals(0,record().marriedAt());
        assertNull(database.use(r->r.get("proposals",b.toString())));
    }
    @Test void validNormalProposalStillMarriesWithoutCeremony() {
        database.use(r->{r.deleteMarriage(a);return null;});
        marriages.propose(first,second,"NORMAL");drainIo();drainOwners();
        marriages.accept(second);drainIo();drainOwners();
        assertEquals(MarriageState.MARRIED,record().state());
        assertEquals("NORMAL",record().type());assertTrue(record().marriedAt()>0);
        assertNull(database.use(r->r.get("proposals",b.toString())));
        assertNull(database.use(r->r.get("weddings",record().id())));
        assertFalse(weddings.pending(a)); assertFalse(weddings.pending(b));
    }
    @Test void invalidProposalModeIsRejectedWithoutConsumingDailyLimit() {
        database.use(r->{r.deleteMarriage(a);return null;});
        marriages.propose(first,second,"INVALID");drainIo();drainOwners();
        assertNull(database.use(r->r.get("proposals",b.toString())));
        assertEquals(0,database.<Long>use(r->MarriageService.number(r,"daily",marriages.today()+":"+a)));
        verify(messages).send(livePlayers.get(a),"invalid-argument");
    }
    @Test void normalAcceptanceIsIdempotentAndSecondClickCannotCreateAnotherMarriage() {
        database.use(r->{r.deleteMarriage(a);return null;});
        marriages.propose(first,second,"NORMAL");drainIo();drainOwners();
        marriages.accept(second);drainIo();drainOwners();
        var created=record();
        marriages.accept(second);drainIo();drainOwners();
        assertEquals(created.id(),record().id());
        verify(messages).send(livePlayers.get(b),"request-missing");
    }
    @Test void ceremonyRejectsNullOrCrossWorldWeddingPointsBeforeTeleport() {
        var otherWorld=new PlayerSnapshot.Point(UUID.randomUUID(),10,70,10,0,0);
        String id=record().id();
        database.use(r->{var plan=WeddingPlan.empty();
            for(var role:List.of("location","nx","nl","ly"))plan=plan.point(role,point);
            plan=plan.point("nl",otherWorld);r.put("weddings",id,marriages.json().toJson(plan));return null;});
        weddings.start(first);drainIo();drainOwners();
        assertTrue(transfers.isEmpty());
    }
    @Test void acceptedGuestsReceiveStableSeatsByIdentityOrder() {
        UUID lower=UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higher=UUID.fromString("00000000-0000-0000-0000-000000000002");
        var lowerPoint=new PlayerSnapshot.Point(point.world(),101,70,10,0,0);
        var higherPoint=new PlayerSnapshot.Point(point.world(),102,70,10,0,0);
        var firstSeat=new PlayerSnapshot.Point(point.world(),201,70,10,0,0);
        var secondSeat=new PlayerSnapshot.Point(point.world(),202,70,10,0,0);
        player(lower,"Lower"); player(higher,"Higher");
        when(directory.identity(lower)).thenReturn(new PlayerSnapshot(lower,lower,"name:Lower","Lower",60,lowerPoint,System.currentTimeMillis()));
        when(directory.identity(higher)).thenReturn(new PlayerSnapshot(higher,higher,"name:Higher","Higher",60,higherPoint,System.currentTimeMillis()));
        String id=record().id();
        database.use(r->{
            var plan=WeddingPlan.empty();
            for(var role:List.of("location","nx","nl","ly"))plan=plan.point(role,point);
            plan=plan.point("1",firstSeat).point("2",secondSeat)
                    .invite(higher,new WeddingPlan.Invite(true,System.currentTimeMillis()+60_000))
                    .invite(lower,new WeddingPlan.Invite(true,System.currentTimeMillis()+60_000));
            r.put("weddings",id,marriages.json().toJson(plan)); return null;
        });
        start();
        assertEquals(List.of(10.0,10.0,201.0,202.0),transferX);
    }
    @Test void guestLeavingDoesNotAbortCeremony() {
        UUID guestId=UUID.randomUUID();
        var guestPoint=new PlayerSnapshot.Point(point.world(),101,70,10,0,0);
        player(guestId,"Guest");
        when(directory.identity(guestId)).thenReturn(new PlayerSnapshot(guestId,guestId,"name:Guest","Guest",60,guestPoint,System.currentTimeMillis()));
        String id=record().id();
        database.use(r->{var plan=WeddingPlan.empty();
            for(var role:List.of("location","nx","nl","ly"))plan=plan.point(role,point);
            plan=plan.point("1",guestPoint).invite(guestId,new WeddingPlan.Invite(true,System.currentTimeMillis()+60000));
            r.put("weddings",id,marriages.json().toJson(plan)); return null;});
        start();
        assertEquals(3,transfers.size());
        weddings.leave(guestId);
        transfers.get(0).complete(true); transfers.get(1).complete(true); transfers.get(2).complete(false);
        drainOwners();
        assertTrue(weddings.pending(a));
        assertTrue(weddings.pending(b));
    }
    @Test void malformedWeddingPlanFallsBackWithoutThrowing() {
        String id=record().id();
        database.use(r -> { r.put("weddings",id,"not-json"); return null; });
        assertDoesNotThrow(() -> database.use(r -> weddings.plan(r,id)));
    }
    @Test void missingParticipantStopsImmediately() {
        weddings.start(first); drainIo(); when(livePlayers.get(b).isOnline()).thenReturn(false); drainOwners();
        verify(timeouts.getFirst().handle()).cancel(); assertFalse(weddings.pending(a));
    }
    @Test void failedTeleportDoesNotWaitForAnotherFuture() {
        int n=start(); transfers.get(n).complete(false);
        verify(timeouts.getFirst().handle()).cancel(); assertFalse(weddings.pending(a));
    }
    @Test void lateOldSuccessCannotOpenNewVows() {
        int old=start(); weddings.leave(a); int next=start();
        transfers.get(old).complete(true); transfers.get(old+1).complete(true);
        assertFalse(weddings.pending(a)); assertFalse(weddings.pending(b));
        arrive(next); assertTrue(weddings.pending(a));
    }
    @Test void lateOldFailureCannotCancelNewReadyCeremony() {
        int old=start(); weddings.leave(a); arrive(start());
        transfers.get(old).complete(false); transfers.get(old+1).complete(true);
        assertTrue(weddings.pending(a)); verify(timeouts.getLast().handle(),never()).cancel();
    }
    @Test void oldTimeoutCannotStopRestartedCeremony() {
        start(); var old=timeouts.getFirst(); weddings.leave(a); arrive(start()); old.action().run();
        assertTrue(weddings.pending(a)); verify(timeouts.getLast().handle(),never()).cancel();
    }
    @Test void queuedOldOwnerCannotTeleportForNewCeremony() {
        weddings.start(first); drainIo(); var delayed=owners.remove(); owners.clear(); weddings.leave(a);
        weddings.start(first); drainIo(); run(delayed); assertTrue(transfers.isEmpty());
    }
    @Test void queuedOldChatCannotConsentToNewCeremony() {
        ready(); weddings.oathFromChat(a); var oldChat=owners.remove();
        weddings.leave(a); arrive(start()); run(oldChat); weddings.oath(second); drainIo();
        assertEquals(MarriageState.ENGAGED,record().state());
    }
    @Test void queuedCompletionCannotMarryNewEngagementOfSamePlayers() {
        ready(); consent();
        String previous=record().id();
        database.use(r -> { r.deleteMarriage(a); assertTrue(r.createEngagement(a,b,"WEDDING",System.currentTimeMillis())); return null; });
        drainIo(); assertNotEquals(previous,record().id()); assertEquals(MarriageState.ENGAGED,record().state());
    }
    @Test void queuedCompletionCannotWriteToNewDatabase() {
        ready(); consent(); database.switchTo(DatabaseSettings.sqlite(root.resolve("replacement.db")));
        database.use(r -> r.createEngagement(a,b,"WEDDING",System.currentTimeMillis()));
        drainIo(); assertEquals(MarriageState.ENGAGED,record().state());
    }
    @Test void reloadInvalidatesQueuedCompletion() {
        ready(); consent(); var s=config.snapshot();
        config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(),s.files(),s.menus(),s.database(),s.tasks()));
        weddings.clear(); drainIo(); assertEquals(MarriageState.ENGAGED,record().state());
    }
    @Test void clearInvalidatesQueuedStart() {
        weddings.start(first); weddings.clear(); drainIo(); drainOwners(); assertTrue(transfers.isEmpty());
    }
    @Test void rejectedTimeoutDoesNotLeaveStuckCeremony() {
        when(scheduler.runGlobalLater(any(),anyLong())).thenThrow(new IllegalStateException("timer refused"));
        start();
        doAnswer(c -> { var t=new Timeout(c.getArgument(0),mock(TaskHandle.class)); timeouts.add(t); return t.handle(); }).when(scheduler).runGlobalLater(any(),anyLong());
        start(); assertEquals(2,transfers.size());
    }
    @Test void rejectedTeleportStopsCeremony() {
        when(scheduler.teleportAsync(any(),any(UUID.class),anyDouble(),anyDouble(),anyDouble(),anyFloat(),anyFloat()))
            .thenThrow(new IllegalStateException("teleport refused"));
        weddings.start(first); drainIo(); assertDoesNotThrow(this::drainOwners); verify(timeouts.getFirst().handle()).cancel();
    }
    @Test void expiredEngagementCannotStart() {
        seed(System.currentTimeMillis()-49L*3600000); start(); assertTrue(transfers.isEmpty());
    }
    @Test void closeInvalidatesQueuedStartAndCannotBeReopenedByClear() {
        weddings.start(first); weddings.close(); weddings.clear(); drainIo(); drainOwners();
        start(); assertTrue(transfers.isEmpty()); assertTrue(timeouts.isEmpty());
    }
    @Test void closeCancelsCurrentTimerAndIgnoresLateTeleport() {
        int n=start(); weddings.close(); arrive(n);
        verify(timeouts.getFirst().handle()).cancel(); assertFalse(weddings.pending(a));
        assertEquals(MarriageState.ENGAGED,record().state());
    }
    @Test void timerReturnedAfterClearIsCancelled() {
        var timer=mock(TaskHandle.class);
        doAnswer(c -> { weddings.clear(); return timer; }).when(scheduler).runGlobalLater(any(),anyLong());
        start(); verify(timer).cancel(); assertTrue(transfers.isEmpty());
    }
    @Test void deadParticipantCannotEnterCeremony() {
        when(livePlayers.get(a).isDead()).thenReturn(true); start();
        verify(timeouts.getFirst().handle()).cancel(); assertFalse(weddings.pending(a));
        verify(scheduler,never()).teleportAsync(eq(livePlayers.get(a)),any(UUID.class),anyDouble(),anyDouble(),anyDouble(),anyFloat(),anyFloat());
    }
    @Test void changedIdentityCannotEnterCeremony() {
        when(directory.capture(livePlayers.get(a))).thenReturn(new PlayerSnapshot(a,UUID.randomUUID(),"changed","Alice",60,point,System.currentTimeMillis()));
        start(); verify(timeouts.getFirst().handle()).cancel(); assertFalse(weddings.pending(a));
        verify(scheduler,never()).teleportAsync(eq(livePlayers.get(a)),any(UUID.class),anyDouble(),anyDouble(),anyDouble(),anyFloat(),anyFloat());
    }
    @Test void unavailableSnapshotStopsTeleportWithoutException() {
        when(directory.capture(any(Player.class))).thenReturn(null);
        assertDoesNotThrow(this::start);
        verify(scheduler,never()).teleportAsync(any(),any(UUID.class),anyDouble(),anyDouble(),anyDouble(),anyFloat(),anyFloat());
        verify(timeouts.getFirst().handle()).cancel();
        assertEquals(MarriageState.ENGAGED,record().state());
    }
    @Test void unavailableSnapshotStopsQueuedChatWithoutCompletingMarriage() {
        ready(); weddings.oathFromChat(a);
        when(directory.capture(livePlayers.get(a))).thenReturn(null);
        assertDoesNotThrow(this::drainOwners); assertFalse(weddings.pending(a));
        verify(timeouts.getFirst().handle()).cancel();
        assertEquals(MarriageState.ENGAGED,record().state());
    }
    @Test void failedCompletionNotifiesBothSpouses() {
        ready(); consent(); var s=config.snapshot();
        config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(),s.files(),s.menus(),s.database(),s.tasks()));
        drainIo(); drainOwners();
        verify(messages).send(livePlayers.get(a),"wedding-completion-failed");
        verify(messages).send(livePlayers.get(b),"wedding-completion-failed");
        assertEquals(MarriageState.ENGAGED,record().state());
    }
    @Test void legacyCompletionApiRejectsDifferentPartner() {
        marriages.completeWedding(a,UUID.randomUUID()); drainIo();
        assertEquals(MarriageState.ENGAGED,record().state());
    }
}
