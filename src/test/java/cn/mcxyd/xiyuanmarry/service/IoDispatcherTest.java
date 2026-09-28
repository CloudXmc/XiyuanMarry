package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.scheduler.TaskHandle;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IoDispatcherTest {
    final UnifiedScheduler scheduler = mock(UnifiedScheduler.class);
    final Queue<Runnable> workers = new ArrayDeque<>();
    IoDispatcher io;

    @BeforeEach void setup() {
        when(scheduler.runAsync(any())).thenAnswer(call -> { workers.add(call.getArgument(0)); return mock(TaskHandle.class); });
        io = new IoDispatcher(scheduler);
    }
    @Test void rejectedSchedulingReturnsFalseAndNeverReplaysRejectedTask() {
        var rejectedRuns = new AtomicInteger();
        doThrow(new IllegalStateException("test scheduler rejection")).when(scheduler).runAsync(any());
        assertFalse(assertDoesNotThrow(() -> io.submit(rejectedRuns::incrementAndGet)));
        doAnswer(call -> { workers.add(call.getArgument(0)); return mock(TaskHandle.class); }).when(scheduler).runAsync(any());
        var nextRuns = new AtomicInteger();
        assertTrue(io.submit(nextRuns::incrementAndGet)); assertEquals(1, workers.size());
        workers.remove().run(); assertEquals(0, rejectedRuns.get()); assertEquals(1, nextRuns.get());
    }
    @Test void failedTaskDoesNotAbortOtherAcceptedWork() {
        var events = new ArrayList<String>();
        assertTrue(io.submit(() -> { throw new IllegalStateException("test isolated failure"); }));
        assertTrue(io.submit(() -> events.add("next")));
        assertDoesNotThrow(() -> workers.remove().run());
        assertEquals(List.of("next"), events); assertTrue(workers.isEmpty());
    }
    @Test void boundedQueueRejectsOverflowAndCanBeReusedAfterDrain() {
        var calls = new AtomicInteger();
        var rejected = new AtomicInteger();
        for (int i = 0; i < 1024; i++) assertTrue(io.submit(calls::incrementAndGet));
        assertFalse(io.submit(rejected::incrementAndGet));
        assertEquals(1, workers.size()); workers.remove().run(); assertEquals(1024, calls.get());
        assertTrue(io.submit(calls::incrementAndGet)); workers.remove().run(); assertEquals(1025, calls.get());
        assertEquals(0, rejected.get());
    }
    @Test void closeDropsQueuedWorkAndRejectsNewRequests() {
        var calls = new AtomicInteger();
        assertTrue(io.submit(calls::incrementAndGet));
        io.close(); io.close(); workers.remove().run();
        assertFalse(io.submit(calls::incrementAndGet)); assertEquals(0, calls.get());
    }
    @Test void runningTaskCanEnqueueFollowupWithoutAnotherWorker() {
        var order = new ArrayList<Integer>();
        assertTrue(io.submit(() -> { order.add(1); assertTrue(io.submit(() -> order.add(3))); }));
        assertTrue(io.submit(() -> order.add(2))); workers.remove().run();
        assertEquals(List.of(1, 2, 3), order); assertTrue(workers.isEmpty());
    }
    @Test void concurrentSubmittersShareOneSerialWorker() throws Exception {
        var calls = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var jobs = new ArrayList<java.util.concurrent.Future<?>>();
            for (int n = 0; n < 4; n++) jobs.add(executor.submit(() -> {
                for (int i = 0; i < 100; i++) assertTrue(io.submit(calls::incrementAndGet));
            }));
            for (var job : jobs) job.get(3, TimeUnit.SECONDS);
        }
        assertEquals(1, workers.size()); workers.remove().run(); assertEquals(400, calls.get());
    }
    @Test void closeCancelsRegisteredWorkerExactlyOnce() {
        var handle = mock(TaskHandle.class);
        doReturn(handle).when(scheduler).runAsync(any());
        assertTrue(io.submit(() -> fail("cancelled task must not execute")));
        io.close(); io.close(); verify(handle, times(1)).cancel();
    }
    @Test void concurrentEnqueueAndCloseDoNotWaitForRunningIo() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (var executor = Executors.newSingleThreadExecutor()) {
            assertTrue(io.submit(() -> {
                started.countDown();
                try { assertTrue(release.await(3, TimeUnit.SECONDS)); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            }));
            var worker = executor.submit(workers.remove());
            try {
                assertTrue(started.await(2, TimeUnit.SECONDS));
                assertTrue(io.submit(calls::incrementAndGet)); io.close();
                assertFalse(io.submit(calls::incrementAndGet));
            } finally { release.countDown(); }
            worker.get(3, TimeUnit.SECONDS); assertEquals(0, calls.get());
        }
    }
}
