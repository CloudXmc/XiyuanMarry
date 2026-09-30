package cn.mcxyd.xiyuanmarry.model;
import java.util.UUID;
public record PlayerSnapshot(UUID liveId,UUID id,String identityKey,String name,long onlineMinutes,Point point,long seenAt){
 public record Point(UUID world,double x,double y,double z,float yaw,float pitch){public boolean near(Point p,double distance){if(p==null||world==null||!world.equals(p.world))return false;double a=x-p.x,b=y-p.y,c=z-p.z;return a*a+b*b+c*c<=distance*distance;}}
}

