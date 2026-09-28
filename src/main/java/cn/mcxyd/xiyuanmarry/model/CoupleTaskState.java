package cn.mcxyd.xiyuanmarry.model;
import java.util.*;
public record CoupleTaskState(String coupleId,int cycleDay,String taskType,long progress,long target,boolean completed,long updatedAt){
 public CoupleTaskState{if(cycleDay<1||cycleDay>30||progress<0||target<1)throw new IllegalArgumentException();}
 public CoupleTaskState add(long amount){if(amount<0)throw new IllegalArgumentException();long next=amount>=target-progress?target:progress+amount;return new CoupleTaskState(coupleId,cycleDay,taskType,next,target,completed||next>=target,System.currentTimeMillis());}
 public CoupleTaskState reset(int day,String type,long nextTarget){return new CoupleTaskState(coupleId,day,type,0,nextTarget,false,System.currentTimeMillis());}
}

