package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.config.*;import cn.mcxyd.xiyuanmarry.message.*;import cn.mcxyd.xiyuanmarry.model.*;import cn.mcxyd.xiyuanmarry.repository.*;import cn.mcxyd.xiyuanmarry.scheduler.*;
import com.google.gson.Gson;import org.bukkit.Bukkit;import org.bukkit.command.CommandSender;import org.bukkit.entity.Player;import org.bukkit.plugin.java.JavaPlugin;
import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.function.*;import static cn.mcxyd.xiyuanmarry.service.RuleViolation.require;
/** 婚姻事务编排。命令和GUI复用相同资格检查，任何成功提示都在提交后发送。 */
public final class MarriageService {
 public record View(Map<UUID,MarriageRecord> byPlayer,Map<UUID,PlayerProfile> profiles,List<MarriageRecord> couples,Map<String,String> metadata){public View{byPlayer=Map.copyOf(byPlayer);profiles=Map.copyOf(profiles);couples=List.copyOf(couples);metadata=Map.copyOf(metadata);}}
 private final JavaPlugin plugin;private final ConfigurationManager config;private final MessageService messages;private final DatabaseManager database;private final UnifiedScheduler scheduler;private final IoDispatcher io;private final PlayerDirectory directory;private final Gson gson=new Gson();private final Set<UUID> busy=ConcurrentHashMap.newKeySet();private volatile View view=new View(Map.of(),Map.of(),List.of(),Map.of());private volatile boolean closed;private TaskHandle timer;private Runnable afterReload=()->{};
 public MarriageService(JavaPlugin p,ConfigurationManager c,MessageService m,DatabaseManager db,UnifiedScheduler s,IoDispatcher io,PlayerDirectory d){plugin=p;config=c;messages=m;database=db;scheduler=s;this.io=io;directory=d;}
 public void initialize(){database.use(r->{refresh(r);return null;});directory.onJoin(this::register);timer=scheduler.runRepeatingAsync(()->submit(null,r->{maintain(r,System.currentTimeMillis());return null;},x->{}),1,15,TimeUnit.SECONDS);}
 public void onReload(Runnable callback){afterReload=callback;}public View view(){return view;}public PlayerDirectory directory(){return directory;}public Gson json(){return gson;}
 public UUID databaseGeneration(){return database.generation();}
 public String name(UUID id){var p=view.profiles().get(id);return p==null?id.toString():p.name();}
 public int level(long exp){int level=1;for(int i=2;i<=10;i++)if(exp>=config.config().getLong("bond.levels."+i+".required"))level=i;return level;}
 public String title(int level){return config.config().getString("bond.levels."+level+".name","");}
 public long setting(String key,long fallback){return config.config().getLong(key,fallback);}public LocalDate today(){return LocalDate.now(ZoneId.of(config.config().getString("timezone","Asia/Shanghai")));}
 public void notifyLive(UUID live,String key,Object...values){if(!closed&&live!=null)scheduler.player(live,p->messages.send(p,key,values));}
 public void notifyIdentity(UUID id,String key,Object...values){var p=directory.identity(id);if(p!=null)notifyLive(p.liveId(),key,values);}
 public void broadcast(String key,Object...values){for(var p:directory.all())notifyLive(p.liveId(),key,values);}
 public void partnerChat(PlayerSnapshot actor, String raw) {
  String content=PartnerInteractionPolicy.validateChat(raw, 200);
  if(content==null){notifyLive(actor.liveId(),"invalid-argument");return;}
  var marriage=view.byPlayer().get(actor.id());
  if(marriage==null||!marriage.married()){notifyLive(actor.liveId(),"married-required");return;}
  var partner=directory.identity(marriage.partnerOf(actor.id()));
  if(partner==null){notifyLive(actor.liveId(),"offline");return;}
  notifyLive(actor.liveId(),"partner-chat","player",actor.name(),"message",content);
  if(!partner.liveId().equals(actor.liveId()))notifyLive(partner.liveId(),"partner-chat","player",name(actor.id()),"message",content);
 }
 public <T>void submit(UUID actor,Function<MarriageRepository,T> work,Consumer<T> committed){submit(actor,work,committed,error->{});}
 public <T>void submit(UUID actor,Function<MarriageRepository,T> work,Consumer<T> committed,Consumer<Throwable> failed){submitAtGeneration(actor,null,work,committed,failed,()->{});}
 public <T>void submit(UUID actor,Function<MarriageRepository,T> work,Consumer<T> committed,Consumer<Throwable> failed,Runnable afterCommit){submitAtGeneration(actor,null,work,committed,failed,afterCommit);}
 public <T>void submitAtGeneration(UUID actor,UUID expectedGeneration,Function<MarriageRepository,T> work,Consumer<T> committed,Consumer<Throwable> failed){submitAtGeneration(actor,expectedGeneration,work,committed,failed,()->{});}
 /** afterCommit 只做纯内存提交标记；必须早于缓存刷新、租约释放和可能跳过的玩家通知。 */
 public <T>void submitAtGeneration(UUID actor,UUID expectedGeneration,Function<MarriageRepository,T> work,Consumer<T> committed,Consumer<Throwable> failed,Runnable afterCommit){
  if(closed){failed.accept(new IllegalStateException("插件已关闭"));return;}
  if(actor!=null&&!busy.add(actor)){notifyLive(actor,"busy");failed.accept(new IllegalStateException("操作繁忙"));return;}
  boolean accepted=io.submit(()->{
   try{
    if(closed){failed.accept(new IllegalStateException("插件已关闭"));return;}
    T value=database.use(expectedGeneration,r->{
     T result=r.transaction(work);
     afterCommit.run();
     // 刷新失败不等于事务回滚；保持最后有效快照并记录控制台异常。
     try{refresh(r);}catch(RuntimeException error){plugin.getLogger().log(java.util.logging.Level.SEVERE,"结婚系统事务已提交，但缓存刷新失败；保留旧快照",error);}
     return result;
    });
    if(!closed)committed.accept(value);
   }catch(DatabaseManager.StaleGenerationException e){failed.accept(e);}
   catch(Throwable e){
    Throwable reason=e;
    while(reason instanceof TransactionRollbackException && reason.getCause()!=null)reason=reason.getCause();
    if(reason instanceof RuleViolation rule)notifyLive(actor,rule.key());
    else{plugin.getLogger().log(java.util.logging.Level.SEVERE,"结婚系统事务或提交回调失败；对玩家隐藏技术详情",e);notifyLive(actor,"internal-error");}
    failed.accept(e);
   }finally{if(actor!=null)busy.remove(actor);}
  });
  if(!accepted){if(actor!=null)busy.remove(actor);notifyLive(actor,"busy");failed.accept(new IllegalStateException("IO队列已满"));}
 }
 private void refresh(MarriageRepository r){var list=r.findAll();var map=new HashMap<UUID,MarriageRecord>();for(var m:list){map.put(m.playerOne(),m);map.put(m.playerTwo(),m);}var profiles=new HashMap<UUID,PlayerProfile>();for(var raw:r.entries("profiles").values()){var p=gson.fromJson(raw,PlayerProfile.class);profiles.put(p.id(),p);}view=new View(map,profiles,list,r.entries("settings"));}
 public void addBond(UUID id,long amount){if(amount>0)submit(null,r->{return r.addBond(id,amount);},x->{});}
 public void register(PlayerSnapshot p){submit(null,r->{var old=r.get("profiles",p.id().toString());String name=p.name();if(old!=null&&!config.config().getBoolean("identity.save-last-known-name",true))name=gson.fromJson(old,PlayerProfile.class).name();r.put("profiles",p.id().toString(),gson.toJson(new PlayerProfile(p.id(),p.identityKey(),name,p.liveId())));return null;},x->{});}
 private void eligible(MarriageRepository r,PlayerSnapshot a,PlayerSnapshot b){require(a!=null&&b!=null,"offline");require(!a.id().equals(b.id()),"self-proposal");require(directory.identity(a.id())!=null&&directory.identity(b.id())!=null,"offline");require(r.findByPlayer(a.id())==null&&r.findByPlayer(b.id())==null,"already-married");require(number(r,"cooldowns",a.id().toString())<=System.currentTimeMillis()&&number(r,"cooldowns",b.id().toString())<=System.currentTimeMillis(),"cooldown");require(a.onlineMinutes()>=setting("marriage.minimum-online-minutes",30)&&b.onlineMinutes()>=setting("marriage.minimum-online-minutes",30),"minimum-online");require(r.get("blocks",b.id()+":"+a.id())==null,"blocked");}
 public void propose(PlayerSnapshot a,PlayerSnapshot b,String mode){submit(a.liveId(),r->{maintain(r,System.currentTimeMillis());eligible(r,a,b);for(var raw:r.entries("proposals").values()){var p=gson.fromJson(raw,Proposal.class);require(!p.proposer().equals(a.id())&&!p.target().equals(a.id())&&!p.target().equals(b.id())&&!p.proposer().equals(b.id()),"request-pending");}String key=today()+":"+a.id();long n=number(r,"daily",key);require(n<setting("marriage.proposal-daily-limit",3),"daily-limit");var request=new Proposal(UUID.randomUUID(),a.id(),b.id(),mode,System.currentTimeMillis()+setting("marriage.proposal-expire-seconds",300)*1000);r.put("proposals",b.id().toString(),gson.toJson(request));r.put("daily",key,Long.toString(n+1));return request;},q->{String label=messages.raw("mode-"+mode.toLowerCase(Locale.ROOT));notifyLive(a.liveId(),"proposal-sent","player",b.name(),"mode",label);notifyLive(b.liveId(),"proposal-received","player",a.name(),"mode",label);});}
 public void accept(PlayerSnapshot target){submit(target.liveId(),r->{maintain(r,System.currentTimeMillis());String raw=r.get("proposals",target.id().toString());require(raw!=null,"request-missing");var q=gson.fromJson(raw,Proposal.class);var first=directory.identity(q.proposer());eligible(r,first,target);require(q.expiresAt()>System.currentTimeMillis(),"request-missing");boolean normal=q.type().equals("NORMAL");require(normal?r.createMarriage(q.proposer(),q.target(),q.type(),System.currentTimeMillis()):r.createEngagement(q.proposer(),q.target(),q.type(),System.currentTimeMillis()),"already-married");r.remove("proposals",target.id().toString());return r.findByPlayer(target.id());},m->{if(m.married())announce(m);else{notifyIdentity(m.playerOne(),"engaged","hours",setting("marriage.engagement-hours",48));notifyIdentity(m.playerTwo(),"engaged","hours",setting("marriage.engagement-hours",48));}});}
 public void deny(PlayerSnapshot p){submit(p.liveId(),r->{require(r.get("proposals",p.id().toString())!=null,"request-missing");r.remove("proposals",p.id().toString());return null;},x->notifyLive(p.liveId(),"proposal-denied"));}
 public void cancel(PlayerSnapshot p){submit(p.liveId(),r->{var m=r.findByPlayer(p.id());require(m!=null&&m.state()==MarriageState.ENGAGED,"wedding-required");r.deleteMarriage(p.id());r.remove("weddings",m.id());return m;},m->{notifyIdentity(m.playerOne(),"cancelled");notifyIdentity(m.playerTwo(),"cancelled");});}
 public void divorce(PlayerSnapshot p,boolean withdraw){submit(p.liveId(),r->{maintain(r,System.currentTimeMillis());require(withdraw?r.withdrawDivorce(p.id()):r.requestDivorce(p.id(),System.currentTimeMillis()+setting("marriage.divorce-cooling-hours",24)*3600000),"married-required");return r.findByPlayer(p.id());},m->{for(UUID id:List.of(m.playerOne(),m.playerTwo()))notifyIdentity(id,withdraw?"divorce-withdrawn":"divorce-requested","hours",setting("marriage.divorce-cooling-hours",24));});}
 public void block(PlayerSnapshot p,UUID target,boolean blocked){submit(p.liveId(),r->{String key=p.id()+":"+target;if(blocked)r.put("blocks",key,"1");else r.remove("blocks",key);return null;},x->notifyLive(p.liveId(),"block-set"));}
 public void completeWedding(UUID one,UUID two){submit(null,r->{var m=r.findByPlayer(one);require(m!=null&&m.state()==MarriageState.ENGAGED&&m.partnerOf(one).equals(two)&&m.createdAt()+setting("marriage.engagement-hours",48)*3600000>System.currentTimeMillis(),"wedding-required");require(r.completeMarriage(one,two,"WEDDING",System.currentTimeMillis()),"wedding-required");return r.findByPlayer(one);},this::announce);}
 private void announce(MarriageRecord m){broadcast("married","player1",name(m.playerOne()),"player2",name(m.playerTwo()));}
 public void info(CommandSender sender,UUID id){var m=view.byPlayer().get(id);if(m==null){messages.send(sender,"not-married");return;}int lvl=level(m.bond());messages.send(sender,"info","player1",name(m.playerOne()),"player2",name(m.playerTwo()),"state",messages.raw("state-"+m.state().name().toLowerCase(Locale.ROOT)),"level",lvl,"title",title(lvl),"bond",m.bond(),"days",m.married()?Math.max(0,(System.currentTimeMillis()-m.marriedAt())/86400000):0,"hours",m.sharedSeconds()/3600);}
 public UUID identityByName(String value){var online=directory.name(value);if(online!=null)return online.id();return view.profiles().values().stream().filter(p->p.name().equalsIgnoreCase(value)).map(PlayerProfile::id).findFirst().orElse(null);}
 public void admin(UUID actor,String action,UUID a,UUID b,long amount){submit(actor,r->{switch(action){case "force"->require(r.createMarriage(a,b,"ADMIN",System.currentTimeMillis()),"already-married");case "divorce","clear"->{var m=r.findByPlayer(a);require(m!=null,"not-married");end(r,m,System.currentTimeMillis());}case "setexp"->require(r.setBond(a,amount),"not-married");case "setlevel"->{require(amount>=1&&amount<=10,"invalid-argument");require(r.setBond(a,config.config().getLong("bond.levels."+amount+".required")),"not-married");}default->throw new RuleViolation("invalid-argument");}return null;},x->{if(actor!=null)notifyLive(actor,"admin-success");else scheduler.runGlobal(()->messages.send(Bukkit.getConsoleSender(),"admin-success"));});}
 private void end(MarriageRepository r,MarriageRecord m,long now){long until=now+setting("marriage.remarriage-cooling-hours",24)*3600000;r.put("cooldowns",m.playerOne().toString(),Long.toString(until));r.put("cooldowns",m.playerTwo().toString(),Long.toString(until));r.deleteMarriage(m.playerOne());r.remove("weddings",m.id());}
 private void maintain(MarriageRepository r,long now){for(var m:r.findAll()){if(m.state()==MarriageState.ENGAGED&&m.createdAt()+setting("marriage.engagement-hours",48)*3600000<=now){r.deleteMarriage(m.playerOne());r.remove("weddings",m.id());}else if(m.state()==MarriageState.DIVORCE_PENDING&&m.divorceAt()<=now)end(r,m,m.divorceAt());}for(var e:r.entries("proposals").entrySet())if(gson.fromJson(e.getValue(),Proposal.class).expiresAt()<=now)r.remove("proposals",e.getKey());for(var e:r.entries("cooldowns").entrySet())if(Long.parseLong(e.getValue())<=now)r.remove("cooldowns",e.getKey());String cutoff=today().minusDays(7).toString();for(String k:r.entries("daily").keySet())if(k.compareTo(cutoff)<0)r.remove("daily",k);}
 public static long number(MarriageRepository r,String bucket,String key){String v=r.get(bucket,key);return v==null?0:Long.parseLong(v);}
 public void leave(UUID live,UUID id){busy.remove(live);submit(null,r->{for(var e:r.entries("proposals").entrySet()){var q=gson.fromJson(e.getValue(),Proposal.class);if(q.proposer().equals(id)||q.target().equals(id))r.remove("proposals",e.getKey());}return null;},x->{});}
 public void reload(UUID actor){if(!io.submit(()->{try{var next=config.prepare();String oldMode=config.config().getString("identity.mode");boolean switched=database.switchTo(next.database());config.publish(next);database.use(r->{refresh(r);return null;});directory.refresh();afterReload.run();notifyLive(actor,"reload-success");if(switched)notifyLive(actor,"database-switched");if(!oldMode.equals(config.config().getString("identity.mode")))broadcast("identity-changed");if(actor==null)scheduler.runGlobal(()->messages.send(Bukkit.getConsoleSender(),switched?"database-switched":"reload-success"));}catch(Exception e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"重载校验失败，旧配置继续有效",e);notifyLive(actor,"reload-failed");if(actor==null)scheduler.runGlobal(()->messages.send(Bukkit.getConsoleSender(),"reload-failed"));}}))notifyLive(actor,"busy");}
 public void shutdown(){closed=true;if(timer!=null)timer.cancel();busy.clear();}
}





