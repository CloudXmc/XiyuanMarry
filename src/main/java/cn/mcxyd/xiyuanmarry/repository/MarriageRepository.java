package cn.mcxyd.xiyuanmarry.repository;
import cn.mcxyd.xiyuanmarry.model.*;
import java.util.*;
import java.util.function.Function;
public interface MarriageRepository extends AutoCloseable {
 MarriageRecord findByPlayer(UUID player);List<MarriageRecord> findAll();
 boolean createEngagement(UUID one,UUID two,String type,long at);
 boolean createMarriage(UUID one,UUID two,String type,long at);
 boolean completeMarriage(UUID one,UUID two,String type,long at);
 boolean requestDivorce(UUID player,long at);boolean withdrawDivorce(UUID player);
 boolean deleteMarriage(UUID player);boolean addBond(UUID player,long amount);boolean setBond(UUID player,long amount);boolean addOnline(UUID player,long seconds);
 int expireEngagements(long cutoff);int completeDueDivorces(long now);
 String get(String bucket,String key);Map<String,String> entries(String bucket);void put(String bucket,String key,String value);void remove(String bucket,String key);
 <T> T transaction(Function<MarriageRepository,T> body);
 @Override void close();
}


