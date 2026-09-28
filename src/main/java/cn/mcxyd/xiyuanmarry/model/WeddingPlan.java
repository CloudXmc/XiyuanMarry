package cn.mcxyd.xiyuanmarry.model;
import java.util.*;
public record WeddingPlan(Map<String,PlayerSnapshot.Point> points,Map<UUID,Invite> invites){
 public record Invite(boolean accepted,long expires){}
 public WeddingPlan{points=Map.copyOf(points);invites=Map.copyOf(invites);}
 public static WeddingPlan empty(){return new WeddingPlan(Map.of(),Map.of());}
 public WeddingPlan point(String role,PlayerSnapshot.Point point){var copy=new HashMap<>(points);copy.put(role,point);return new WeddingPlan(copy,invites);}
 public WeddingPlan invite(UUID who,Invite invite){var copy=new HashMap<>(invites);copy.put(who,invite);return new WeddingPlan(points,copy);}
}

