package cn.mcxyd.xiyuanmarry.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PartnerInteractionPolicyTest {
    @Test void trimsAndBoundsPrivateMessages() {
        assertEquals("你好", PartnerInteractionPolicy.validateChat("  你好  ", 20));
        assertNull(PartnerInteractionPolicy.validateChat(" ", 20));
        assertNull(PartnerInteractionPolicy.validateChat("123456", 5));
    }
    @Test void cooldownUsesInclusiveExpiry() {
        assertTrue(PartnerInteractionPolicy.cooldownReady(100, 100));
        assertFalse(PartnerInteractionPolicy.cooldownReady(101, 100));
    }
}
