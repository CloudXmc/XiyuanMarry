package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.model.*;import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class CoupleTaskServiceTest{
 private PlayerSnapshot.Point p(double x,double z){return new PlayerSnapshot.Point(UUID.randomUUID(),x,64,z,0,0);}
 @Test void requiresSameWorldAndDistance(){var a=p(0,0);var b=new PlayerSnapshot.Point(a.world(),40,64,0,0,0);var state=new CoupleTaskState("c",1,"PLACE_BLOCK",0,2,false,0);assertEquals(1,CoupleTaskService.apply(state,new CoupleTaskService.Event("PLACE_BLOCK","*",1),a,b,50,20).state().progress());assertEquals(0,CoupleTaskService.apply(state,new CoupleTaskService.Event("PLACE_BLOCK","*",1),a,p(60,0),50,20).state().progress());}
 @Test void completedRewardIsOneShot(){var a=p(0,0);var b=new PlayerSnapshot.Point(a.world(),1,64,1,0,0);var state=new CoupleTaskState("c",1,"PLACE_BLOCK",0,1,false,0);var first=CoupleTaskService.apply(state,new CoupleTaskService.Event("PLACE_BLOCK","*",1),a,b,50,20);assertEquals(20,first.bondReward());assertEquals(0,CoupleTaskService.apply(first.state(),new CoupleTaskService.Event("PLACE_BLOCK","*",1),a,b,50,20).bondReward());}
}

