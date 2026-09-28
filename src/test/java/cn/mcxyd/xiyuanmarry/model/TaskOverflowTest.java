package cn.mcxyd.xiyuanmarry.model;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TaskOverflowTest {
    @Test void veryLargeIncrementCapsWithoutOverflow() {
        var state = new CoupleTaskState("couple", 1, "PLACE_BLOCK", 2, 10, false, 0);
        assertEquals(10, state.add(Long.MAX_VALUE).progress());
    }
}
