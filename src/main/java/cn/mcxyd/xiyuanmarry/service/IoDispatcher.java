package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.scheduler.TaskHandle;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 有界串行事务队列；只把纯数据和 IO 闭包交给官方 AsyncScheduler。 */
public final class IoDispatcher {
    private static final int CAPACITY = 1024;
    private final Object lifecycle = new Object();
    private final Deque<Runnable> queue = new ArrayDeque<>();
    private final UnifiedScheduler scheduler;
    private final Logger logger;
    // 以下状态与队列只在 lifecycle 短锁内访问，不依赖并发集合实现复合操作。
    private boolean closed, draining;
    private TaskHandle worker;

    public IoDispatcher(UnifiedScheduler scheduler) { this(scheduler, Logger.getLogger(IoDispatcher.class.getName())); }
    public IoDispatcher(UnifiedScheduler scheduler, Logger logger) {
        this.scheduler = Objects.requireNonNull(scheduler); this.logger = Objects.requireNonNull(logger);
    }

    public boolean submit(Runnable task) {
        Objects.requireNonNull(task);
        RuntimeException rejected;
        synchronized (lifecycle) {
            if (closed || queue.size() >= CAPACITY) return false;
            queue.addLast(task);
            if (draining) return true;
            draining = true;
            try {
                // 锁内仅登记非阻塞的官方异步调度；工作线程拿到任务后在锁外执行 IO。
                worker = scheduler.runAsync(this::drain);
                return true;
            } catch (RuntimeException failure) {
                // 尚无其他提交能进入此临界区，撤销本次入队，避免拒绝的任务日后又执行。
                queue.removeLast(); draining = false; worker = null; rejected = failure;
            }
        }
        logger.log(Level.WARNING, "结婚系统 IO 调度被拒绝，本次请求未入队，可稍后重试", rejected);
        return false;
    }

    private void drain() {
        while (true) {
            Runnable task;
            synchronized (lifecycle) {
                if (closed || (task = queue.pollFirst()) == null) { draining = false; worker = null; return; }
            }
            try { task.run(); }
            catch (ThreadDeath | VirtualMachineError fatal) { close(); throw fatal; }
            catch (Throwable failure) {
                // 不重试失败任务，避免重复外部副作用；后续已接受任务仍按原顺序执行。
                logger.log(Level.SEVERE, "结婚系统 IO 任务异常，已隔离且不会自动重试，继续处理后续请求", failure);
            }
        }
    }

    public void close() {
        TaskHandle previous;
        synchronized (lifecycle) { closed = true; queue.clear(); previous = worker; worker = null; }
        // 不等待正在执行的 IO；活动数据库租约仍由 DatabaseManager 在结束时释放。
        if (previous != null) try { previous.cancel(); }
        catch (RuntimeException failure) { logger.log(Level.WARNING, "结婚系统 IO 工作任务取消失败", failure); }
    }
}

