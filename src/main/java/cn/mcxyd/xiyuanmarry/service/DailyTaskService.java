package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** 事件仅传值；每秒批量事务，队列有界，GUI读操作也走异步仓库。 */
public final class DailyTaskService implements AutoCloseable {
    private record Observation(UUID actor, String relationship, long at, TaskDefinition definition,
                               CoupleTaskService.Event event, UUID generation) {}
    private record BatchKey(String relationship, long day, String type, String value) {}
    private final MarriageService marriages;
    private final ConfigurationManager config;
    private final DailyTaskLedger ledger = new DailyTaskLedger();
    private final ArrayBlockingQueue<Observation> pending = new ArrayBlockingQueue<>(4096);
    private final TaskHandle timer;
    private volatile boolean closed;
    public DailyTaskService(MarriageService marriages, ConfigurationManager config, UnifiedScheduler scheduler) {
        this.marriages = marriages; this.config = config;
        timer = scheduler.runRepeatingAsync(this::flush, 1, 1, TimeUnit.SECONDS);
    }
    public void record(PlayerSnapshot actor, String type, String value, long amount) {
        if (closed || amount <= 0) return;
        var marriage = marriages.view().byPlayer().get(actor.id());
        if (marriage == null || !marriage.married()) return;
        var partner = marriages.directory().identity(marriage.partnerOf(actor.id()));
        var settings = config.snapshot();
        if (partner == null || System.currentTimeMillis() - partner.seenAt() > 2500
                || !CoupleTaskService.partnersNear(actor.point(), partner.point(), settings.tasks().distance())) return;
        var definition = settings.tasks().forDay(DailyTaskLedger.daySerial(marriage.marriedAt(), actor.seenAt()));
        if (!pending.offer(new Observation(actor.id(), marriage.id(), actor.seenAt(), definition,
                new CoupleTaskService.Event(type, value, amount), settings.generation())))
            marriages.notifyLive(actor.liveId(), "task-busy");
    }
    /** 供实体采样器复用同一套关系、世界和距离资格判断。 */
    public boolean nearbyPartner(PlayerSnapshot actor) {
        if (actor == null) return false;
        var marriage = marriages.view().byPlayer().get(actor.id());
        if (marriage == null || !marriage.married()) return false;
        var partner = marriages.directory().identity(marriage.partnerOf(actor.id()));
        return partner != null && System.currentTimeMillis() - partner.seenAt() <= 2500
                && CoupleTaskService.partnersNear(actor.point(), partner.point(), config.snapshot().tasks().distance());
    }
    private void flush() {
        if (closed || pending.isEmpty()) return;
        var collected = new ArrayList<Observation>(); pending.drainTo(collected, 4096);
        UUID generation = config.snapshot().generation();
        var groups = new LinkedHashMap<BatchKey, Observation>();
        for (var observation : collected) {
            if (!observation.generation().equals(generation)) continue;
            var marriage = marriages.view().byPlayer().get(observation.actor());
            if (marriage == null || !marriage.id().equals(observation.relationship())) continue;
            var key = new BatchKey(marriage.id(), DailyTaskLedger.daySerial(marriage.marriedAt(), observation.at()),
                    observation.event().type(), observation.event().value());
            groups.merge(key, observation, (a,b) -> new Observation(a.actor(), a.relationship(), a.at(), a.definition(),
                new CoupleTaskService.Event(a.event().type(), a.event().value(),
                    Math.min(1_000_000_000L, Math.min(1_000_000_000L,a.event().amount()) + Math.min(1_000_000_000L,b.event().amount()))), a.generation()));
        }
        if (groups.isEmpty()) return;
        marriages.submit(null, r -> {
            var completed = new ArrayList<DailyTask>();
            if (!generation.equals(config.snapshot().generation())) return completed;
            for (var o : groups.values()) {
                var outcome = ledger.record(r, o.actor(), o.relationship(), o.at(), o.definition(), o.event());
                if (outcome.rewarded()) completed.add(outcome.task());
            }
            return completed;
        }, completed -> {
            if (closed || !generation.equals(config.snapshot().generation())) return;
            for (var task : completed) for (var marriage : marriages.view().couples()) {
                if (!marriage.id().equals(task.coupleId())) continue;
                for (UUID id : List.of(marriage.playerOne(),marriage.playerTwo()))
                    marriages.notifyIdentity(id, "task-completed", "task", task.definition().name(), "bond", task.definition().bondReward());
            }
        });
    }
    public void load(PlayerSnapshot actor, Consumer<DailyTask> result) {
        UUID generation = config.snapshot().generation();
        marriages.submit(actor.liveId(), r -> {
            var marriage = r.findByPlayer(actor.id());
            RuleViolation.require(marriage != null && marriage.married(), "married-required");
            long now = System.currentTimeMillis();
            return ledger.current(r, marriage, now, config.snapshot().tasks().forDay(DailyTaskLedger.daySerial(marriage.marriedAt(), now)));
        }, task -> {if (!closed && generation.equals(config.snapshot().generation())) result.accept(task);});
    }
    public void reload() {pending.clear();}
    @Override public void close() {closed = true; timer.cancel(); pending.clear();}
}
