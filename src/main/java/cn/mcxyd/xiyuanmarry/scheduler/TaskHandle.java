package cn.mcxyd.xiyuanmarry.scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.atomic.AtomicBoolean;
/** 先注册后绑定，避免极短任务完成早于句柄入表造成泄漏。 */
public final class TaskHandle {
 private final AtomicBoolean finished=new AtomicBoolean();
 private final Runnable cleanup;
 private volatile ScheduledTask task;
 TaskHandle(Runnable cleanup){this.cleanup=cleanup;}
 void bind(ScheduledTask value){task=value;if(value==null)finish();else if(finished.get())value.cancel();}
 void finish(){if(finished.compareAndSet(false,true))cleanup.run();}
 boolean active(){return !finished.get();}
 public void cancel(){var t=task;try{if(t!=null)t.cancel();}finally{finish();}}
}

