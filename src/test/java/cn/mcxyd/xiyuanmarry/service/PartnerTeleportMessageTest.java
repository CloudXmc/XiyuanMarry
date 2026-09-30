package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.*;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartnerTeleportMessageTest {
    @TempDir Path root;
    @Test void persistedCooldownRejectionCarriesRemainingTimeWithoutReplacingTicket(){
        UUID actor=UUID.randomUUID(),partner=UUID.randomUUID(),generation=UUID.randomUUID();
        var config=mock(ConfigurationManager.class);var settings=new YamlConfiguration();settings.set("privileges.partner-teleport-cooldown-seconds",600);
        when(config.config()).thenReturn(settings);
        when(config.snapshot()).thenReturn(new ConfigurationManager.Snapshot(generation,Map.of(),Map.of(),null,null));
        when(config.messages()).thenReturn(YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile()));
        var marriageService=mock(MarriageService.class);var directory=mock(PlayerDirectory.class);
        when(marriageService.directory()).thenReturn(directory);
        var a=new PlayerSnapshot(actor,actor,"name:alice","Alice",60,null,System.currentTimeMillis());
        var b=new PlayerSnapshot(partner,partner,"name:bob","Bob",60,null,System.currentTimeMillis());
        when(directory.identity(partner)).thenReturn(b);
        var database=mock(DatabaseManager.class);var io=mock(IoDispatcher.class);var scheduler=mock(UnifiedScheduler.class);
        var queued=new ArrayDeque<Runnable>();when(io.submit(any())).thenAnswer(call->{queued.add(call.getArgument(0));return true;});
        when(scheduler.runAsyncLater(any(),anyLong(),any())).thenReturn(mock(TaskHandle.class));
        try(var repository=new SqliteMarriageRepository(root.resolve("cooldown.db"));
            var service=new PartnerTeleportService(marriageService,config,database,io,scheduler,Logger.getAnonymousLogger())){
            repository.createMarriage(actor,partner,"NORMAL",1);
            var marriage=repository.findByPlayer(actor);
            when(marriageService.view()).thenReturn(new MarriageService.View(Map.of(actor,marriage,partner,marriage),Map.of(),List.of(marriage),Map.of()));
            when(database.use(any())).thenAnswer(call->call.<Function<MarriageRepository,Object>>getArgument(0).apply(repository));
            var ticket=new TeleportCooldownLedger().reserve(repository,actor,System.currentTimeMillis(),125000);
            service.teleport(a);queued.remove().run();
            var notifications=mockingDetails(marriageService).getInvocations().stream().filter(c->c.getMethod().getName().equals("notifyLive")).toList();
            assertEquals(1,notifications.size());var args=notifications.getFirst().getArguments();
            assertEquals("teleport-cooldown",args[1]);assertTrue(args.length>2,"冷却拒绝必须携带剩余时间参数");
            var rendered=new MessageService(config).format((String)args[1],Arrays.copyOfRange(args,2,args.length));
            String visible=PlainTextComponentSerializer.plainText().serialize(rendered);
            assertTrue(visible.contains("剩余"));assertFalse(visible.contains("{minutes}"));assertFalse(visible.contains("{seconds}"));
            assertEquals(ticket.token(),repository.get("teleport-tokens",actor.toString()));
            assertEquals(Long.toString(ticket.until()),repository.get("teleport-cooldowns",actor.toString()));
            verify(scheduler,never()).player(any(),any(),any());
        }
    }
}
