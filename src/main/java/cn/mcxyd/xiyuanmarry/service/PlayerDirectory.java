package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;import cn.mcxyd.xiyuanmarry.model.*;import cn.mcxyd.xiyuanmarry.scheduler.*;import org.bukkit.entity.Player;import org.bukkit.Statistic;import java.util.*;import java.util.concurrent.*;import java.util.function.Consumer;
/** 只在玩家所有者上下文采集，其他线程只读取带时间戳的不可变快照。 */
public final class PlayerDirectory implements AutoCloseable {
 private final ConfigurationManager config;private final UnifiedScheduler scheduler;private final Map<UUID,PlayerSnapshot> online=new ConcurrentHashMap<>();private final Map<UUID,TaskHandle> updates=new ConcurrentHashMap<>();private final Map<UUID,Long> sessions=new ConcurrentHashMap<>();private volatile Consumer<PlayerSnapshot> onJoin=s->{};private volatile boolean closed;
 public PlayerDirectory(ConfigurationManager c,UnifiedScheduler s){config=c;scheduler=s;}public void onJoin(Consumer<PlayerSnapshot> handler){onJoin=handler;}
 public void bootstrap(){if(closed)return; for(Player p:org.bukkit.Bukkit.getOnlinePlayers()){UUID id=p.getUniqueId();scheduler.player(id,this::join);}}
 public PlayerSnapshot capture(Player p){if(closed)return null;var c=config.config();UUID live=p.getUniqueId();String name=p.getName(),key=IdentityResolver.key(c.getString("identity.mode"),c.getBoolean("identity.offline-name-ignore-case",true),live,name);var l=p.getLocation();var snapshot=new PlayerSnapshot(live,IdentityResolver.storageId(key,live),key,name,p.getStatistic(Statistic.PLAY_ONE_MINUTE)/1200L,new PlayerSnapshot.Point(l.getWorld().getUID(),l.getX(),l.getY(),l.getZ(),l.getYaw(),l.getPitch()),System.currentTimeMillis());online.put(live,snapshot);return snapshot;}
 public void join(Player p){if(closed)return;var snap=capture(p);if(snap==null)return;long session=System.nanoTime();sessions.put(snap.liveId(),session);TaskHandle previous=updates.remove(snap.liveId());if(previous!=null)previous.cancel();onJoin.accept(snap);updates.put(snap.liveId(),scheduler.repeatEntity(p,()->{if(!closed&&Objects.equals(sessions.get(snap.liveId()),session))capture(p);},40));}
 public void leave(UUID id){sessions.remove(id);online.remove(id);var t=updates.remove(id);if(t!=null)t.cancel();}
 public PlayerSnapshot live(UUID id){return fresh(online.get(id));}public PlayerSnapshot identity(UUID id){return online.values().stream().filter(s->s.id().equals(id)&&fresh(s)!=null).findFirst().orElse(null);}
 public PlayerSnapshot name(String name){return online.values().stream().filter(s->s.name().equalsIgnoreCase(name)&&fresh(s)!=null).findFirst().orElse(null);}
 private PlayerSnapshot fresh(PlayerSnapshot s){return s!=null&&System.currentTimeMillis()-s.seenAt()<10000?s:null;}
 public List<PlayerSnapshot> all(){return online.values().stream().filter(s->fresh(s)!=null).sorted(Comparator.comparing(PlayerSnapshot::name)).toList();}
 public void refresh(){for(UUID id:List.copyOf(online.keySet()))scheduler.player(id,this::join);}
 @Override public void close(){closed=true;sessions.clear();updates.values().forEach(TaskHandle::cancel);updates.clear();online.clear();}
}

