package cn.mcxyd.xiyuanmarry.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseLazyInitTest {
    @TempDir Path root;

    @Test void concurrentInitializationCreatesOnlyOneRepository() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var duplicate = new CountDownLatch(1);
        var count = new AtomicInteger();
        var pool = mock(MarriageRepository.class);
        var db = new DatabaseManager(DatabaseSettings.sqlite(root.resolve("once.db")), false, settings -> {
            if (count.incrementAndGet() > 1) duplicate.countDown();
            entered.countDown();
            await(release);
            return pool;
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(db::initialize);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = executor.submit(db::initialize);
            assertFalse(duplicate.await(300, TimeUnit.MILLISECONDS), "重复初始化不应重复创建连接池");
            release.countDown();
            first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS);
            assertEquals(1, count.get());
        } finally { release.countDown(); executor.shutdown(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); db.close(); }
        verify(pool, times(1)).close();
    }

    @Test void rejectedCandidateClosesOutsideManagerMonitor() throws Exception {
        var closing = new CountDownLatch(1);
        var releaseClose = new CountDownLatch(1);
        var pool = mock(MarriageRepository.class);
        doAnswer(call -> { closing.countDown(); await(releaseClose); return null; }).when(pool).close();
        var reference = new AtomicReference<DatabaseManager>();
        var db = new DatabaseManager(DatabaseSettings.sqlite(root.resolve("retire.db")), false, settings -> {
            reference.get().close();
            return pool;
        });
        reference.set(db);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var initialization = executor.submit(db::initialize);
            assertTrue(closing.await(5, TimeUnit.SECONDS));
            var state = executor.submit(db::initialized);
            assertFalse(assertDoesNotThrow(() -> state.get(1, TimeUnit.SECONDS), "关闭候选池不能锁住就绪状态读取"));
            releaseClose.countDown();
            var failure = assertThrows(ExecutionException.class, () -> initialization.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
        } finally { releaseClose.countDown(); executor.shutdown(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); db.close(); }
        verify(pool, times(1)).close();
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }

    @Test void lazyDatabaseRejectsUseUntilInitializedThenWorks() {
        try (var database = new DatabaseManager(DatabaseSettings.sqlite(root.resolve("lazy.db")), false)) {
            assertThrows(IllegalStateException.class, () -> database.use(r -> r.findAll()));
            database.initialize();
            assertTrue(database.<Boolean>use(r -> r.findAll().isEmpty()));
        }
    }

    @Test void closingBeforeInitializationDoesNotCreateOrLeakPool() {
        var database = new DatabaseManager(DatabaseSettings.sqlite(root.resolve("never.db")), false);
        database.close();
        assertThrows(IllegalStateException.class, database::initialize);
        assertFalse(java.nio.file.Files.exists(root.resolve("never.db")));
    }

    @Test void closeDoesNotWaitForSlowCandidateConstruction() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var repository = mock(MarriageRepository.class);
        var database = new DatabaseManager(DatabaseSettings.sqlite(root.resolve("slow.db")), false, settings -> {
            started.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            return repository;
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var initialization = executor.submit(database::initialize);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var close = executor.submit(database::close);
            assertDoesNotThrow(() -> close.get(1, TimeUnit.SECONDS), "关闭不应等待数据库构造器");
            release.countDown();
            assertThrows(ExecutionException.class, () -> initialization.get(5, TimeUnit.SECONDS));
            verify(repository).close();
            assertThrows(IllegalStateException.class, database::generation);
        } finally {
            release.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void generationReadDoesNotWaitForSlowInitialization() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var repository = mock(MarriageRepository.class);
        var database = new DatabaseManager(DatabaseSettings.sqlite(root.resolve("slow-generation.db")), false, settings -> {
            started.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            return repository;
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var initialization = executor.submit(database::initialize);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var query = executor.submit(() -> assertThrows(IllegalStateException.class, database::generation));
            assertDoesNotThrow(() -> query.get(1, TimeUnit.SECONDS));
            release.countDown();
            initialization.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown(); executor.shutdown(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); database.close();
        }
    }
}
