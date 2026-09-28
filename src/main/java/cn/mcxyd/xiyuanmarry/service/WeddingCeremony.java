package cn.mcxyd.xiyuanmarry.service;
import java.util.*;
/** 锁内只修改内存状态，不访问实体，也不等待 IO。 */
public final class WeddingCeremony {
 public enum Result { ABSENT, WAITING, COMPLETE }
 private record Session(UUID one,UUID two,long deadline,Set<UUID> vows){}
 private final Map<UUID,Session> sessions=new HashMap<>();
 public synchronized void start(UUID one,UUID two,long deadline){if(one.equals(two))throw new IllegalArgumentException();cancel(one);cancel(two);var s=new Session(one,two,deadline,new HashSet<>());sessions.put(one,s);sessions.put(two,s);}
 public synchronized Result confirm(UUID who,long now){var s=sessions.get(who);if(s==null)return Result.ABSENT;if(now>=s.deadline()){cancel(who);return Result.ABSENT;}s.vows().add(who);if(s.vows().size()<2)return Result.WAITING;cancel(who);return Result.COMPLETE;}
 public synchronized void cancel(UUID who){var s=sessions.remove(who);if(s!=null){sessions.remove(s.one());sessions.remove(s.two());}}
 public synchronized boolean contains(UUID who,long now){var s=sessions.get(who);if(s!=null&&now>=s.deadline()){cancel(who);return false;}return s!=null;}
 public synchronized void expire(long now){for(var id:new ArrayList<>(sessions.keySet()))contains(id,now);}
 public synchronized void clear(){sessions.clear();}
}

