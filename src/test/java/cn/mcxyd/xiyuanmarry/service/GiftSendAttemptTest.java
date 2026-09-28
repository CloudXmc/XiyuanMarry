package cn.mcxyd.xiyuanmarry.service;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class GiftSendAttemptTest {
    GiftSendAttempt attempt() { return new GiftSendAttempt(UUID.randomUUID(),new byte[]{1},UUID.randomUUID()); }

    @Test void startAndCloseCannotBothWin() throws Exception {
        try(var workers=Executors.newFixedThreadPool(2)) {
            for(int i=0;i<100;i++) {
                var attempt=attempt(); var start=new CountDownLatch(1);
                var writer=workers.submit(()->{start.await();return attempt.begin();});
                var closer=workers.submit(()->{start.await();return attempt.returnOnce(false);});
                start.countDown();
                assertNotEquals(writer.get(2,TimeUnit.SECONDS),closer.get(2,TimeUnit.SECONDS));
            }
        }
    }
    @Test void snapshotCannotBeModifiedByCallers() {
        byte[] bytes={1}; var attempt=new GiftSendAttempt(UUID.randomUUID(),bytes,UUID.randomUUID());
        bytes[0]=9; attempt.item()[0]=8; assertArrayEquals(new byte[]{1},attempt.item());
    }
    @Test void inFlightWriteOnlyRefundsAfterConfirmedRollback() {
        var attempt=attempt(); assertTrue(attempt.begin()); assertFalse(attempt.returnOnce(false));
        assertTrue(attempt.returnOnce(true)); assertFalse(attempt.returnOnce(true));
        attempt.committed(); assertFalse(attempt.persisted()); assertFalse(attempt.begin());
    }
    @Test void committedAndFinishedAttemptsNeverRefund() {
        var attempt=attempt(); assertTrue(attempt.begin()); attempt.committed();
        assertFalse(attempt.returnOnce(true)); attempt.finish();
        assertFalse(attempt.returnOnce(true)); assertFalse(attempt.review());
    }
    @Test void reviewIsTerminalEvenForLateSuccess() {
        var attempt=attempt(); assertTrue(attempt.begin()); assertTrue(attempt.review());
        attempt.committed(); assertFalse(attempt.persisted()); assertFalse(attempt.returnOnce(true));
        assertFalse(attempt.review());
    }
}
