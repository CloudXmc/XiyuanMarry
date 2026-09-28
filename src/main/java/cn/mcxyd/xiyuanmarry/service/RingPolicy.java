package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.model.MarriageRecord;

/** 戒指效果资格判断只依赖不可变快照，便于单元测试和跨线程计算。 */
public final class RingPolicy {
    private RingPolicy() {}

    public static boolean active(boolean enabled, boolean marriageRing, MarriageRecord marriage,
                                 boolean sameWorld, double distanceSquared, double maxDistance) {
        return enabled && marriageRing && marriage != null && marriage.married()
                && sameWorld && distanceSquared <= maxDistance * maxDistance;
    }
}
