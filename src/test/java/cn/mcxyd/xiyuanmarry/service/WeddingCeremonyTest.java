package cn.mcxyd.xiyuanmarry.service;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WeddingCeremonyTest {
 @Test void needsTwoDistinctConsents(){var s=new WeddingCeremony();var a=UUID.randomUUID();var b=UUID.randomUUID();s.start(a,b,100);assertEquals(WeddingCeremony.Result.WAITING,s.confirm(a,10));assertEquals(WeddingCeremony.Result.WAITING,s.confirm(a,11));assertEquals(WeddingCeremony.Result.COMPLETE,s.confirm(b,12));assertEquals(WeddingCeremony.Result.ABSENT,s.confirm(b,13));}
 @Test void rejectsExpiredOath(){var s=new WeddingCeremony();var a=UUID.randomUUID();s.start(a,UUID.randomUUID(),100);assertEquals(WeddingCeremony.Result.ABSENT,s.confirm(a,100));}
 @Test void cancellationClearsBoth(){var s=new WeddingCeremony();var a=UUID.randomUUID();var b=UUID.randomUUID();s.start(a,b,100);s.cancel(a);assertFalse(s.contains(b,10));}
}

