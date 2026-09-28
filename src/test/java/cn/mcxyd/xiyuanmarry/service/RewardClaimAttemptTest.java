package cn.mcxyd.xiyuanmarry.service;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static cn.mcxyd.xiyuanmarry.service.RewardClaimAttempt.Outcome.*;

class RewardClaimAttemptTest {
    @Test void cancellationBeforeEffectsAllowsRetry() {
        var a = new RewardClaimAttempt(); assertTrue(a.finish(RETRY));
        assertEquals(RETRY, a.outcome()); assertFalse(a.startEffects());
    }
    @Test void timeoutAfterEffectsCannotReopenTicket() {
        var a = new RewardClaimAttempt(); assertTrue(a.startEffects());
        assertTrue(a.finish(RETRY)); assertEquals(REVIEW, a.outcome()); assertFalse(a.finish(CONSUMED));
    }
    @Test void completionAndStartAreOnceOnly() {
        var a = new RewardClaimAttempt(); assertTrue(a.startEffects()); assertFalse(a.startEffects());
        assertTrue(a.finish(CONSUMED)); assertFalse(a.finish(REVIEW)); assertFalse(a.active());
    }
    @Test void racingClicksOnlyStartOneDelivery() throws Exception {
        var a = new RewardClaimAttempt(); var count = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 16; i++) tasks.add(executor.submit(() -> { if (a.startEffects()) count.incrementAndGet(); }));
            for (var task : tasks) task.get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, count.get());
    }
}
