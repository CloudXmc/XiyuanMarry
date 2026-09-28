package cn.mcxyd.xiyuanmarry.service;

/** 跨线程领取门闩；锁内只有状态切换，不执行 IO 或游戏操作。 */
final class RewardClaimAttempt {
    enum Outcome { RETRY, REVIEW, CONSUMED }
    private boolean started;
    private Outcome outcome;
    synchronized boolean startEffects() {
        if (outcome != null || started) return false;
        started = true; return true;
    }
    synchronized boolean active() { return outcome == null; }
    synchronized boolean finish(Outcome requested) {
        if (outcome != null) return false;
        // 一旦开始外部发放，失败不能重新开放领取，否则可能重复发奖。
        outcome = requested == Outcome.RETRY && started ? Outcome.REVIEW : requested;
        return true;
    }
    synchronized Outcome outcome() { return outcome; }
}
