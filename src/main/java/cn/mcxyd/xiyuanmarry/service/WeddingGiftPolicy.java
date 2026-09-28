package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.model.MarriageRecord;
import cn.mcxyd.xiyuanmarry.model.MarriageState;
import cn.mcxyd.xiyuanmarry.model.WeddingPlan;

import java.util.UUID;

/** 纯婚礼贺礼资格规则，供持久化事务和单元测试共同使用。 */
public final class WeddingGiftPolicy {
    private WeddingGiftPolicy() {}

    public static boolean canSend(WeddingPlan plan, UUID guest, MarriageRecord engagement, long now) {
        if (plan == null || guest == null || engagement == null || engagement.state() != MarriageState.ENGAGED
                || !engagement.type().equals("WEDDING") || engagement.contains(guest)) return false;
        WeddingPlan.Invite invite = plan.invites().get(guest);
        return invite != null && invite.accepted() && invite.expires() > now;
    }

    public static boolean canClaim(UUID giftWeddingId, UUID actor, MarriageRecord current) {
        return giftWeddingId != null && actor != null && current != null && current.state() == MarriageState.MARRIED
                && giftWeddingId.toString().equals(current.id()) && current.contains(actor);
    }
}
