package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.DatabaseManager;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import org.bukkit.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.*;

/** DB只处理冷却令牌；坐标由伴侣实体线程采集，实际传送回到发起者线程。 */
public final class PartnerTeleportService implements AutoCloseable {
    private enum Phase { RESERVED, DISPATCHED, DONE }
    private static final class Operation {
        final PlayerSnapshot actor;final String relationship;final UUID partner;final UUID generation;
        final AtomicReference<Phase> phase=new AtomicReference<>(Phase.RESERVED);
        volatile TeleportCooldownLedger.Ticket ticket;volatile TaskHandle timeout;
        Operation(PlayerSnapshot a,String r,UUID p,UUID g){actor=a;relationship=r;partner=p;generation=g;}
    }
    private final MarriageService marriages;private final ConfigurationManager config;private final DatabaseManager database;
    private final IoDispatcher io;private final UnifiedScheduler scheduler;private final Logger logger;
    private final TeleportCooldownLedger ledger=new TeleportCooldownLedger();
    private final ConcurrentMap<UUID,Operation> pending=new ConcurrentHashMap<>();
    private volatile boolean closed;
    public PartnerTeleportService(MarriageService m,ConfigurationManager c,DatabaseManager d,IoDispatcher io,UnifiedScheduler s,Logger logger){
        marriages=m;config=c;database=d;this.io=io;scheduler=s;this.logger=logger;
    }
    public void teleport(PlayerSnapshot actor){
        if(closed)return;var marriage=marriages.view().byPlayer().get(actor.id());
        if(!valid(marriage)){marriages.notifyLive(actor.liveId(),"married-required");return;}
        var partner=marriages.directory().identity(marriage.partnerOf(actor.id()));
        if(partner==null){marriages.notifyLive(actor.liveId(),"offline");return;}
        var operation=new Operation(actor,marriage.id(),partner.liveId(),config.snapshot().generation());
        if(pending.putIfAbsent(actor.liveId(),operation)!=null){marriages.notifyLive(actor.liveId(),"busy");return;}
        operation.timeout=scheduler.runAsyncLater(()->timeout(operation),30,TimeUnit.SECONDS);
        if(!io.submit(()->reserve(operation)))finish(operation,false,"busy");
    }
    private void reserve(Operation operation){
        if(!current(operation)){finish(operation,false,"partner-teleport-failed");return;}
        try{
            var ticket=database.use(r->r.transaction(tx->{
                var marriage=tx.findByPlayer(operation.actor.id());
                RuleViolation.require(valid(marriage)&&marriage.id().equals(operation.relationship),"married-required");
                return ledger.reserve(tx,operation.actor.id(),System.currentTimeMillis(),
                    config.config().getLong("privileges.partner-teleport-cooldown-seconds",600)*1000L);
            }));
            operation.ticket=ticket;
            if(!current(operation)||operation.phase.get()==Phase.DONE){release(operation);return;}
            scheduler.player(operation.partner,partner->{
                if(!current(operation)){finish(operation,false,"partner-teleport-failed");return;}
                var target=marriages.directory().capture(partner);
                scheduler.player(operation.actor.liveId(),actor->{
                    if(!current(operation)||!marriages.directory().capture(actor).id().equals(operation.actor.id())
                        ||System.currentTimeMillis()-target.seenAt()>5000){finish(operation,false,"partner-teleport-failed");return;}
                    var marriage=marriages.view().byPlayer().get(operation.actor.id());
                    if(!valid(marriage)||!marriage.id().equals(operation.relationship)||!marriage.partnerOf(operation.actor.id()).equals(target.id())){finish(operation,false,"married-required");return;}
                    if(actor.isDead()){finish(operation,false,"partner-teleport-failed");return;}
                    if(!operation.phase.compareAndSet(Phase.RESERVED,Phase.DISPATCHED))return;
                    var point=target.point();
                    try{scheduler.teleportAsync(actor,point.world(),point.x(),point.y(),point.z(),point.yaw(),point.pitch())
                        .whenComplete((ok,error)->finish(operation,error==null&&Boolean.TRUE.equals(ok),error==null&&Boolean.TRUE.equals(ok)?"teleport-success":"partner-teleport-failed"));
                    }catch(RuntimeException failure){logger.log(Level.WARNING,"伴侣传送调用失败",failure);finish(operation,false,"partner-teleport-failed");}
                },()->finish(operation,false,"offline"));
            },()->finish(operation,false,"offline"));
        }catch(RuleViolation rule){finish(operation,false,rule.key());}
        catch(Exception failure){logger.log(Level.SEVERE,"伴侣传送冷却事务失败",failure);finish(operation,false,"internal-error");}
    }
    private boolean valid(MarriageRecord m){return m!=null&&m.married()&&(m.state()!=MarriageState.DIVORCE_PENDING||m.divorceAt()>System.currentTimeMillis());}
    private boolean current(Operation op){return !closed&&op.generation.equals(config.snapshot().generation())&&pending.get(op.actor.liveId())==op;}
    private void finish(Operation operation,boolean success,String message){
        if(operation.phase.getAndSet(Phase.DONE)==Phase.DONE)return;
        pending.remove(operation.actor.liveId(),operation);if(operation.timeout!=null)operation.timeout.cancel();
        if(!success)release(operation);
        if(!closed&&operation.generation.equals(config.snapshot().generation()))marriages.notifyLive(operation.actor.liveId(),message);
    }
    private void timeout(Operation operation){
        Phase prior=operation.phase.getAndSet(Phase.DONE);if(prior==Phase.DONE)return;
        pending.remove(operation.actor.liveId(),operation);if(operation.timeout!=null)operation.timeout.cancel();
        // 已交给核心的异步传送结果不明，不能释放冷却后允许新请求重入。
        if(prior==Phase.RESERVED)release(operation);
        if(!closed)marriages.notifyLive(operation.actor.liveId(),prior==Phase.DISPATCHED?"partner-teleport-timeout":"partner-teleport-failed");
    }
    private void release(Operation operation){
        var ticket=operation.ticket;if(ticket==null||closed||!operation.generation.equals(config.snapshot().generation()))return;
        if(!io.submit(()->{
            if(closed||!operation.generation.equals(config.snapshot().generation()))return;
            try{database.use(r->ledger.release(r,ticket));}catch(Exception failure){logger.log(Level.WARNING,"传送冷却释放失败，将等待原冷却到期",failure);}
        }))logger.warning("传送冷却释放队列繁忙，将等待原冷却到期");
    }
    public void leave(UUID live){var op=pending.get(live);if(op!=null)timeout(op);}
    public void reload(){for(var op:List.copyOf(pending.values()))timeout(op);}
    @Override public void close(){closed=true;for(var op:pending.values()){op.phase.set(Phase.DONE);if(op.timeout!=null)op.timeout.cancel();}pending.clear();}
}
