package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.config.*;import cn.mcxyd.xiyuanmarry.message.*;import cn.mcxyd.xiyuanmarry.model.*;import cn.mcxyd.xiyuanmarry.repository.*;import cn.mcxyd.xiyuanmarry.scheduler.*;
import com.google.gson.Gson;import org.bukkit.Bukkit;import org.bukkit.command.CommandSender;import org.bukkit.entity.Player;import org.bukkit.plugin.java.JavaPlugin;
import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.function.*;import static cn.mcxyd.xiyuanmarry.service.RuleViolation.require;
/** 婚姻事务编排。命令和GUI复用相同资格检查，任何成功提示都在提交后发送。 */
public final class MarriageService {
 private final Object lifecycle=new Object();
 public record View(Map<UUID,MarriageRecord> byPlayer,Map<UUID,PlayerProfile> profiles,List<MarriageRecord> couples,Map<String,String> metadata){public View{byPlayer=Map.copyOf(byPlayer);profiles=Map.copyOf(profiles);couples=List.copyOf(couples);metadata=Map.copyOf(metadata);}}
 private final JavaPlugin plugin;private final ConfigurationManager config;private final MessageService messages;private final DatabaseManager database;private final UnifiedScheduler scheduler;private final IoDispatcher io;private final PlayerDirectory directory;private final Gson gson=new Gson();private final ConcurrentMap<UUID,Object> busy=new ConcurrentHashMap<>();private final Set<String> warnedMetadata=ConcurrentHashMap.newKeySet();private volatile View view=new View(Map.of(),Map.of(),List.of(),Map.of());private volatile boolean closed,ready;private TaskHandle timer;private Runnable afterReload=()->{};
 public MarriageService(JavaPlugin p,ConfigurationManager c,MessageService m,DatabaseManager db,UnifiedScheduler s,IoDispatcher io,PlayerDirectory d){this(p,c,m,db,s,io,d,db.initialized());}
 public MarriageService(JavaPlugin p,ConfigurationManager c,MessageService m,DatabaseManager db,UnifiedScheduler s,IoDispatcher io,PlayerDirectory d,boolean initiallyReady){plugin=p;config=c;messages=m;database=db;scheduler=s;this.io=io;directory=d;ready=initiallyReady;}
 public void initialize(){synchronized(lifecycle){if(closed||!ready||timer!=null)return;directory.onJoin(this::register);timer=scheduler.runRepeatingAsync(()->submit(null,r->{maintain(r,System.currentTimeMillis());return null;},x->{}),1,15,TimeUnit.SECONDS);}}
 public void initializeAfterDatabase(){
  synchronized(lifecycle){if(closed||ready)return;}
  View initial=database.use(this::loadView);
  synchronized(lifecycle){if(closed)return;view=initial;ready=true;}
 }
 public boolean ready(){return ready&&!closed;}
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
 /** 玩家发起的普通业务提交绑定当前登录会话、配置代次和数据库代次，旧点击不能写入新会话或新库。 */
 public <T>void submitPlayer(PlayerSnapshot participant,Function<MarriageRepository,T> work,Consumer<T> committed){
  if(participant==null)return;
  UUID expectedConfig=config.snapshot().generation(), expectedDatabase=database.generation();
  Long expectedSession=directory.session(participant.liveId());
  submitAtGeneration(participant.liveId(),expectedDatabase,r->{
   require(expectedConfig.equals(config.snapshot().generation()),"relationship-request-stale");
   require(expectedSession!=null&&expectedSession.equals(directory.session(participant.liveId())),"relationship-request-stale");
   currentIdentity(participant);
   return work.apply(r);
  },committed,error->{});
 }
 public <T>void submit(UUID actor,Function<MarriageRepository,T> work,Consumer<T> committed,Consumer<Throwable> failed,Runnable afterCommit){submitAtGeneration(actor,null,work,committed,failed,afterCommit);}
 public <T>void submitAtGeneration(UUID actor,UUID expectedGeneration,Function<MarriageRepository,T> work,Consumer<T> committed,Consumer<Throwable> failed){submitAtGeneration(actor,expectedGeneration,work,committed,failed,()->{});}
 /** afterCommit 只做纯内存提交标记；必须早于缓存刷新、租约释放和可能跳过的玩家通知。 */
 public <T>void submitAtGeneration(UUID actor,UUID expectedGeneration,Function<MarriageRepository,T> work,Consumer<T> committed,Consumer<Throwable> failed,Runnable afterCommit){
  // 外部失败回调可能调度其他任务，不能在生命周期锁中执行。
  if(closed){failed.accept(new IllegalStateException("插件已关闭"));return;}
  if(!ready){if(actor!=null)notifyLive(actor,"database-not-ready");failed.accept(new IllegalStateException("数据库尚未初始化"));return;}
  // 退出允许新登录继续操作；旧任务只能移除自己持有的令牌，不能释放新请求的门闩。
  Object operation=new Object();
  if(actor!=null&&busy.putIfAbsent(actor,operation)!=null){notifyLive(actor,"busy");failed.accept(new IllegalStateException("操作繁忙"));return;}
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
    if(reason instanceof RuleViolation rule)notifyLive(actor,rule.key(),rule.values());
    else{plugin.getLogger().log(java.util.logging.Level.SEVERE,"结婚系统事务或提交回调失败；对玩家隐藏技术详情",e);notifyLive(actor,"internal-error");}
    failed.accept(e);
   }finally{if(actor!=null)busy.remove(actor,operation);}
  });
  if(!accepted){if(actor!=null)busy.remove(actor,operation);notifyLive(actor,"busy");failed.accept(new IllegalStateException("IO队列已满"));}
 }
 private View loadView(MarriageRepository r){var list=r.findAll();var map=new HashMap<UUID,MarriageRecord>();for(var m:list){map.put(m.playerOne(),m);map.put(m.playerTwo(),m);}var profiles=new HashMap<UUID,PlayerProfile>();for(var raw:r.entries("profiles").values()){var p=gson.fromJson(raw,PlayerProfile.class);profiles.put(p.id(),p);}return new View(map,profiles,list,r.entries("settings"));}
 private void refresh(MarriageRepository r){var next=loadView(r);synchronized(lifecycle){if(!closed)view=next;}}
 public void addBond(UUID id,long amount){
  if(id==null||amount<=0)return;
  long requestedAt=System.currentTimeMillis();
  // 后台增量不要求玩家在线，但必须属于提交时已存在的有效婚姻，不能转记到再婚或切换后的库。
  submitRelationshipRequest(null,null,id,false,(r,expected)->{
   if(expected==null||!expected.married()
       ||expected.state()==MarriageState.DIVORCE_PENDING&&expected.divorceAt()<=requestedAt)return false;
   var current=requireRelationship(r,id,expected,"not-married");
   if(!current.married()||current.state()==MarriageState.DIVORCE_PENDING&&current.divorceAt()<=System.currentTimeMillis())return false;
   return r.addBond(id,amount);
  },ignored->{});
 }
 public void register(PlayerSnapshot p){
  if(p==null)return;
  // 登录资料是后台事务，不占用玩家操作门闩，过期任务也不冒充管理员报错。
  submitRelationshipRequest(null,p,null,false,(r,ignored)->{
   var old=r.get("profiles",p.id().toString());String name=p.name();
   if(old!=null&&!config.config().getBoolean("identity.save-last-known-name",true))name=gson.fromJson(old,PlayerProfile.class).name();
   r.put("profiles",p.id().toString(),gson.toJson(new PlayerProfile(p.id(),p.identityKey(),name,p.liveId())));return null;
  },x->{});
 }
 private PlayerSnapshot currentIdentity(PlayerSnapshot expected){
  require(expected!=null,"offline");var current=directory.live(expected.liveId());
  require(current!=null&&current.id().equals(expected.id())&&current.identityKey().equals(expected.identityKey()),"offline");
  return current;
 }
 private record Participant(PlayerSnapshot player,Long session) {}
 /** 入队前只读取内存令牌；数据库事务执行前重新核对，旧请求不能穿透切库、重载或重登。 */
 private <T>void submitProposalRequest(PlayerSnapshot actor,List<PlayerSnapshot> players,Function<MarriageRepository,T> work,Consumer<T> committed){
  if(!ready()){if(!closed)notifyLive(actor.liveId(),"database-not-ready");return;}
  UUID expectedConfig=config.snapshot().generation(),expectedDatabase=database.generation();
  var participants=players.stream().map(p->new Participant(p,directory.session(p.liveId()))).toList();
  submitAtGeneration(actor.liveId(),expectedDatabase,r->{
   require(expectedConfig.equals(config.snapshot().generation()),"marriage-request-stale");
   for(var participant:participants){
    require(participant.session()!=null&&participant.session().equals(directory.session(participant.player().liveId())),"marriage-request-stale");
    currentIdentity(participant.player());
   }
   return work.apply(r);
  },committed,error->{if(error instanceof DatabaseManager.StaleGenerationException)notifyLive(actor.liveId(),"marriage-request-stale");});
 }
 /** actor 是消息/防抖使用的实时 UUID，target 是查婚姻使用的持久身份；两者不能混用。 */
 private <T>void submitRelationshipRequest(UUID actor,PlayerSnapshot participant,UUID target,boolean administrative,
     BiFunction<MarriageRepository,MarriageRecord,T> work,Consumer<T> committed){
  if(!ready()){if(!closed){if(administrative)notifyAdministrator(actor,"database-not-ready");else notifyLive(actor,"database-not-ready");}return;}
  UUID expectedConfig,expectedDatabase;MarriageRecord expected;
  // 与 reload 的内存发布共用短锁；锁内不执行 SQL、调度或文件 IO。
  synchronized(lifecycle){
   if(closed)return;expectedConfig=config.snapshot().generation();expectedDatabase=database.generation();
   expected=target==null?null:view.byPlayer().get(target);
  }
  Long expectedSession=participant==null?null:directory.session(participant.liveId());
  submitAtGeneration(actor,expectedDatabase,r->{
   require(expectedConfig.equals(config.snapshot().generation()),"relationship-request-stale");
   if(participant!=null){
    require(expectedSession!=null&&expectedSession.equals(directory.session(participant.liveId())),"relationship-request-stale");
    currentIdentity(participant);
   }
   return work.apply(r,expected);
  },committed,error->{
   if(error instanceof DatabaseManager.StaleGenerationException){
    if(administrative)notifyAdministrator(actor,"relationship-request-stale");else notifyLive(actor,"relationship-request-stale");
   }else if(administrative&&actor==null){
    Throwable reason=error;while(reason instanceof TransactionRollbackException&&reason.getCause()!=null)reason=reason.getCause();
    notifyAdministrator(null,reason instanceof RuleViolation rule?rule.key():"internal-error");
   }
  });
 }
 /** 即便双方玩家相同，再婚也是新的关系，旧操作不得作用于它。 */
 private MarriageRecord requireRelationship(MarriageRepository r,UUID target,MarriageRecord expected,String missingKey){
  require(expected!=null,missingKey);var current=r.findByPlayer(target);
  require(current!=null&&current.id().equals(expected.id())
      &&current.playerOne().equals(expected.playerOne())&&current.playerTwo().equals(expected.playerTwo()),"relationship-request-stale");
  return current;
 }
 private void eligible(MarriageRepository r,PlayerSnapshot a,PlayerSnapshot b){
  require(a!=null&&b!=null,"offline");require(!a.id().equals(b.id()),"self-proposal");
  var first=currentIdentity(a);var second=currentIdentity(b);
  require(r.findByPlayer(a.id())==null&&r.findByPlayer(b.id())==null,"already-married");
  require(number(r,"cooldowns",a.id().toString())<=System.currentTimeMillis()&&number(r,"cooldowns",b.id().toString())<=System.currentTimeMillis(),"cooldown");
  long minimum=setting("marriage.minimum-online-minutes",30);
  if(first.onlineMinutes()<minimum||second.onlineMinutes()<minimum)throw new RuleViolation("minimum-online","minutes",minimum);
  require(r.get("blocks",b.id()+":"+a.id())==null,"blocked");
 }
 private void eligibleOfflineProposer(MarriageRepository r,UUID proposer,PlayerSnapshot target){
  require(proposer!=null&&target!=null,"offline");require(!proposer.equals(target.id()),"self-proposal");
  require(r.findByPlayer(proposer)==null&&r.findByPlayer(target.id())==null,"already-married");
  long now=System.currentTimeMillis();
  require(number(r,"cooldowns",proposer.toString())<=now&&number(r,"cooldowns",target.id().toString())<=now,"cooldown");
  long minimum=setting("marriage.minimum-online-minutes",30);
  if(target.onlineMinutes()<minimum)throw new RuleViolation("minimum-online","minutes",minimum);
  require(r.get("blocks",target.id()+":"+proposer)==null,"blocked");
 }
 public void propose(PlayerSnapshot a,PlayerSnapshot b,String mode){
  if(a==null||b==null)return;
  submitProposalRequest(a,List.of(a,b),r->{maintain(r,System.currentTimeMillis());require("NORMAL".equals(mode)||"WEDDING".equals(mode),"invalid-argument");eligible(r,a,b);
   // 无法解析不等于不存在；保留双方名下的损坏请求，避免覆盖待核对数据。
   require(r.get("proposals",a.id().toString())==null&&r.get("proposals",b.id().toString())==null,"request-pending");
   for(var e:r.entries("proposals").entrySet()){var p=readProposal(e.getKey(),e.getValue());if(p!=null)require(!p.proposer().equals(a.id())&&!p.target().equals(a.id())&&!p.target().equals(b.id())&&!p.proposer().equals(b.id()),"request-pending");}String key=today()+":"+a.id();long n=number(r,"daily",key);require(n<setting("marriage.proposal-daily-limit",3),"daily-limit");var request=new Proposal(UUID.randomUUID(),a.id(),b.id(),mode,System.currentTimeMillis()+setting("marriage.proposal-expire-seconds",300)*1000);r.put("proposals",b.id().toString(),gson.toJson(request));r.put("daily",key,Long.toString(n+1));return request;},q->{String label=messages.raw("mode-"+mode.toLowerCase(Locale.ROOT));notifyLive(a.liveId(),"proposal-sent","player",b.name(),"mode",label);notifyLive(b.liveId(),"proposal-received","player",a.name(),"mode",label);});}
 public void accept(PlayerSnapshot target){
  if(target==null)return;
  long requestedAt=System.nanoTime();
  submitProposalRequest(target,List.of(target),r->{
   maintain(r,System.currentTimeMillis());String key=target.id().toString(),raw=r.get("proposals",key);
   require(raw!=null,"request-missing");var q=readProposal(key,raw);require(q!=null,"request-missing");
    var first=directory.identity(q.proposer());
    if(first!=null){
     eligible(r,first,target);
     // 求婚方的退出清理可能排在接受之后；新登录不能复用接受提交前的旧同意。
     Long proposerSession=directory.session(first.liveId());
     require(proposerSession!=null&&proposerSession-requestedAt<=0,"marriage-request-stale");
    }else{
     // 求婚已持久化，求婚方可以在有效期内离线；仍在事务中重验关系、冷却、屏蔽和收件方在线时长。
     eligibleOfflineProposer(r,q.proposer(),target);
    }
   require(q.expiresAt()>System.currentTimeMillis(),"request-missing");boolean normal=q.type().equals("NORMAL");
   require(normal?r.createMarriage(q.proposer(),q.target(),q.type(),System.currentTimeMillis())
       :r.createEngagement(q.proposer(),q.target(),q.type(),System.currentTimeMillis()),"already-married");
   r.remove("proposals",key);return r.findByPlayer(target.id());
  },m->{if(m.married())announce(m);else{
   notifyIdentity(m.playerOne(),"engaged","hours",setting("marriage.engagement-hours",48));
   notifyIdentity(m.playerTwo(),"engaged","hours",setting("marriage.engagement-hours",48));
  }});
 }
 public void deny(PlayerSnapshot p){
  if(p==null)return;
  submitProposalRequest(p,List.of(p),r->{String key=p.id().toString(),raw=r.get("proposals",key);var proposal=raw==null?null:readProposal(key,raw);require(proposal!=null&&proposal.expiresAt()>System.currentTimeMillis(),"request-missing");r.remove("proposals",key);return null;},x->notifyLive(p.liveId(),"proposal-denied"));}
 public void cancel(PlayerSnapshot p){
  if(p==null)return;
  submitRelationshipRequest(p.liveId(),p,p.id(),false,(r,expected)->{
   var m=requireRelationship(r,p.id(),expected,"wedding-required");
   require(expected.state()==MarriageState.ENGAGED&&m.state()==MarriageState.ENGAGED,"wedding-required");
   r.deleteMarriage(p.id());r.remove("weddings",m.id());return m;
  },m->{notifyIdentity(m.playerOne(),"cancelled");notifyIdentity(m.playerTwo(),"cancelled");});
 }
 public void divorce(PlayerSnapshot p,boolean withdraw){
  if(p==null)return;
  submitRelationshipRequest(p.liveId(),p,p.id(),false,(r,expected)->{
   long now=System.currentTimeMillis();var m=requireRelationship(r,p.id(),expected,"married-required");
   MarriageState requiredState=withdraw?MarriageState.DIVORCE_PENDING:MarriageState.MARRIED;
   require(expected.state()==requiredState&&m.state()==requiredState,"married-required");
   // 撤回绑定原申请的到期时间；不能撤回后来重提的申请或已到期的冷静期。
   if(withdraw){require(m.divorceAt()==expected.divorceAt(),"relationship-request-stale");require(m.divorceAt()>now,"married-required");}
   maintain(r,now);
   require(withdraw?r.withdrawDivorce(p.id()):r.requestDivorce(p.id(),now+setting("marriage.divorce-cooling-hours",24)*3600000),"married-required");
   return r.findByPlayer(p.id());
  },m->{for(UUID id:List.of(m.playerOne(),m.playerTwo()))notifyIdentity(id,withdraw?"divorce-withdrawn":"divorce-requested","hours",setting("marriage.divorce-cooling-hours",24));});
 }
 public void block(PlayerSnapshot p,UUID target,boolean blocked){
  if(p==null)return;
  if(target==null){notifyLive(p.liveId(),"invalid-argument");return;}
  submitProposalRequest(p,List.of(p),r->{
   String key=p.id()+":"+target;if(blocked)r.put("blocks",key,"1");else r.remove("blocks",key);return null;
  },x->notifyLive(p.liveId(),"block-set"));
 }
 public void completeWedding(UUID one,UUID two){var m=view.byPlayer().get(one);if(m!=null&&m.partnerOf(one).equals(two))completeWedding(m,config.snapshot().generation(),database.generation());}
 /** 完成婚礼必须绑定开始仪式时的关系、配置代次和数据库代次，避免旧回调完成新订婚。 */
 public void completeWedding(MarriageRecord expected,UUID expectedConfig,UUID expectedDatabase){
  submitAtGeneration(null,expectedDatabase,r->{
   require(expectedConfig.equals(config.snapshot().generation()),"wedding-required");
   var m=r.findByPlayer(expected.playerOne());
   require(m!=null&&m.id().equals(expected.id())&&m.state()==MarriageState.ENGAGED
       &&m.type().equals("WEDDING")&&m.partnerOf(expected.playerOne()).equals(expected.playerTwo())
       &&m.createdAt()+setting("marriage.engagement-hours",48)*3600000>System.currentTimeMillis(),"wedding-required");
   require(r.completeMarriage(expected.playerOne(),expected.playerTwo(),"WEDDING",System.currentTimeMillis()),"wedding-required");
   return r.findByPlayer(expected.playerOne());
  },this::announce,error->{notifyIdentity(expected.playerOne(),"wedding-completion-failed");notifyIdentity(expected.playerTwo(),"wedding-completion-failed");});
 }
 private void announce(MarriageRecord m){broadcast("married","player1",name(m.playerOne()),"player2",name(m.playerTwo()));}
 public void info(CommandSender sender,UUID id){var m=view.byPlayer().get(id);if(m==null){messages.send(sender,"not-married");return;}int lvl=level(m.bond());var bonus=BondAttributeService.calculate(lvl,config.config().getBoolean("bond.attributes.enabled",true)&&m.state()==MarriageState.MARRIED,config.config().getDouble("bond.attributes.per-level.max-health",1.0),config.config().getDouble("bond.attributes.per-level.attack-damage",0.25),config.config().getDouble("bond.attributes.per-level.movement-speed",0.005));messages.send(sender,"info","player1",name(m.playerOne()),"player2",name(m.playerTwo()),"state",messages.raw("state-"+m.state().name().toLowerCase(Locale.ROOT)),"level",lvl,"title",title(lvl),"bond",m.bond(),"days",m.married()?Math.max(0,(System.currentTimeMillis()-m.marriedAt())/86400000):0,"hours",m.sharedSeconds()/3600,"max-health",formatBonus(bonus.maxHealth()),"attack-damage",formatBonus(bonus.attackDamage()),"movement-speed",formatBonus(bonus.movementSpeed()));}
 private String formatBonus(double value){return String.format(Locale.ROOT,"%.3f",value);} public UUID identityByName(String value){var online=directory.name(value);if(online!=null)return online.id();return view.profiles().values().stream().filter(p->p.name().equalsIgnoreCase(value)).map(PlayerProfile::id).findFirst().orElse(null);}
 public void admin(UUID actor,String action,UUID a,UUID b,long amount){
  var administrator=actor==null?null:directory.live(actor);
  if(actor!=null&&administrator==null){notifyAdministrator(actor,"offline");return;}
  submitRelationshipRequest(actor,administrator,a,true,(r,expected)->{
   require(action!=null&&a!=null,"invalid-argument");
   switch(action){
    case "force"->{require(b!=null,"invalid-argument");require(r.createMarriage(a,b,"ADMIN",System.currentTimeMillis()),"already-married");}
    case "divorce","clear"->end(r,requireRelationship(r,a,expected,"not-married"),System.currentTimeMillis());
    case "setexp"->{require(amount>=0,"invalid-argument");requireRelationship(r,a,expected,"not-married");require(r.setBond(a,amount),"not-married");}
    case "setlevel"->{require(amount>=1&&amount<=10,"invalid-argument");requireRelationship(r,a,expected,"not-married");require(r.setBond(a,config.config().getLong("bond.levels."+amount+".required")),"not-married");}
    default->throw new RuleViolation("invalid-argument");
   }return null;
  },x->notifyAdministrator(actor,"admin-success"));
 }
 private void notifyAdministrator(UUID actor,String key){if(closed)return;if(actor!=null)notifyLive(actor,key);else scheduler.runGlobal(()->{if(!closed)messages.send(Bukkit.getConsoleSender(),key);});}
 private void end(MarriageRepository r,MarriageRecord m,long now){long until=now+setting("marriage.remarriage-cooling-hours",24)*3600000;r.put("cooldowns",m.playerOne().toString(),Long.toString(until));r.put("cooldowns",m.playerTwo().toString(),Long.toString(until));r.deleteMarriage(m.playerOne());r.remove("weddings",m.id());}
 private void maintain(MarriageRepository r,long now){for(var m:r.findAll()){if(m.state()==MarriageState.ENGAGED&&m.createdAt()+setting("marriage.engagement-hours",48)*3600000<=now){r.deleteMarriage(m.playerOne());r.remove("weddings",m.id());}else if(m.state()==MarriageState.DIVORCE_PENDING&&m.divorceAt()<=now)end(r,m,m.divorceAt());}for(var e:r.entries("proposals").entrySet()){var p=readProposal(e.getKey(),e.getValue());if(p!=null&&p.expiresAt()<=now)r.remove("proposals",e.getKey());}for(var e:r.entries("cooldowns").entrySet()){Long expiry=readLong("cooldowns",e.getKey(),e.getValue());if(expiry!=null&&expiry<=now)r.remove("cooldowns",e.getKey());}String cutoff=today().minusDays(7).toString();for(String k:r.entries("daily").keySet())if(k.compareTo(cutoff)<0)r.remove("daily",k);}
 public static long number(MarriageRepository r,String bucket,String key){String v=r.get(bucket,key);if(v==null)return 0;try{return Long.parseLong(v);}catch(NumberFormatException invalid){return Long.MAX_VALUE;}}
 /**
  * 退出只撤销玩家操作门闩，不删除求婚记录。求婚有效期是持久化状态，
  * 这样收件方可以在求婚方离线时接受；过期记录由 maintain 统一清理。
  */
 public void leave(UUID live,UUID id){busy.remove(live);}
 private Proposal readProposal(String key,String raw){try{Proposal p=gson.fromJson(raw,Proposal.class);if(p==null||p.id()==null||p.proposer()==null||p.target()==null||p.type()==null||p.type().isBlank()||p.expiresAt()<=0
   ||!p.target().toString().equalsIgnoreCase(key)||p.proposer().equals(p.target())||!Set.of("NORMAL","WEDDING").contains(p.type()))throw new IllegalArgumentException("求婚记录字段不一致");return p;}catch(RuntimeException invalid){warnMetadata("proposals",key);return null;}}
 private Long readLong(String bucket,String key,String raw){try{return Long.valueOf(raw);}catch(RuntimeException invalid){warnMetadata(bucket,key);return null;}}
 void warnMetadata(String bucket,String key){if(!warnedMetadata.add(bucket+":"+key))return;var logger=plugin==null?null:plugin.getLogger();if(logger!=null)logger.warning("结婚系统检测到损坏的"+bucket+"元数据，已跳过："+key);}
 public void reload(UUID actor){
  if(closed)return;
  if(!io.submit(()->{
   if(closed)return;
   var previous=config.snapshot();ConfigurationManager.Snapshot next;DatabaseManager.SwitchResult<View> result;
   try{
    next=config.prepare();
    result=database.switchTo(next.database(),r->{var candidate=loadView(r);if(closed)throw new IllegalStateException("插件已关闭");return candidate;});
   }catch(Exception error){
    plugin.getLogger().log(java.util.logging.Level.SEVERE,"重载校验失败，继续使用原配置和数据库",error);
    notifyAdministrator(actor,"reload-failed");return;
   }
   // IO 串行队列内完成数据库校验；这里只发布内存快照，不持有锁执行 IO 或跨区调度。
   synchronized(lifecycle){if(closed)return;config.publish(next);view=result.value();}
   if(result.switched())notifyAdministrator(actor,"database-switched");
   if(!Objects.equals(previous.files().get("config.yml").getString("identity.mode"),next.files().get("config.yml").getString("identity.mode")))broadcast("identity-changed");
   try{directory.refresh();afterReload.run();}
   catch(RuntimeException error){
    // 发布后新请求可能已开始，不能重新打开旧库冒充原代次，否则会破坏领取令牌。
    plugin.getLogger().log(java.util.logging.Level.SEVERE,"配置和数据库已发布，但运行状态刷新失败；请检查异常并重启",error);
    notifyAdministrator(actor,"reload-refresh-failed");return;
   }
   notifyAdministrator(actor,"reload-success");
  }))notifyAdministrator(actor,"busy");
 }
 public void shutdown(){synchronized(lifecycle){closed=true;ready=false;busy.clear();view=new View(Map.of(),Map.of(),List.of(),Map.of());afterReload=()->{};}if(timer!=null)timer.cancel();}
}






