package cn.mcxyd.xiyuanmarry.scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
/** Paper 1.21.11 与同版本 Folia 共用官方调度 API；所有权切换至少一 tick。 */
public final class UnifiedScheduler implements AutoCloseable {
 private final Plugin plugin;private final Map<Long,TaskHandle> tasks=new ConcurrentHashMap<>();private final AtomicLong ids=new AtomicLong();private volatile boolean closed;
 public UnifiedScheduler(Plugin plugin){this.plugin=plugin;}
 private TaskHandle schedule(boolean repeating,BiFunction<Consumer<ScheduledTask>,Runnable,ScheduledTask> submit,Runnable action){
  return schedule(repeating,submit,action,()->{});
 }
 private TaskHandle schedule(boolean repeating,BiFunction<Consumer<ScheduledTask>,Runnable,ScheduledTask> submit,Runnable action,Runnable onRetired){
  long id=ids.incrementAndGet();var handle=new TaskHandle(()->tasks.remove(id));if(closed){handle.cancel();onRetired.run();return handle;}tasks.put(id,handle);
  var retired=new java.util.concurrent.atomic.AtomicBoolean();
  Runnable retirement=()->{handle.finish();if(retired.compareAndSet(false,true))onRetired.run();};
  try{var task=submit.apply(t->{if(!closed&&handle.active())try{action.run();}finally{if(!repeating)handle.finish();}else retirement.run();},retirement);handle.bind(task);if(closed)handle.cancel();if(task==null)retirement.run();}catch(RuntimeException ex){retirement.run();if(!closed)throw ex;}return handle;
 }
 public TaskHandle runAsync(Runnable task){return schedule(false,(c,r)->Bukkit.getAsyncScheduler().runNow(plugin,c),task);}
 public TaskHandle runAsyncLater(Runnable task,long delay,TimeUnit unit){return schedule(false,(c,r)->Bukkit.getAsyncScheduler().runDelayed(plugin,c,Math.max(1,unit.toMillis(delay)),TimeUnit.MILLISECONDS),task);}
 public TaskHandle runRepeatingAsync(Runnable task,long initial,long period,TimeUnit unit){return schedule(true,(c,r)->Bukkit.getAsyncScheduler().runAtFixedRate(plugin,c,Math.max(1,unit.toMillis(initial)),Math.max(1,unit.toMillis(period)),TimeUnit.MILLISECONDS),task);}
 public TaskHandle runEntity(Entity entity,Runnable task){return runEntityLater(entity,task,1);}
 public TaskHandle runEntityLater(Entity entity,Runnable task,long ticks){return schedule(false,(c,r)->entity.getScheduler().runDelayed(plugin,c,r,Math.max(1,ticks)),task);}
 public TaskHandle repeatEntity(Entity entity,Runnable task,long ticks){return schedule(true,(c,r)->entity.getScheduler().runAtFixedRate(plugin,c,r,1,Math.max(1,ticks)),task);}
 public void player(UUID id,Consumer<Player> action){var p=Bukkit.getPlayer(id);if(p!=null)runEntity(p,()->{if(p.isOnline())action.accept(p);});}
 public void player(UUID id,Consumer<Player> action,Runnable unavailable){
  var player=Bukkit.getPlayer(id);if(player==null){unavailable.run();return;}
  schedule(false,(c,r)->player.getScheduler().runDelayed(plugin,c,r,1),
    ()->{if(player.isOnline())action.accept(player);else unavailable.run();},unavailable);
 }
 public TaskHandle runRegion(Location loc,Runnable task){return runRegionLater(loc,task,1);}
 public TaskHandle runRegionLater(Location loc,Runnable task,long ticks){return schedule(false,(c,r)->Bukkit.getRegionScheduler().runDelayed(plugin,loc,c,Math.max(1,ticks)),task);}
 public TaskHandle runGlobal(Runnable task){return runGlobalLater(task,1);}
 public TaskHandle runGlobalLater(Runnable task,long ticks){return schedule(false,(c,r)->Bukkit.getGlobalRegionScheduler().runDelayed(plugin,c,Math.max(1,ticks)),task);}
 /** 传送边界统一接收不可变世界 UUID 与坐标；业务层不再持有或创建跨区域 World/Location。 */
 public CompletableFuture<Boolean> teleportAsync(Entity entity,UUID worldId,double x,double y,double z,float yaw,float pitch){
  if(entity==null||worldId==null)return CompletableFuture.completedFuture(false);
  World world=Bukkit.getWorld(worldId);
  if(world==null)return CompletableFuture.completedFuture(false);
  return teleportAsync(entity,new Location(world,x,y,z,yaw,pitch));
 }
 public CompletableFuture<Boolean> teleportAsync(Entity entity,Location destination){return entity.teleportAsync(destination.clone());}
 public int activeTaskCount(){return tasks.size();}
 @Override public void close(){
  closed=true;
  // 一个核心任务取消失败也必须继续清理其余句柄。
  tasks.values().forEach(task->{try{task.cancel();}catch(RuntimeException ex){plugin.getLogger().log(java.util.logging.Level.WARNING,"结婚系统任务取消失败",ex);}});
  tasks.clear();
  try{Bukkit.getAsyncScheduler().cancelTasks(plugin);}finally{Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);}
 }
}

