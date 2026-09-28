package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.model.MarriageRecord;
import java.util.*;
import java.util.function.LongToIntFunction;
public final class RankingService {
    public static final List<String> BOARDS=List.of("bond","duration","online","total");
    public List<MarriageRecord> rank(Collection<MarriageRecord> couples,String board,long now,LongToIntFunction level,int limit) {
        if (!BOARDS.contains(board) || limit < 1 || limit > 1000) throw new IllegalArgumentException("榜单或数量无效");
        Comparator<MarriageRecord> levels=Comparator.comparingInt((MarriageRecord m)->level.applyAsInt(m.bond())).reversed();
        Comparator<MarriageRecord> order=switch(board) {
            case "bond" -> levels.thenComparing(Comparator.comparingLong(MarriageRecord::bond).reversed());
            case "duration" -> Comparator.comparingLong((MarriageRecord m)->days(m,now)).reversed().thenComparing(levels);
            case "online" -> Comparator.comparingLong(MarriageRecord::sharedSeconds).reversed().thenComparing(levels);
            case "total" -> Comparator.comparingLong(MarriageRecord::totalBond).reversed()
                .thenComparing(Comparator.comparingLong((MarriageRecord m)->days(m,now)).reversed());
            default -> throw new IllegalArgumentException("未知榜单");
        };
        return couples.stream().filter(MarriageRecord::married)
            .filter(m->m.state()!=cn.mcxyd.xiyuanmarry.model.MarriageState.DIVORCE_PENDING || m.divorceAt()>now)
            .sorted(order.thenComparing(MarriageRecord::id)).limit(limit).toList();
    }
    public static long days(MarriageRecord marriage,long now) {return Math.max(0,(now-marriage.marriedAt())/86400000L);}
}
