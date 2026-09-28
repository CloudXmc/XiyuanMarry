package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.model.MarriageRecord;
import cn.mcxyd.xiyuanmarry.model.MarriageState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingPolicyTest {
    private final MarriageRecord married = new MarriageRecord(UUID.randomUUID(), UUID.randomUUID(),
            MarriageState.MARRIED, "NORMAL", 0, 0, 0, "pair", 1, 0, 0);
    private final MarriageRecord pending = new MarriageRecord(UUID.randomUUID(), UUID.randomUUID(),
            MarriageState.DIVORCE_PENDING, "NORMAL", 0, Long.MAX_VALUE, 0, "pair2", 1, 0, 0);

    @Test void onlyNearbyMarriageRingCanActivate() {
        assertTrue(RingPolicy.active(true, true, married, true, 9, 20));
        assertFalse(RingPolicy.active(true, false, married, true, 9, 20));
        assertFalse(RingPolicy.active(true, true, married, false, 9, 20));
        assertFalse(RingPolicy.active(true, true, married, true, 401, 20));
    }

    @Test void toggleAndExpiredStatesAreSafe() {
        assertFalse(RingPolicy.active(false, true, married, true, 0, 20));
        assertTrue(RingPolicy.active(true, true, pending, true, 0, 20));
        var expired = new MarriageRecord(pending.playerOne(), pending.playerTwo(), MarriageState.DIVORCE_PENDING,
                pending.type(), pending.createdAt(), 1, 0, pending.id(), pending.marriedAt(), 0, 0);
        assertTrue(expired.married());
    }
}
