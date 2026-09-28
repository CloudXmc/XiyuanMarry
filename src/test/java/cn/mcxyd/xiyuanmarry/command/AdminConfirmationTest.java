package cn.mcxyd.xiyuanmarry.command;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class AdminConfirmationTest {
    @Test void destructiveCommandsRequireExplicitFinalConfirmation(){
        assertFalse(AdminConfirmation.accepted("force",new String[]{"a","b"}));
        assertTrue(AdminConfirmation.accepted("force",new String[]{"a","b","confirm"}));
        assertFalse(AdminConfirmation.accepted("clear",new String[]{"a"}));
        assertTrue(AdminConfirmation.accepted("divorce",new String[]{"a","confirm"}));
    }
}
