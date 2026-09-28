package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import org.bukkit.event.*;import org.bukkit.event.player.PlayerQuitEvent;
import java.util.*;import java.util.concurrent.TimeUnit;
/** 同服同时在线即可累计；第一次采样只建立基线，不推测登录前的时间。 */
public final class SharedOnlineService implements Listener,AutoCloseable {
    private record Window(UUID actor,String relationship,long start,long end){}
    private final MarriageService marriages;private final ConfigurationManager config;
    private final SharedOnlineLedger ledger=new SharedOnlineLedger();
    private final SharedOnlineClock clock=new SharedOnlineClock();private final TaskHandle timer;
    private volatile boolean closed;
    public SharedOnlineService(MarriageService m,ConfigurationManager c,UnifiedScheduler s){
        marriages=m;config=c;timer=s.runRepeatingAsync(this::sample,2,2,TimeUnit.SECONDS);
    }
    private void sample(){
        if(closed)return;var settings=config.snapshot();long nowMillis=System.currentTimeMillis(),now=nowMillis/1000;
        if(!settings.files().get("config.yml").getBoolean("bond.common-online-enabled",true)){clock.clear();return;}
        var online=new HashMap<UUID,PlayerSnapshot>();
        for(var player:marriages.directory().all())if(nowMillis-player.seenAt()<=2500)online.put(player.id(),player);
        var windows=new ArrayList<Window>();var active=new HashSet<String>();
        for(var marriage:marriages.view().couples()){
            var one=online.get(marriage.playerOne());var two=online.get(marriage.playerTwo());
            if(one==null||two==null||!marriage.married()||marriage.state()==MarriageState.DIVORCE_PENDING&&marriage.divorceAt()<=nowMillis)continue;
            active.add(marriage.id());var interval=clock.sample(marriage.id(),one.liveId(),two.liveId(),now);
            if(interval!=null)windows.add(new Window(one.id(),marriage.id(),interval.start(),interval.end()));
        }
        clock.retain(active);if(windows.isEmpty())return;
        var options=settings.files().get("config.yml");long hourly=options.getLong("bond.common-online-per-hour",10),daily=options.getLong("bond.first-together-login",8);
        String date=java.time.Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneId.of(options.getString("timezone","Asia/Shanghai"))).toLocalDate().toString();
        // 一个采样批次只提交一次队列；配置切换后丢弃旧代次窗口。
        marriages.submit(null,r->{
            if(closed||!settings.generation().equals(config.snapshot().generation()))return null;
            for(var w:windows)ledger.record(r,w.actor(),w.relationship(),w.start(),w.end(),date,hourly,daily);
            return null;
        },result->{});
    }
    @EventHandler public void quit(PlayerQuitEvent event){clock.forget(event.getPlayer().getUniqueId());}
    public void reload(){clock.clear();}
    @Override public void close(){closed=true;timer.cancel();clock.clear();}
}
