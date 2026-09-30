package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.repository.DatabaseManager;
import cn.mcxyd.xiyuanmarry.repository.DatabaseSettings;
import cn.mcxyd.xiyuanmarry.repository.MarriageRepository;
import cn.mcxyd.xiyuanmarry.scheduler.TaskHandle;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarriageReadinessTest {
    @TempDir Path root;

    @Test void unreadyInitializationMustNotRegisterCallbacksOrTimers() {
        var database = mock(DatabaseManager.class);
        var scheduler = mock(UnifiedScheduler.class);
        var directory = mock(PlayerDirectory.class);
        var service = new MarriageService(mock(JavaPlugin.class), mock(ConfigurationManager.class),
                mock(MessageService.class), database, scheduler, mock(IoDispatcher.class), directory, false);
        service.initialize();
        verifyNoInteractions(scheduler, directory);
        service.shutdown();
    }

    @Test void databaseNotReadyRejectsBeforeIoQueue() {
        var plugin = mock(JavaPlugin.class);
        var config = mock(ConfigurationManager.class);
        var messages = mock(MessageService.class);
        var scheduler = mock(UnifiedScheduler.class);
        var directory = mock(PlayerDirectory.class);
        var database = new DatabaseManager(DatabaseSettings.sqlite(root.resolve("not-ready.db")), false);
        var io = mock(IoDispatcher.class);
        var service = new MarriageService(plugin, config, messages, database, scheduler, io, directory);
        var failure = new AtomicReference<Throwable>();
        try {
            service.submit(null, repository -> null, value -> fail("未就绪不能提交"), failure::set);
            assertNotNull(failure.get());
            verifyNoInteractions(io);
            verifyNoInteractions(scheduler);
        } finally {
            service.shutdown();
            database.close();
        }
    }

    @Test void shutdownBeforeInitialSnapshotLeavesServiceClosed() throws Exception {
        var plugin = mock(JavaPlugin.class);
        var config = mock(ConfigurationManager.class);
        var messages = mock(MessageService.class);
        var scheduler = mock(UnifiedScheduler.class);
        var directory = mock(PlayerDirectory.class);
        var database = new DatabaseManager(DatabaseSettings.sqlite(root.resolve("initial.db")), true);
        var io = mock(IoDispatcher.class);
        var service = new MarriageService(plugin, config, messages, database, scheduler, io, directory);
        service.shutdown();
        service.initializeAfterDatabase();
        assertFalse(service.ready());
        database.close();
    }

    @Test void deferredSnapshotRestoresExistingMarriagesBeforeReportingReady() {
        try (var db = new DatabaseManager(DatabaseSettings.sqlite(root.resolve("restore.db")), false)) {
            var scheduler = mock(UnifiedScheduler.class);
            var directory = mock(PlayerDirectory.class);
            var service = new MarriageService(mock(JavaPlugin.class), mock(ConfigurationManager.class),
                    mock(MessageService.class), db, scheduler, mock(IoDispatcher.class), directory);
            db.initialize();
            UUID one = UUID.randomUUID(), two = UUID.randomUUID();
            db.use(r -> r.createMarriage(one, two, "NORMAL", 1));
            assertFalse(service.ready());
            service.initializeAfterDatabase();
            assertTrue(service.ready());
            assertEquals(two, service.view().byPlayer().get(one).partnerOf(one));
            assertEquals(1, service.view().couples().size());
            verifyNoInteractions(scheduler, directory);
            service.shutdown();
        }
    }

    @Test void initializeRegistersOneTimerAndShutdownCancelsItOnce() {
        var db = mock(DatabaseManager.class); when(db.initialized()).thenReturn(true);
        var scheduler = mock(UnifiedScheduler.class);
        var handle = mock(TaskHandle.class);
        when(scheduler.runRepeatingAsync(any(), anyLong(), anyLong(), any())).thenReturn(handle);
        var directory = mock(PlayerDirectory.class);
        var service = new MarriageService(mock(JavaPlugin.class), mock(ConfigurationManager.class),
                mock(MessageService.class), db, scheduler, mock(IoDispatcher.class), directory);
        service.initialize(); service.initialize(); service.shutdown(); service.initialize();
        verify(directory, times(1)).onJoin(any());
        verify(scheduler, times(1)).runRepeatingAsync(any(), eq(1L), eq(15L), eq(TimeUnit.SECONDS));
        verify(handle, times(1)).cancel();
        assertFalse(service.ready());
    }

    @Test void corruptInitialSnapshotNeverReportsReadyOrStartsTimers() {
        try (var db = new DatabaseManager(DatabaseSettings.sqlite(root.resolve("corrupt.db")), false)) {
            var scheduler = mock(UnifiedScheduler.class);
            var directory = mock(PlayerDirectory.class);
            var service = new MarriageService(mock(JavaPlugin.class), mock(ConfigurationManager.class),
                    mock(MessageService.class), db, scheduler, mock(IoDispatcher.class), directory);
            db.initialize(); db.use(r -> { r.put("profiles", "bad", "["); return null; });
            assertThrows(RuntimeException.class, service::initializeAfterDatabase);
            assertFalse(service.ready());
            assertTrue(service.view().profiles().isEmpty());
            service.initialize(); verifyNoInteractions(scheduler, directory);
            service.shutdown();
        }
    }

    @Test void shutdownWhileSnapshotIsLoadingRejectsLatePublication() throws Exception {
        var reading = new CountDownLatch(1); var release = new CountDownLatch(1);
        var repository = mock(MarriageRepository.class);
        when(repository.findAll()).thenAnswer(c -> {
            reading.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return List.of();
        });
        when(repository.entries("settings")).thenReturn(Map.of("marker", "late"));
        var database = mock(DatabaseManager.class);
        when(database.use(any(Function.class))).thenAnswer(c -> c.<Function<MarriageRepository, ?>>getArgument(0).apply(repository));
        var service = new MarriageService(mock(JavaPlugin.class), mock(ConfigurationManager.class),
                mock(MessageService.class), database, mock(UnifiedScheduler.class), mock(IoDispatcher.class), mock(PlayerDirectory.class));
        var executor = Executors.newSingleThreadExecutor();
        try {
            var loading = executor.submit(service::initializeAfterDatabase);
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            service.shutdown(); release.countDown(); loading.get(5, TimeUnit.SECONDS);
            assertFalse(service.ready());
            assertTrue(service.view().metadata().isEmpty());
        } finally { release.countDown(); executor.shutdown(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); service.shutdown(); }
    }
}
