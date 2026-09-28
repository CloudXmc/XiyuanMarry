package cn.mcxyd.xiyuanmarry.model;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MarriageStateTest {
    @Test void statesContainFullFlow() {
        assertEquals(MarriageState.SINGLE, MarriageState.valueOf("SINGLE"));
        assertEquals(MarriageState.ENGAGED, MarriageState.valueOf("ENGAGED"));
        assertEquals(MarriageState.MARRIED, MarriageState.valueOf("MARRIED"));
        assertEquals(MarriageState.DIVORCE_PENDING, MarriageState.valueOf("DIVORCE_PENDING"));
    }
}
