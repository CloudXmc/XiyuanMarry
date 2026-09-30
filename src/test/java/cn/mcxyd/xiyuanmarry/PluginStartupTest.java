package cn.mcxyd.xiyuanmarry;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.repository.*;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import cn.mcxyd.xiyuanmarry.service.*;
import cn.mcxyd.xiyuanmarry.gui.GuiFactory;
import cn.mcxyd.xiyuanmarry.listener.CoupleTaskListener;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 可控调度驱动真实 onEnable/onDisable，验证接线而非模拟服务器兼容结论。 */
class PluginStartupTest {
    @TempDir Path root;
    final List<AutoCloseable> scopes = new ArrayList<>();
    final Deque<Runnable> async = new ArrayDeque<>(), global = new ArrayDeque<>();
    XiyuanMarryPlugin plugin;
    PluginCommand playerCommand, adminCommand;
    CommandExecutor playerExecutor, adminExecutor;
    MockedConstruction<DatabaseManager> databases;
    MockedConstruction<MarriageService> marriages;
    MockedConstruction<MessageService> messages;
    MockedConstruction<UnifiedScheduler> schedulers;
    MockedConstruction<PlayerDirectory> directories;
    MockedConstruction<CoupleTaskListener> coupleListeners;

    @BeforeEach void setup() throws Exception {
        plugin = mock(XiyuanMarryPlugin.class, CALLS_REAL_METHODS);
        // Mockito 跳过构造器；仅测试夹具补齐生命周期锁。生产代码没有反射注入。
        var lock = XiyuanMarryPlugin.class.getDeclaredField("lifecycle");
        lock.setAccessible(true); lock.set(plugin, new Object());
        doReturn(root.toFile()).when(plugin).getDataFolder();
        doReturn(Logger.getLogger("startup-test")).when(plugin).getLogger();
        doReturn(new PluginDescriptionFile("XiyuanMarry", "2.10.10", XiyuanMarryPlugin.class.getName())).when(plugin).getDescription();
        doReturn(mock(Server.class, RETURNS_DEEP_STUBS)).when(plugin).getServer();
        doNothing().when(plugin).saveDefaultConfig();
        doNothing().when(plugin).saveResource(anyString(), anyBoolean());
        playerCommand = mock(PluginCommand.class); adminCommand = mock(PluginCommand.class);
        doReturn(playerCommand).when(plugin).getCommand("marry");
        doReturn(adminCommand).when(plugin).getCommand("marryadmin");
        doAnswer(c -> { playerExecutor = c.getArgument(0); return null; }).when(playerCommand).setExecutor(any());
        doAnswer(c -> { adminExecutor = c.getArgument(0); return null; }).when(adminCommand).setExecutor(any());
        var snapshot = new ConfigurationManager.Snapshot(UUID.randomUUID(), Map.of(), Map.of(),
                DatabaseSettings.sqlite(root.resolve("test.db")), null);
        scopes.add(mockConstruction(ConfigurationManager.class, (c, context) -> when(c.snapshot()).thenReturn(snapshot)));
        schedulers = mockConstruction(UnifiedScheduler.class, (s, context) -> {
            when(s.runAsync(any())).thenAnswer(c -> { async.add(c.getArgument(0)); return mock(TaskHandle.class); });
            when(s.runGlobal(any())).thenAnswer(c -> { global.add(c.getArgument(0)); return mock(TaskHandle.class); });
        });
        scopes.add(schedulers);
        databases = mockConstruction(DatabaseManager.class); scopes.add(databases);
        messages = mockConstruction(MessageService.class); scopes.add(messages);
        directories = mockConstruction(PlayerDirectory.class); scopes.add(directories);
        marriages = mockConstruction(MarriageService.class, (m, context) -> when(m.ready()).thenReturn(true));
        scopes.add(marriages);
    }

    @AfterEach void cleanup() throws Exception {
        if (plugin != null) plugin.onDisable();
        for (int i = scopes.size() - 1; i >= 0; i--) scopes.get(i).close();
    }

    @Test void loadingCommandsAndCompletionCannotReachBusinessBeforeDatabaseWork() {
        plugin.onEnable();
        var sender = mock(CommandSender.class);
        playerExecutor.onCommand(sender, playerCommand, "marry", new String[0]);
        adminExecutor.onCommand(sender, adminCommand, "marryadmin", new String[]{"reload"});
        verify(messages.constructed().getFirst(), times(2)).send(sender, "database-not-ready");
        verify(databases.constructed().getFirst(), never()).initialize();
        var completer = org.mockito.ArgumentCaptor.forClass(TabCompleter.class);
        verify(playerCommand).setTabCompleter(completer.capture());
        assertTrue(completer.getValue().onTabComplete(sender, playerCommand, "marry", new String[0]).isEmpty());
        assertTrue(global.isEmpty());
    }

    @Test void stopDuringDatabaseInitializationDoesNotCreateLateServiceOrActivation() {
        plugin.onEnable();
        doAnswer(c -> { plugin.onDisable(); return null; }).when(databases.constructed().getFirst()).initialize();
        async.removeFirst().run();
        assertTrue(global.isEmpty(), "关闭后不得再登记全局激活回调");
        for (var service : marriages.constructed()) {
            verify(service).shutdown();
            verify(service, never()).initializeAfterDatabase();
            verify(service, never()).initialize();
        }
    }

    @Test void failedSnapshotClosesDatabaseAndLeavesCommandsInFailedState() {
        plugin.onEnable();
        doThrow(new IllegalStateException("snapshot failed")).when(marriages.constructed().getFirst()).initializeAfterDatabase();
        async.removeFirst().run();
        var sender = mock(CommandSender.class);
        playerExecutor.onCommand(sender, playerCommand, "marry", new String[0]);
        verify(messages.constructed().getFirst()).send(sender, "startup-failed");
        verify(databases.constructed().getFirst()).close();
        verify(marriages.constructed().getFirst()).shutdown();
        assertTrue(global.isEmpty());
    }

    @Test void stoppingBeforeQueuedGlobalActivationMakesCallbackHarmless() {
        plugin.onEnable(); async.removeFirst().run();
        assertEquals(1, global.size());
        plugin.onDisable();
        assertDoesNotThrow(global.removeFirst()::run);
        verify(playerCommand, times(1)).setExecutor(any());
        verify(marriages.constructed().getFirst()).shutdown();
    }

    @Test void activationFailureClosesPartialServicesAndKeepsGateClosed() {
        var attributes = mockConstruction(BondAttributeService.class, (s, c) ->
                doThrow(new IllegalStateException("activation failed")).when(s).start());
        scopes.add(attributes);
        plugin.onEnable(); async.removeFirst().run();
        assertDoesNotThrow(global.removeFirst()::run);
        while (!async.isEmpty()) async.removeFirst().run();
        var sender = mock(CommandSender.class);
        playerExecutor.onCommand(sender, playerCommand, "marry", new String[0]);
        verify(messages.constructed().getFirst()).send(sender, "startup-failed");
        verify(attributes.constructed().getFirst()).close();
        verify(marriages.constructed().getFirst()).shutdown();
        verify(databases.constructed().getFirst()).close();
    }

    @Test void rejectedGlobalActivationClosesPreparedService() {
        plugin.onEnable();
        doThrow(new IllegalStateException("rejected")).when(schedulers.constructed().getFirst()).runGlobal(any());
        async.removeFirst().run();
        verify(databases.constructed().getFirst()).close();
        verify(marriages.constructed().getFirst()).shutdown();
        verify(marriages.constructed().getFirst(), never()).initialize();
    }

    @Test void rejectedInitialIoClosesUnstartedServicesAndShowsFailure() {
        schedulers.close(); scopes.remove(schedulers);
        schedulers = mockConstruction(UnifiedScheduler.class, (s, c) ->
                doThrow(new IllegalStateException("async rejected")).when(s).runAsync(any()));
        scopes.add(schedulers);
        plugin.onEnable();
        var sender = mock(CommandSender.class);
        playerExecutor.onCommand(sender, playerCommand, "marry", new String[0]);
        verify(messages.constructed().getFirst()).send(sender, "startup-failed");
        verify(databases.constructed().getFirst(), never()).initialize();
        verify(databases.constructed().getFirst()).close();
        verify(marriages.constructed().getFirst()).shutdown();
    }

    @Test void successfulActivationOpensBothMarriageModesAndAdminCommandOnce() {
        var guis = mockBusinessModules();
        plugin.onEnable();
        var marriage = marriages.constructed().getFirst();
        verify(marriage, never()).initialize();
        async.removeFirst().run();
        verify(marriage, never()).initialize();
        var activation = global.removeFirst();
        activation.run();
        var player = mock(Player.class); when(player.hasPermission(anyString())).thenReturn(true);
        playerExecutor.onCommand(player, playerCommand, "marry", new String[]{"normal"});
        playerExecutor.onCommand(player, playerCommand, "marry", new String[]{"wedding"});
        verify(guis.constructed().getFirst()).open(player, "propose", "NORMAL", 0);
        verify(guis.constructed().getFirst()).open(player, "propose", "WEDDING", 0);
        var admin = mock(CommandSender.class); when(admin.hasPermission(anyString())).thenReturn(true);
        adminExecutor.onCommand(admin, adminCommand, "marryadmin", new String[]{"reload"});
        verify(marriage).reload(null);
        activation.run();
        verify(marriage, times(1)).initialize();
        verify(directories.constructed().getFirst(), times(1)).bootstrap();
        assertEquals(1, guis.constructed().size());
        verify(playerCommand, times(1)).setExecutor(any());
        var completer = org.mockito.ArgumentCaptor.forClass(TabCompleter.class);
        verify(playerCommand).setTabCompleter(completer.capture());
        assertTrue(completer.getValue().onTabComplete(player, playerCommand, "marry", new String[]{"norm"}).contains("normal"));
    }

    @Test void lateActivationFailureDoesNotExposeAlreadyWiredCommands() {
        var guis = mockBusinessModules();
        plugin.onEnable();
        doThrow(new IllegalStateException("bootstrap failed")).when(directories.constructed().getFirst()).bootstrap();
        async.removeFirst().run(); global.removeFirst().run();
        while (!async.isEmpty()) async.removeFirst().run();
        var player = mock(Player.class); when(player.hasPermission(anyString())).thenReturn(true);
        playerExecutor.onCommand(player, playerCommand, "marry", new String[]{"normal"});
        verify(messages.constructed().getFirst()).send(player, "startup-failed");
        verify(guis.constructed().getFirst(), never()).open(any(), anyString(), anyString(), anyInt());
        verify(guis.constructed().getFirst()).close();
        verify(marriages.constructed().getFirst()).shutdown();
        verify(databases.constructed().getFirst()).close();
    }

    private MockedConstruction<GuiFactory> mockBusinessModules() {
        scopes.add(mockConstruction(BondAttributeService.class));
        scopes.add(mockConstruction(WeddingService.class));
        scopes.add(mockConstruction(DailyTaskService.class));
        scopes.add(mockConstruction(SharedOnlineService.class));
        coupleListeners=mockConstruction(CoupleTaskListener.class);scopes.add(coupleListeners);
        scopes.add(mockConstruction(GiftService.class));
        scopes.add(mockConstruction(RewardService.class));
        scopes.add(mockConstruction(ClaimInboxService.class));
        scopes.add(mockConstruction(PartnerTeleportService.class));
        scopes.add(mockConstruction(RingService.class));
        var guis = mockConstruction(GuiFactory.class); scopes.add(guis); return guis;
    }
    @Test void successfulActivationStartsTaskSamplingForAlreadyOnlinePlayers() {
        mockBusinessModules();plugin.onEnable();async.removeFirst().run();global.removeFirst().run();
        assertTrue(mockingDetails(coupleListeners.constructed().getFirst()).getInvocations().stream()
                .anyMatch(i->i.getMethod().getName().equals("bootstrap")),"激活时必须补接已在线玩家的探索采样");
    }
}

