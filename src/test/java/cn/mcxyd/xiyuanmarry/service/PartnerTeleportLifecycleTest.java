package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.repository.*;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartnerTeleportLifecycleTest {
    @TempDir Path root;
    private final class Fixture implements AutoCloseable {
        final MarriageService marriages=mock(MarriageService.class);
        final PlayerDirectory directory=mock(PlayerDirectory.class);
        final ConfigurationManager config=mock(ConfigurationManager.class);
        final DatabaseManager database=mock(DatabaseManager.class);
        final IoDispatcher io=mock(IoDispatcher.class);
        final UnifiedScheduler scheduler=mock(UnifiedScheduler.class);
        final TaskHandle timer=mock(TaskHandle.class);
        final UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        final Player first=mock(Player.class),second=mock(Player.class);
        final Queue<Runnable> work=new ArrayDeque<>(),entities=new ArrayDeque<>();
        final PlayerSnapshot actor,partner;
        final SqliteMarriageRepository repo;
        final PartnerTeleportService service;
        Runnable timeout;
        Fixture() {
            var point=new PlayerSnapshot.Point(UUID.randomUUID(),0,64,0,0,0);
            actor=new PlayerSnapshot(a,a,"name:alice","Alice",60,point,System.currentTimeMillis());
            partner=new PlayerSnapshot(b,b,"name:bob","Bob",60,point,System.currentTimeMillis());
            when(marriages.directory()).thenReturn(directory);when(directory.identity(b)).thenReturn(partner);
            when(directory.capture(first)).thenReturn(actor);when(directory.capture(second)).thenReturn(partner);
            when(config.snapshot()).thenReturn(snapshot());
            var settings=new YamlConfiguration();settings.set("privileges.partner-teleport-cooldown-seconds",600);
            when(config.config()).thenReturn(settings);
            when(io.submit(any())).thenAnswer(c->{work.add(c.getArgument(0));return true;});
            when(scheduler.runAsyncLater(any(),anyLong(),any())).thenAnswer(c->{timeout=c.getArgument(0);return timer;});
            doAnswer(c->{Consumer<Player> callback=c.getArgument(1);UUID id=c.getArgument(0);
                entities.add(()->callback.accept(id.equals(a)?first:second));return null;})
                .when(scheduler).player(any(),any(),any());
            repo=new SqliteMarriageRepository(root.resolve(UUID.randomUUID()+".db"));
            assertTrue(repo.createMarriage(a,b,"NORMAL",1));var relation=repo.findByPlayer(a);
            when(marriages.view()).thenReturn(new MarriageService.View(Map.of(a,relation,b,relation),Map.of(),List.of(relation),Map.of()));
            when(database.use(any())).thenAnswer(c->c.<Function<MarriageRepository,Object>>getArgument(0).apply(repo));
            service=new PartnerTeleportService(marriages,config,database,io,scheduler,Logger.getAnonymousLogger());
        }
        void drainIo(){for(int n=0;n<20&&!work.isEmpty();n++)work.remove().run();assertTrue(work.isEmpty());}
        void noCooldown(){assertNull(repo.get("teleport-cooldowns",a.toString()));assertNull(repo.get("teleport-tokens",a.toString()));}
        @Override public void close(){service.close();repo.close();}
    }
    private ConfigurationManager.Snapshot snapshot(){return new ConfigurationManager.Snapshot(UUID.randomUUID(),Map.of(),Map.of(),null,null);}

    @Test void rejectedTimeoutReleasesBusyStateAndAllowsRetry(){
        try(var f=new Fixture()){
            when(f.scheduler.runAsyncLater(any(),anyLong(),any()))
                .thenThrow(new RejectedExecutionException("test rejection")).thenReturn(f.timer);
            assertDoesNotThrow(()->f.service.teleport(f.actor));
            verify(f.io,never()).submit(any());
            verify(f.marriages).notifyLive(f.a,"partner-teleport-failed");
            f.service.teleport(f.actor);
            verify(f.io).submit(any());
            verify(f.marriages,never()).notifyLive(f.a,"busy");
        }
    }
    @Test void timeoutBeforeHandleBindingCancelsLateHandleWithoutSubmittingIo(){
        try(var f=new Fixture()){
            when(f.scheduler.runAsyncLater(any(),anyLong(),any())).thenAnswer(c->{c.<Runnable>getArgument(0).run();return f.timer;});
            f.service.teleport(f.actor);
            verify(f.timer).cancel();verify(f.io,never()).submit(any());
        }
    }
    @Test void closeBeforeHandleBindingCancelsLateHandleWithoutSubmittingIo(){
        try(var f=new Fixture()){
            when(f.scheduler.runAsyncLater(any(),anyLong(),any())).thenAnswer(c->{f.service.close();return f.timer;});
            f.service.teleport(f.actor);
            verify(f.timer).cancel();verify(f.io,never()).submit(any());
            verify(f.marriages,never()).notifyLive(any(),anyString());
        }
    }
    @Test void rejectedActorSchedulingReleasesReservationImmediately(){
        try(var f=new Fixture()){
            doThrow(new RejectedExecutionException("actor retired")).when(f.scheduler).player(eq(f.a),any(),any());
            f.service.teleport(f.actor);f.drainIo();
            assertNotNull(f.repo.get("teleport-cooldowns",f.a.toString()));
            assertDoesNotThrow(()->f.entities.remove().run());f.drainIo();
            f.noCooldown();verify(f.marriages).notifyLive(f.a,"partner-teleport-failed");
        }
    }
    @Test void brokenTimerCancellationDoesNotSkipCompensationOrFeedback(){
        try(var f=new Fixture()){
            doThrow(new IllegalStateException("cancel failed")).when(f.timer).cancel();
            when(f.directory.capture(f.second)).thenReturn(null);
            f.service.teleport(f.actor);f.drainIo();
            assertDoesNotThrow(()->f.entities.remove().run());f.drainIo();
            f.noCooldown();verify(f.marriages).notifyLive(f.a,"partner-teleport-failed");
        }
    }
    @Test void expiredGenerationTimeoutDoesNotNotifyNewSession(){
        try(var f=new Fixture()){
            f.service.teleport(f.actor);when(f.config.snapshot()).thenReturn(snapshot());
            f.timeout.run();
            verify(f.marriages,never()).notifyLive(any(),anyString());
        }
    }
    @Test void missingActorSnapshotDoesNotThrowOrStartWork(){
        try(var f=new Fixture()){
            assertDoesNotThrow(()->f.service.teleport(null));
            verifyNoInteractions(f.io,f.scheduler);
        }
    }
    @Test void dispatchedTimeoutRetainsCooldownAndIgnoresLateFailure(){
        try(var f=new Fixture()){
            var result=new CompletableFuture<Boolean>();
            when(f.scheduler.teleportAsync(any(),any(UUID.class),anyDouble(),anyDouble(),anyDouble(),anyFloat(),anyFloat())).thenReturn(result);
            f.service.teleport(f.actor);f.drainIo();f.entities.remove().run();f.entities.remove().run();
            var token=f.repo.get("teleport-tokens",f.a.toString());assertNotNull(token);
            f.timeout.run();result.complete(false);f.drainIo();
            assertEquals(token,f.repo.get("teleport-tokens",f.a.toString()));
            verify(f.marriages).notifyLive(f.a,"partner-teleport-timeout");
            verify(f.marriages,never()).notifyLive(f.a,"partner-teleport-failed");
        }
    }
}
