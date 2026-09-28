package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.model.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WeddingGiftPolicyTest {
    private final UUID one = UUID.randomUUID();
    private final UUID two = UUID.randomUUID();
    private final UUID guest = UUID.randomUUID();
    private final long now = 10_000;

    @Test void onlyAcceptedUnexpiredWeddingInviteCanSend() {
        var engagement = new MarriageRecord(one, two, MarriageState.ENGAGED, "WEDDING", 1, 0, 0, "relation", 0, 0, 0);
        var accepted = new WeddingPlan(Map.of(), Map.of(guest, new WeddingPlan.Invite(true, now + 1)));
        assertTrue(WeddingGiftPolicy.canSend(accepted, guest, engagement, now));
        var denied = new WeddingPlan(Map.of(), Map.of(guest, new WeddingPlan.Invite(false, now + 1)));
        var expired = new WeddingPlan(Map.of(), Map.of(guest, new WeddingPlan.Invite(true, now)));
        assertFalse(WeddingGiftPolicy.canSend(denied, guest, engagement, now));
        assertFalse(WeddingGiftPolicy.canSend(expired, guest, engagement, now));
        assertFalse(WeddingGiftPolicy.canSend(accepted, one, engagement, now));
    }

    @Test void onlyCurrentMarriedCoupleCanClaimWeddingGiftOnceRelationshipMatches() {
        UUID wedding = UUID.randomUUID();
        var married = new MarriageRecord(one, two, MarriageState.MARRIED, "WEDDING", 1, 0, 0, wedding.toString(), 2, 0, 0);
        var divorced = new MarriageRecord(one, two, MarriageState.DIVORCE_PENDING, "WEDDING", 1, now + 1, 0, wedding.toString(), 2, 0, 0);
        assertTrue(WeddingGiftPolicy.canClaim(wedding, one, married));
        assertTrue(WeddingGiftPolicy.canClaim(wedding, two, married));
        assertFalse(WeddingGiftPolicy.canClaim(wedding, guest, married));
        assertFalse(WeddingGiftPolicy.canClaim(wedding, one, divorced));
        assertFalse(WeddingGiftPolicy.canClaim(UUID.randomUUID(), one, married));
    }
}
