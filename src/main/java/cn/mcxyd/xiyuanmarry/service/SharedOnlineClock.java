package cn.mcxyd.xiyuanmarry.service;
import java.util.*;
/** 只保存当前在线关系的值；长暂停、退服及reload都会断开计时窗口。 */
public final class SharedOnlineClock {
    public record Interval(long start,long end){}
    private record Sample(UUID one,UUID two,long at){}
    private final Map<String,Sample> previous=new HashMap<>();
    public synchronized Interval sample(String relation,UUID one,UUID two,long now){
        var old=previous.put(relation,new Sample(one,two,now));
        if(old==null||!old.one().equals(one)||!old.two().equals(two)||now<=old.at()||now-old.at()>5)return null;
        return new Interval(old.at(),now);
    }
    public synchronized void retain(Set<String> active){previous.keySet().retainAll(active);}
    public synchronized void forget(UUID player){previous.values().removeIf(s->s.one().equals(player)||s.two().equals(player));}
    public synchronized void clear(){previous.clear();}
}
