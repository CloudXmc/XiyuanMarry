package cn.mcxyd.xiyuanmarry.service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** 鞘翅只计原版飞行统计增量，传送位置差不作为飞行距离。 */
public final class ExplorationTracker {
    public record Progress(long metres,String biome){}
    private record Sample(int centimetres,boolean gliding,UUID world,String biome,long at,long remainder,boolean eligible){}
    private final Map<UUID,Sample> samples=new ConcurrentHashMap<>();
    public Progress sample(UUID player,int centimetres,boolean gliding,UUID world,String biome,long now,boolean eligible){
        var old=samples.get(player);long metres=0,remainder=0;String entered=null;
        if(old!=null&&now>old.at()&&now-old.at()<=3000){
            if(eligible&&biome!=null&&old.biome()!=null&&(!biome.equals(old.biome())||!world.equals(old.world())))entered=biome;
            long delta=(long)centimetres-old.centimetres();
            if(eligible&&old.eligible()&&gliding&&old.gliding()&&world.equals(old.world())&&delta>=0&&delta<=300000){
                long value=delta+old.remainder();metres=value/100;remainder=value%100;
            }
        }
        samples.put(player,new Sample(centimetres,gliding,world,biome,now,remainder,eligible));
        return new Progress(metres,entered);
    }
    public void forget(UUID player){samples.remove(player);}
    public void clear(){samples.clear();}
}
