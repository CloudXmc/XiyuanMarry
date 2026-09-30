package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.model.*;import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;
/** 任务计数只接收不可变玩家快照和事件值，监听器不直接做数据库或奖励 IO。 */
public final class CoupleTaskService {
 public record Event(String type,String value,long amount){}
 public record Result(CoupleTaskState state,boolean newlyCompleted,long bondReward){ }
 public static final List<String> TYPES=List.of("KILL_MONSTER","KILL_ANIMAL","KILL_SPECIFIC","PLACE_BLOCK","BREAK_BLOCK","EAT","ENCHANT","SMELT","CRAFT","ANVIL","FISH","BREW","TRADE","TAME","SLEEP","ELYTRA","BIOME","EXPERIENCE");
 public static boolean partnersNear(PlayerSnapshot.Point a,PlayerSnapshot.Point b,double distance){return a!=null&&b!=null&&a.near(b,distance);}
 public static Result apply(CoupleTaskState current,Event event,PlayerSnapshot.Point first,PlayerSnapshot.Point second,double distance,long reward){
  if(!current.taskType().equals(event.type())||!partnersNear(first,second,distance)||event.amount()<=0)return new Result(current,false,0);
  boolean before=current.completed();var next=current.add(event.amount());return new Result(next,!before&&next.completed(),!before&&next.completed()?reward:0);
 }
 /** 兼容旧调用的默认时区版本；结婚当天为第1天，按自然日切换。 */
 public static int day(long marriedAt,long now){return day(marriedAt,now,ZoneId.of("Asia/Shanghai"));}
 public static int day(long marriedAt,long now,ZoneId zone){
  if(zone==null)return 1;
  long serial=DailyTaskLedger.daySerial(marriedAt,now,zone);
  return (int)Math.floorMod(serial,30)+1;
 }
 public static String cycleKey(String coupleId,int day){return coupleId+":"+day;}
}

