package cn.mcxyd.xiyuanmarry.service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** 仅保存不可变数据；实体返还与异步提交通过 CAS 仲裁，不阻塞任何 Region。 */
final class GiftSendAttempt {
    private enum Phase { QUEUED, WRITING, PERSISTED, FINISHED, RETURNED, REVIEW }
    private final UUID liveId;
    private final byte[] item;
    private final UUID generation;
    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.QUEUED);

    GiftSendAttempt(UUID liveId, byte[] item, UUID generation) {
        this.liveId=liveId; this.item=item.clone(); this.generation=generation;
    }
    UUID liveId() { return liveId; }
    byte[] item() { return item.clone(); }
    UUID generation() { return generation; }
    boolean begin() { return phase.compareAndSet(Phase.QUEUED,Phase.WRITING); }
    void committed() { phase.compareAndSet(Phase.WRITING,Phase.PERSISTED); }
    boolean persisted() { return phase.get()==Phase.PERSISTED; }
    void finish() { phase.compareAndSet(Phase.PERSISTED,Phase.FINISHED); }
    boolean returnOnce(boolean confirmedRollback) {
        while(true) {
            Phase current=phase.get();
            if(current!=Phase.QUEUED && !(confirmedRollback && current==Phase.WRITING))return false;
            if(phase.compareAndSet(current,Phase.RETURNED))return true;
        }
    }
    boolean review() {
        while(true) {
            Phase current=phase.get();
            if(current==Phase.RETURNED||current==Phase.FINISHED||current==Phase.REVIEW)return false;
            if(phase.compareAndSet(current,Phase.REVIEW))return true;
        }
    }
}
