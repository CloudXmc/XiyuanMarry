package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.PlayerProfile;
import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.repository.MarriageRepository;
import cn.mcxyd.xiyuanmarry.scheduler.TaskHandle;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 戒指开关按持久身份落库；退出重登或 reload 不得静默恢复默认开启。 */
class RingPreferenceTest {
    final UnifiedScheduler scheduler = mock(UnifiedScheduler.class);
    final PlayerDirectory directory = mock(PlayerDirectory.class);
    final MarriageService marriages = mock(MarriageService.class);
    final UUID live = UUID.randomUUID();
    final UUID generation = UUID.randomUUID();
    final PlayerSnapshot actor = new PlayerSnapshot(live, live, "name:guest", "Guest", 60, null, 1);
    final List<Function<MarriageRepository, Object>> submitted = new ArrayList<>();
    Runnable tick;
    RingService rings;

    @BeforeEach void setup() {
        var plugin = mock(JavaPlugin.class);
        when(plugin.namespace()).thenReturn("xiyuanmarry");
        when(directory.all()).thenReturn(List.of(actor));
        when(marriages.view()).thenReturn(new MarriageService.View(Map.of(),
                Map.of(live, new PlayerProfile(live, "name:guest", "Guest", live)), List.of(), Map.of()));
        when(scheduler.runRepeatingAsync(any(), anyLong(), anyLong(), any()))
                .thenAnswer(call -> { tick = call.getArgument(0); return mock(TaskHandle.class); });
        // 开关偏好走四参数 submit；记录工作闭包后手动执行，避免真实数据库。
        when(marriages.databaseGeneration()).thenReturn(generation);
        doAnswer(call -> { submitted.add(call.getArgument(2)); return null; })
                .when(marriages).submitAtGeneration(any(), any(), any(), any(), any());
        rings = new RingService(plugin, marriages, mock(ConfigurationManager.class), directory, scheduler, mock(MessageService.class));
    }

    @AfterEach void close() { rings.close(); }

    @Test void toggleWritesPreferenceUnderThePersistentIdentity() {
        assertFalse(rings.toggle(actor), "首次关闭应返回 false");
        var repository = mock(MarriageRepository.class);
        submitted.getLast().apply(repository);
        verify(repository).put(RingService.PREF_BUCKET, live.toString(), "off");
    }

    @Test void offlineIdentityKeepsItsToggleAcrossTicks() {
        rings.start();
        assertFalse(rings.toggle(actor));
        when(directory.all()).thenReturn(List.of());        // 玩家已退出：在线集合为空，但资料仍在
        tick.run();
        // 偏好若被 tick 按在线集合清除，会退回默认开启，第二次开关将变成 false。
        assertTrue(rings.toggle(actor), "退出后偏好必须保留，不能静默恢复默认开启");
    }

    @Test void firstTogglePersistsBeforeLoginProfileCommit() {
        when(marriages.view()).thenReturn(new MarriageService.View(Map.of(), Map.of(), List.of(), Map.of()));
        assertFalse(rings.toggle(actor), "首次关闭应返回 false");
        var repository = mock(MarriageRepository.class);
        submitted.getLast().apply(repository);
        verify(repository).put(RingService.PREF_BUCKET, live.toString(), "off");
    }

    @Test void preferenceWriteIsBoundToTheDatabaseGenerationAtToggleTime() {
        rings.toggle(actor);
        verify(marriages).submitAtGeneration(isNull(), eq(generation), any(), any(), any());
    }
}
