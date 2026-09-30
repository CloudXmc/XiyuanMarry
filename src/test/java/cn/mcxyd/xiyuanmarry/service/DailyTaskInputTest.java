package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DailyTaskInputTest {
    @Test void missingSnapshotsAndEventTypesDoNotReadMarriageOrEnqueueWork() {
        var marriages=mock(MarriageService.class); var scheduler=mock(UnifiedScheduler.class); var handle=mock(TaskHandle.class);
        when(scheduler.runRepeatingAsync(any(),anyLong(),anyLong(),any())).thenReturn(handle);
        try(var tasks=new DailyTaskService(marriages,mock(ConfigurationManager.class),scheduler)) {
            assertDoesNotThrow(()->tasks.record(null,"TRADE","*",1));
            var actor=new PlayerSnapshot(UUID.randomUUID(),UUID.randomUUID(),"test","Test",60,null,1);
            assertDoesNotThrow(()->tasks.record(actor,null,"*",1));
            verifyNoInteractions(marriages);
        }
        verify(handle).cancel();
    }
}
