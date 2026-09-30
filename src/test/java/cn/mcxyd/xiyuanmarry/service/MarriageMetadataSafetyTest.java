package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.repository.DatabaseManager;
import cn.mcxyd.xiyuanmarry.repository.MarriageRepository;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;
import com.google.gson.Gson;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MarriageMetadataSafetyTest {
    @Test void proposalMetadataMustBindTargetKeyAndKnownMode() throws Exception {
        var service=new MarriageService(mock(JavaPlugin.class),mock(ConfigurationManager.class),
                mock(MessageService.class),mock(DatabaseManager.class),mock(UnifiedScheduler.class),
                mock(IoDispatcher.class),mock(PlayerDirectory.class),true);
        Method read=MarriageService.class.getDeclaredMethod("readProposal",String.class,String.class);
        read.setAccessible(true);
        UUID proposer=UUID.randomUUID(), target=UUID.randomUUID();
        String valid=new Gson().toJson(new cn.mcxyd.xiyuanmarry.model.Proposal(
                UUID.randomUUID(),proposer,target,"WEDDING",System.currentTimeMillis()+60_000));
        assertEquals(null,read.invoke(service,UUID.randomUUID().toString(),valid));
        String unknown=new Gson().toJson(new cn.mcxyd.xiyuanmarry.model.Proposal(
                UUID.randomUUID(),proposer,target,"ADMIN",System.currentTimeMillis()+60_000));
        assertEquals(null,read.invoke(service,target.toString(),unknown));
        service.shutdown();
    }

    @Test void malformedNumericMetadataUsesConservativeNumber() {
        var repository=mock(MarriageRepository.class);
        when(repository.get("cooldowns","player")).thenReturn("not-a-number");
        assertEquals(Long.MAX_VALUE,MarriageService.number(repository,"cooldowns","player"));
    }

    @Test void maintenanceSkipsMalformedProposalAndCooldown() throws Exception {
        var repository=mock(MarriageRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        when(repository.entries("proposals")).thenReturn(Map.of("broken","{"));
        when(repository.entries("cooldowns")).thenReturn(Map.of("player","not-a-number"));
        when(repository.entries("daily")).thenReturn(Map.of());
        var yaml=new YamlConfiguration();
        yaml.set("timezone","Asia/Shanghai");
        var config=mock(ConfigurationManager.class); when(config.config()).thenReturn(yaml);
        var service=new MarriageService(mock(JavaPlugin.class),config,mock(MessageService.class),
                mock(DatabaseManager.class),mock(UnifiedScheduler.class),mock(IoDispatcher.class),mock(PlayerDirectory.class),true);
        Method maintain=MarriageService.class.getDeclaredMethod("maintain",MarriageRepository.class,long.class);
        maintain.setAccessible(true);
        assertDoesNotThrow(() -> maintain.invoke(service,repository,System.currentTimeMillis()));
        verify(repository,never()).remove("proposals","broken");
        verify(repository,never()).remove("cooldowns","player");
        service.shutdown();
    }
}
