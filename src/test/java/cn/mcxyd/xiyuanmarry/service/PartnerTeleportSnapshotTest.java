package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.*;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartnerTeleportSnapshotTest {
    @TempDir Path root;
    @Test void missingPartnerSnapshotFailsWithoutTeleportAndReleasesCooldown() { verifyFailure(true); }
    @Test void missingActorSnapshotFailsWithoutTeleportAndReleasesCooldown() { verifyFailure(false); }
    void verifyFailure(boolean missingPartner) {
        var marriages=mock(MarriageService.class); var directory=mock(PlayerDirectory.class);
        var config=mock(ConfigurationManager.class); var database=mock(DatabaseManager.class);
        var scheduler=mock(UnifiedScheduler.class); var io=mock(IoDispatcher.class);
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),world=UUID.randomUUID();
        var point=new PlayerSnapshot.Point(world,0,64,0,0,0);
        var actor=new PlayerSnapshot(a,a,"name:a","A",60,point,System.currentTimeMillis());
        var partner=new PlayerSnapshot(b,b,"name:b","B",60,point,System.currentTimeMillis());
        var first=mock(Player.class); var second=mock(Player.class);
        when(marriages.directory()).thenReturn(directory); when(directory.identity(b)).thenReturn(partner);
        when(directory.capture(first)).thenReturn(missingPartner?actor:null);
        when(directory.capture(second)).thenReturn(missingPartner?null:partner);
        when(config.snapshot()).thenReturn(new ConfigurationManager.Snapshot(UUID.randomUUID(),Map.of(),Map.of(),null,null));
        var settings=new YamlConfiguration(); settings.set("privileges.partner-teleport-cooldown-seconds",600); when(config.config()).thenReturn(settings);
        var work=new ArrayDeque<Runnable>();
        when(io.submit(any())).thenAnswer(c->{work.add(c.getArgument(0));return true;});
        when(scheduler.runAsyncLater(any(),anyLong(),any())).thenReturn(mock(TaskHandle.class));
        doAnswer(c->{UUID live=c.getArgument(0);Consumer<Player> action=c.getArgument(1);work.add(()->action.accept(live.equals(a)?first:second));return null;})
                .when(scheduler).player(any(),any(),any());
        try(var repo=new SqliteMarriageRepository(root.resolve("cooldown.db"));
            var service=new PartnerTeleportService(marriages,config,database,io,scheduler,Logger.getAnonymousLogger())) {
            assertTrue(repo.createMarriage(a,b,"NORMAL",1)); var relationship=repo.findByPlayer(a);
            when(marriages.view()).thenReturn(new MarriageService.View(Map.of(a,relationship,b,relationship),Map.of(),List.of(relationship),Map.of()));
            when(database.use(any())).thenAnswer(c->c.<Function<MarriageRepository,Object>>getArgument(0).apply(repo));
            service.teleport(actor);
            assertDoesNotThrow(()->{for(int n=0;n<20&&!work.isEmpty();n++)work.remove().run();});
            assertTrue(work.isEmpty());
            verify(scheduler,never()).teleportAsync(any(),any(UUID.class),anyDouble(),anyDouble(),anyDouble(),anyFloat(),anyFloat());
            assertNull(repo.get("teleport-cooldowns",a.toString()));
            verify(marriages).notifyLive(a,"partner-teleport-failed");
        }
    }
}
