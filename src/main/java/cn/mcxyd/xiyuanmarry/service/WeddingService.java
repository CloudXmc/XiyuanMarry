package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.MarriageRepository;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import static cn.mcxyd.xiyuanmarry.service.RuleViolation.require;

/** 场地只保存坐标 DTO；所有实时玩家操作均进入实体调度器。 */
public final class WeddingService implements AutoCloseable {
    private record Participant(UUID live, UUID id, PlayerSnapshot.Point point, boolean required) {}
    private record Ceremony(MarriageRecord marriage, List<Participant> people, UUID generation,
                            UUID database, UUID epoch, long seconds) {}
    private static final class Session {
        final Ceremony ceremony;
        final WeddingCeremony vows = new WeddingCeremony();
        final long deadline;
        int remaining;
        boolean ready;
        TaskHandle timeout;
        Session(Ceremony ceremony) {
            this.ceremony=ceremony; remaining=(int)ceremony.people().stream().filter(Participant::required).count();
            deadline=Math.addExact(System.currentTimeMillis(),Math.multiplyExact(ceremony.seconds(),1000));
        }
    }
    private final MarriageService marriages;
    private final ConfigurationManager config;
    private final UnifiedScheduler scheduler;
    private final WeddingInvitationService invitations;
    private final Object lifecycle = new Object();
    private final Map<String,Session> active = new ConcurrentHashMap<>();
    private final Map<UUID,Session> pendingChat = new ConcurrentHashMap<>();
    private volatile UUID epoch = UUID.randomUUID();
    private volatile boolean closed;

    public WeddingService(MarriageService m,ConfigurationManager c,UnifiedScheduler s) {
        marriages=m; config=c; scheduler=s; invitations=new WeddingInvitationService(m,active::containsKey);
    }
    public String oathText() { return config.config().getString("wedding.oath-text","我愿意"); }
    public WeddingPlan plan(MarriageRepository r,String id) {
        String raw=r.get("weddings",id);
        if(raw==null)return WeddingPlan.empty();
        try {
            WeddingPlan plan=marriages.json().fromJson(raw,WeddingPlan.class);
            return plan==null||plan.points()==null||plan.invites()==null ? WeddingPlan.empty() : plan;
        } catch(RuntimeException invalid) {
            marriages.warnMetadata("weddings",id);
            return WeddingPlan.empty();
        }
    }
    private void save(MarriageRepository r,String id,WeddingPlan p) { r.put("weddings",id,marriages.json().toJson(p)); }
    private MarriageRecord engagement(MarriageRepository r,UUID id) {
        var m=r.findByPlayer(id);
        require(m!=null && m.state()==MarriageState.ENGAGED && m.type().equals("WEDDING"),"wedding-required");
        require(m.createdAt()>System.currentTimeMillis()-marriages.setting("marriage.engagement-hours",48)*3600000,"wedding-required");
        return m;
    }
    public void setPoint(PlayerSnapshot actor,String role) {
        if(actor==null)return;
        if(actor.point()==null || actor.point().world()==null) {
            marriages.notifyLive(actor.liveId(),"wedding-location-required");
            return;
        }
        require(Set.of("location","nx","nl","ly").contains(role)||validSeat(role),"invalid-argument");
        marriages.submit(actor.liveId(),r->{
            var m=engagement(r,actor.id()); require(!active.containsKey(m.id()),"wedding-running");
            save(r,m.id(),plan(r,m.id()).point(role,actor.point())); return null;
        },x->marriages.notifyLive(actor.liveId(),"wedding-point-set","role",role));
    }
    private boolean validSeat(String role) {
        try { int n=Integer.parseInt(role); return n>=1 && n<=1000; } catch(NumberFormatException e) { return false; }
    }
    private boolean validPoint(PlayerSnapshot.Point point,UUID world) {
        return point!=null&&point.world()!=null&&world.equals(point.world());
    }
    public void invite(PlayerSnapshot actor,PlayerSnapshot guest) {
        if(actor==null)return;
        require(guest!=null,"offline");
        marriages.submit(actor.liveId(),r->{
            var m=engagement(r,actor.id()); require(!m.contains(guest.id()),"invalid-argument");
            require(!active.containsKey(m.id()),"wedding-running");
            var current=plan(r,m.id());
            var previous=current.invites().get(guest.id());
            // 已接受的请帖不能被重复发送覆盖，否则会把宾客状态重置为未接受。
            require(previous==null||!previous.accepted(),"invite-already-accepted");
            save(r,m.id(),current.invite(guest.id(),new WeddingPlan.Invite(false,
                    System.currentTimeMillis()+marriages.setting("wedding.invite-expire-hours",24)*3600000)));
            return null;
        },x->{ marriages.notifyLive(actor.liveId(),"invite-sent"); marriages.notifyLive(guest.liveId(),"invited","player",actor.name()); });
    }
    public void respond(PlayerSnapshot guest,boolean accept) { if(guest!=null)invitations.respond(guest,null,accept); }
    public void respond(PlayerSnapshot guest,String weddingId,boolean accept) { if(guest!=null)invitations.respond(guest,weddingId,accept); }
    public List<WeddingInvitationService.Invitation> pendingInvitations(MarriageRepository repository,UUID guest) {
        return guest==null?List.of():invitations.pending(repository,guest);
    }

    public void start(PlayerSnapshot actor) {
        if(closed || actor==null)return;
        if(!marriages.ready()) {
            marriages.notifyLive(actor.liveId(),"database-not-ready");
            return;
        }
        UUID requestEpoch=epoch, database=marriages.databaseGeneration();
        var settings=config.snapshot();
        marriages.submitAtGeneration(actor.liveId(),database,r->{
            require(!closed && requestEpoch.equals(epoch) && settings.generation().equals(config.snapshot().generation()),"wedding-required");
            require(settings.files().get("config.yml").getBoolean("wedding.enabled",true),"feature-unavailable");
            var m=engagement(r,actor.id()); require(!active.containsKey(m.id()),"wedding-running");
            var p=plan(r,m.id());
            require(p.points().keySet().containsAll(Set.of("location","nx","nl","ly")),"wedding-location-required");
            var venue=p.points().get("location");
            require(venue!=null&&venue.world()!=null&&validPoint(p.points().get("nx"),venue.world())
                    &&validPoint(p.points().get("nl"),venue.world())&&validPoint(p.points().get("ly"),venue.world()),"wedding-location-required");
            var first=marriages.directory().identity(m.playerOne()); var second=marriages.directory().identity(m.playerTwo());
            require(first!=null && second!=null,"offline");
            var people=new ArrayList<Participant>();
            people.add(new Participant(first.liveId(),first.id(),p.points().get("nx"),true));
            people.add(new Participant(second.liveId(),second.id(),p.points().get("nl"),true));
            var seats=p.points().entrySet().stream().filter(e->validSeat(e.getKey()))
                    .peek(e->require(validPoint(e.getValue(),venue.world()),"wedding-location-required"))
                    .sorted(Comparator.comparingInt(e->Integer.parseInt(e.getKey()))).map(Map.Entry::getValue).toList();
            int seat=0;
            // Map 的迭代顺序不属于持久化契约；按宾客身份排序才能让席位跨重载、重启保持稳定。
            var acceptedGuests=p.invites().entrySet().stream()
                    .filter(e->e.getValue().accepted() && e.getValue().expires()>System.currentTimeMillis())
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                    .toList();
            for(var e:acceptedGuests) {
                var guest=marriages.directory().identity(e.getKey());
                if(guest!=null) {
                    require(seat<seats.size(),"wedding-location-required");
                    people.add(new Participant(guest.liveId(),guest.id(),seats.get(seat++),false));
                }
            }
            long seconds=Math.max(1,settings.files().get("config.yml").getLong("wedding.ceremony-timeout-seconds",300));
            return new Ceremony(m,List.copyOf(people),settings.generation(),database,requestEpoch,seconds);
        },this::teleport,error->{});
    }
    private boolean fresh(Ceremony c) {
        if(closed || !c.epoch().equals(epoch) || !c.generation().equals(config.snapshot().generation()))return false;
        try { if(!c.database().equals(marriages.databaseGeneration()))return false; }
        catch(IllegalStateException closing) { return false; }
        var m=marriages.view().byPlayer().get(c.marriage().playerOne());
        return m!=null && m.id().equals(c.marriage().id()) && m.state()==MarriageState.ENGAGED && m.type().equals("WEDDING");
    }
    private boolean current(Session session) {
        return !closed && session.ceremony.epoch().equals(epoch)
                && active.get(session.ceremony.marriage().id())==session;
    }
    private void teleport(Ceremony ceremony) {
        if(!fresh(ceremony))return;
        final Session session;
        try { session=new Session(ceremony); }
        catch(ArithmeticException invalidTimeout) { fail(ceremony); return; }
        synchronized(lifecycle) {
            if(closed || !ceremony.epoch().equals(epoch) || active.putIfAbsent(ceremony.marriage().id(),session)!=null)return;
        }
        try {
            var timer=scheduler.runGlobalLater(()->stop(session,true),Math.multiplyExact(ceremony.seconds(),20));
            boolean keep;
            synchronized(lifecycle) { keep=current(session); if(keep)session.timeout=timer; }
            if(!keep) { cancel(timer); return; }
        } catch(RuntimeException rejected) { stop(session,true); return; }
        for(var target:ceremony.people()) {
            if(!current(session))break;
            var completed=new AtomicBoolean();
            Consumer<Boolean> finished=ok->{ if(completed.compareAndSet(false,true))arrived(session,ok,target.required()); };
            try {
                scheduler.player(target.live(),player->{
                    if(!current(session))return;
                    if(!fresh(ceremony)) { stop(session,true); return; }
                    try {
                        if(!player.isOnline() || player.isDead()) { finished.accept(false); return; }
                        var identity=marriages.directory().capture(player);
                        if(identity == null || !identity.liveId().equals(target.live()) || !identity.id().equals(target.id())) { finished.accept(false); return; }
                        var point=target.point();
                        scheduler.teleportAsync(player,point.world(),point.x(),point.y(),point.z(),point.yaw(),point.pitch())
                                .whenComplete((ok,error)->finished.accept(error==null && Boolean.TRUE.equals(ok)));
                    } catch(RuntimeException failure) { finished.accept(false); }
                },()->finished.accept(false));
            } catch(RuntimeException rejected) { finished.accept(false); }
        }
    }
    private void arrived(Session session,boolean success,boolean required) {
        if(!current(session))return;
        // 宾客是可选参与者：离线、死亡或传送失败时跳过，不阻断新人宣誓。
        if(!required)return;
        if(!success || !fresh(session.ceremony)) { stop(session,true); return; }
        boolean expired=false, ready=false;
        synchronized(lifecycle) {
            if(!current(session))return;
            if(System.currentTimeMillis()>=session.deadline)expired=true;
            else if(--session.remaining==0) {
                var m=session.ceremony.marriage(); session.vows.start(m.playerOne(),m.playerTwo(),session.deadline);
                session.ready=true; ready=true;
                for(var p:session.ceremony.people())if(m.contains(p.id()))pendingChat.put(p.live(),session);
            }
        }
        if(expired)stop(session,true);
        else if(ready)for(var p:session.ceremony.people())if(session.ceremony.marriage().contains(p.id()) && current(session))
            marriages.notifyLive(p.live(),"oath-prompt","oath",oathText());
    }
    public boolean pending(UUID live) { if(live==null)return false; var s=pendingChat.get(live); return s!=null && current(s); }
    public void oathFromChat(UUID live) {
        if(live==null)return;
        // 捕获收到聊天时的会话，不能将延迟宣誓应用到稍后启动的另一场仪式。
        var session=pendingChat.get(live); if(session==null || !current(session))return;
        try {
            scheduler.player(live,player->{
                if(!current(session) || pendingChat.get(live)!=session)return;
                if(!player.isOnline() || player.isDead()) { stop(session,true); return; }
                var identity = marriages.directory().capture(player);
                if (identity == null) { stop(session,true); return; }
                oath(identity,session);
            },()->stop(session,true));
        } catch(RuntimeException rejected) { stop(session,true); }
    }
    public void oath(PlayerSnapshot actor) {
        if(actor==null)return;
        var m=marriages.view().byPlayer().get(actor.id());
        var session=m==null ? null : active.get(m.id());
        if(session==null) { marriages.notifyLive(actor.liveId(),"oath-missing"); return; }
        oath(actor,session);
    }
    private void oath(PlayerSnapshot actor,Session session) {
        if(!fresh(session.ceremony)) { stop(session,true); return; }
        var target=session.ceremony.people().stream().filter(p->p.id().equals(actor.id()) && p.live().equals(actor.liveId())).findFirst().orElse(null);
        // 误在站位外宣誓只拒绝本次宣誓；不能因为一次错误输入取消整场婚礼。
        if(target==null || actor.point()==null || !actor.point().near(target.point(),5)) {
            marriages.notifyLive(actor.liveId(),"oath-position-required");
            return;
        }
        WeddingCeremony.Result result; TaskHandle timer=null;
        // 状态转换与摘除必须原子完成；所有调度、通知、事务均在锁外。
        synchronized(lifecycle) {
            if(!current(session))return;
            result=session.ready ? session.vows.confirm(actor.id(),System.currentTimeMillis()) : WeddingCeremony.Result.ABSENT;
            if(result==WeddingCeremony.Result.COMPLETE)timer=detach(session);
        }
        if(result==WeddingCeremony.Result.COMPLETE) {
            cancel(timer);
            var c=session.ceremony; marriages.completeWedding(c.marriage(),c.generation(),c.database());
        } else if(result==WeddingCeremony.Result.WAITING)marriages.notifyLive(actor.liveId(),"oath-waiting");
        else marriages.notifyLive(actor.liveId(),"oath-missing");
    }
    public void leave(UUID live) {
        if(live==null)return;
        for(var session:List.copyOf(active.values()))
            // 宾客离场只跳过该宾客；新人离场才中止整场婚礼。
            if(session.ceremony.people().stream().anyMatch(p->p.required() && p.live().equals(live)))stop(session,true);
    }
    private TaskHandle detach(Session session) {
        active.remove(session.ceremony.marriage().id(),session); session.vows.clear();
        for(var p:session.ceremony.people())pendingChat.remove(p.live(),session);
        var timer=session.timeout; session.timeout=null; return timer;
    }
    private void stop(Session session,boolean error) {
        TaskHandle timer;
        synchronized(lifecycle) {
            if(active.get(session.ceremony.marriage().id())!=session)return;
            timer=detach(session);
        }
        cancel(timer); if(error)fail(session.ceremony);
    }
    private void fail(Ceremony c) {
        for(var p:c.people())if(c.marriage().contains(p.id()))marriages.notifyLive(p.live(),"teleport-failed");
    }
    private void cancel(TaskHandle timer) {
        if(timer!=null)try { timer.cancel(); } catch(RuntimeException failure) { config.warn("婚礼超时任务取消失败："+failure); }
    }
    public void clear() { reset(false); }
    @Override public void close() { reset(true); }
    private void reset(boolean closing) {
        var timers=new ArrayList<TaskHandle>();
        synchronized(lifecycle) {
            if(closing)closed=true; epoch=UUID.randomUUID();
            for(var session:List.copyOf(active.values())) { var timer=detach(session); if(timer!=null)timers.add(timer); }
            pendingChat.clear();
        }
        timers.forEach(this::cancel);
    }
}
