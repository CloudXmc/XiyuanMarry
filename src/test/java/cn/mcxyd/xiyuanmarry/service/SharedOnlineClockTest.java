package cn.mcxyd.xiyuanmarry.service;
import org.junit.jupiter.api.Test;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SharedOnlineClockTest {
    @Test void firstSampleLongPauseAndLogoutDoNotInventTime(){
        var c=new SharedOnlineClock();UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        assertNull(c.sample("r",a,b,100));assertEquals(new SharedOnlineClock.Interval(100,102),c.sample("r",a,b,102));
        assertNull(c.sample("r",a,b,120));c.forget(a);assertNull(c.sample("r",a,b,122));
        c.clear();assertNull(c.sample("r",a,b,123));
    }
    @Test void absentCoupleAndReversedClockResetBaseline(){
        var c=new SharedOnlineClock();UUID a=UUID.randomUUID(),b=UUID.randomUUID();c.sample("r",a,b,100);
        c.retain(Set.of());assertNull(c.sample("r",a,b,102));assertNull(c.sample("r",a,b,99));
    }
}
