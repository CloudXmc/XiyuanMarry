package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.repository.*;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReloadFlowTest {
    @TempDir Path root;
    final Queue<Runnable> queue = new ArrayDeque<>();
    final UnifiedScheduler scheduler = mock(UnifiedScheduler.class);
    final PlayerDirectory players = mock(PlayerDirectory.class);
    final MessageService messages = mock(MessageService.class);
    final Player player = mock(Player.class);
    final UUID actor = UUID.randomUUID();
    DatabaseManager database; ConfigurationManager config; IoDispatcher io; MarriageService service;
    ConfigurationManager.Snapshot previous;

    @BeforeEach void setup() {
        var plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        config = spy(new ConfigurationManager(plugin));
        previous = snapshot(DatabaseSettings.sqlite(root.resolve("original.db")));
        config.publish(previous); database = new DatabaseManager(previous.database());
        database.use(r -> {r.put("settings", "marker", "original"); return null;});
        when(scheduler.runAsync(any())).thenAnswer(call -> {queue.add(call.getArgument(0)); return mock(TaskHandle.class);});
        when(scheduler.runGlobal(any())).thenAnswer(call -> {queue.add(call.getArgument(0)); return mock(TaskHandle.class);});
        doAnswer(call -> {Consumer<Player> action = call.getArgument(1); queue.add(() -> action.accept(player)); return null;})
            .when(scheduler).player(eq(actor), any());
        io = new IoDispatcher(scheduler);
        service = new MarriageService(plugin, config, messages, database, scheduler, io, players);
        service.initialize();
    }
    ConfigurationManager.Snapshot snapshot(DatabaseSettings settings) {
        var yaml = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
        return new ConfigurationManager.Snapshot(UUID.randomUUID(), Map.of("config.yml", yaml), Map.of(), settings, null);
    }
    void drain() {for(int n = 0; n < 100 && !queue.isEmpty(); n++) queue.remove().run(); assertTrue(queue.isEmpty());}
    @AfterEach void close() {service.shutdown(); io.close(); database.close(); queue.clear();}

    @Test void unreadableCandidateKeepsOriginalGenerationAndView() throws Exception {
        var next = snapshot(DatabaseSettings.sqlite(root.resolve("corrupt.db")));
        try(var candidate = new DatabaseManager(next.database())) {
            candidate.use(r -> {r.put("profiles", "broken", "not a profile"); return null;});
        }
        doReturn(next).when(config).prepare();
        var generation = database.generation(); var view = service.view();
        service.reload(actor); drain();
        assertEquals(generation, database.generation(), "失败候选库不得短暂成为当前库或使旧请求失效");
        assertSame(previous, config.snapshot()); assertSame(view, service.view());
        assertEquals("original", database.use(r -> r.get("settings", "marker")));
        verify(players, never()).refresh(); verify(messages).send(player, "reload-failed");
    }
    @Test void successfulSwitchPublishesAnEmptyNewDatabase() throws Exception {
        var next = snapshot(DatabaseSettings.sqlite(root.resolve("new.db")));
        doReturn(next).when(config).prepare();
        service.reload(actor); drain();
        assertSame(next, config.snapshot()); assertTrue(service.view().metadata().isEmpty());
        assertNull(database.use(r -> r.get("settings", "marker")));
        verify(players).refresh(); verify(messages).send(player, "database-switched");
        try(var original = new DatabaseManager(previous.database())) {
            assertEquals("original", original.use(r -> r.get("settings", "marker")));
        }
    }
    @Test void invalidConfigurationLeavesCurrentStateUntouched() throws Exception {
        doThrow(new IllegalArgumentException("invalid")).when(config).prepare();
        var generation = database.generation(); var view = service.view();
        service.reload(actor); drain();
        assertSame(previous, config.snapshot()); assertSame(view, service.view());
        assertEquals(generation, database.generation()); verify(messages).send(player, "reload-failed");
    }
    @Test void postPublicationFailureReportsRefreshFailureWithoutFalseRollback() throws Exception {
        var next = snapshot(DatabaseSettings.sqlite(root.resolve("new.db")));
        doReturn(next).when(config).prepare();
        service.onReload(() -> {throw new IllegalStateException("callback");});
        service.reload(actor); drain();
        assertSame(next, config.snapshot()); assertEquals(next.database(), database.currentSettings());
        verify(messages).send(player, "reload-refresh-failed"); verify(messages, never()).send(player, "reload-failed");
    }
    @Test void queuedReloadCannotPublishAfterShutdown() throws Exception {
        var next = snapshot(DatabaseSettings.sqlite(root.resolve("new.db")));
        doReturn(next).when(config).prepare();
        service.reload(actor); service.shutdown(); drain();
        assertSame(previous, config.snapshot()); verify(config, never()).prepare();
    }
    @Test void consoleReceivesAsynchronousBusinessFailure() {
        var console = mock(ConsoleCommandSender.class);
        try(var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            service.admin(null, "divorce", UUID.randomUUID(), null, 0); drain();
            verify(messages).send(console, "not-married");
        }
    }
    @Test void consoleReceivesRejectedReloadFeedback() {
        var console = mock(ConsoleCommandSender.class);
        try(var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            io.close(); service.reload(null); drain();
            verify(messages).send(console, "busy");
        }
    }
}
