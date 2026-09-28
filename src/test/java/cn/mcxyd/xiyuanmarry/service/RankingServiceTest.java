package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RankingServiceTest {
    MarriageRecord m(String id,long at,long bond,long total,long online) {return new MarriageRecord(UUID.randomUUID(),UUID.randomUUID(),MarriageState.MARRIED,"NORMAL",at,0,bond,id,at,total,online);}
    @Test void durationUsesWholeDaysThenBondLevel() {
        var low=m("low",1000,20,20,0); var high=m("high",2000,500,500,0);
        assertEquals("high",new RankingService().rank(List.of(low,high),"duration",86403000L,e->e>=100?2:1,10).getFirst().id());
    }
    @Test void eachBoardUsesItsDocumentedMetric() {
        var a=m("a",1000,200,300,20); var b=m("b",2000,100,700,40);
        var ranking=new RankingService();
        assertEquals("a",ranking.rank(List.of(b,a),"bond",90000000,e->1,1).getFirst().id());
        assertEquals("b",ranking.rank(List.of(a,b),"online",90000000,e->1,1).getFirst().id());
        assertEquals("b",ranking.rank(List.of(a,b),"total",90000000,e->1,1).getFirst().id());
    }
    @Test void rejectsUnknownBoardAndExpiredDivorce() {
        var ended=new MarriageRecord(UUID.randomUUID(),UUID.randomUUID(),MarriageState.DIVORCE_PENDING,"NORMAL",1,2,0,"x",1,0,0);
        assertTrue(new RankingService().rank(List.of(ended),"bond",3,e->1,10).isEmpty());
        assertThrows(IllegalArgumentException.class,()->new RankingService().rank(List.of(),"wrong",3,e->1,10));
    }
}
