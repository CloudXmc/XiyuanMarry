package cn.mcxyd.xiyuanmarry.scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class TaskHandleTest {
    static final class FakeTask implements ScheduledTask {
        int cancellations; boolean throwsOnCancel;
        public Plugin getOwningPlugin(){return null;}
        public boolean isRepeatingTask(){return false;}
        public CancelledState cancel(){cancellations++;if(throwsOnCancel)throw new IllegalStateException("core cancellation failure");return CancelledState.CANCELLED_BY_CALLER;}
        public ExecutionState getExecutionState(){return ExecutionState.IDLE;}
    }
    @Test void cancelledBeforeBindingCancelsLateCoreTask() {
        var cleaned=new AtomicInteger();var handle=new TaskHandle(cleaned::incrementAndGet);var core=new FakeTask();
        handle.cancel();handle.bind(core);assertEquals(1,core.cancellations);assertEquals(1,cleaned.get());assertFalse(handle.active());
    }
    @Test void cleanupRunsOnlyOnceAfterCompletionAndCancellation() {
        var cleaned=new AtomicInteger();var handle=new TaskHandle(cleaned::incrementAndGet);
        handle.finish();handle.finish();handle.cancel();assertEquals(1,cleaned.get());
    }
    @Test void cancellationExceptionStillReleasesRegistryEntry() {
        var cleaned=new AtomicInteger();var handle=new TaskHandle(cleaned::incrementAndGet);var core=new FakeTask();core.throwsOnCancel=true;handle.bind(core);
        assertThrows(IllegalStateException.class,handle::cancel);assertFalse(handle.active());assertEquals(1,cleaned.get());
    }
}
