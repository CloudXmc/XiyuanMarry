package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import java.util.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** 事件仅传值；每秒批量事务，队列有界，GUI读操作也走异步仓库。 */
public final class DailyTaskService implements AutoCloseable {
    private record Observation(UUID actor, String relationship, long at, TaskDefinition definition,
                               CoupleTaskService.Event event, ZoneId zone, UUID generation, UUID epoch, UUID database) {}
    private record BatchKey(String relationship, long day, String type, String value) {}
    private final MarriageService marriages;
    private final ConfigurationManager config;
    private final DailyTaskLedger ledger = new DailyTaskLedger();
    private final ArrayBlockingQueue<Observation> pending = new ArrayBlockingQueue<>(4096);
    private final TaskHandle timer;
    private volatile UUID readEpoch = UUID.randomUUID();
    private volatile boolean closed;
    public DailyTaskService(MarriageService marriages, ConfigurationManager config, UnifiedScheduler scheduler) {
        this.marriages = marriages; this.config = config;
        timer = scheduler.runRepeatingAsync(this::flush, 1, 1, TimeUnit.SECONDS);
    }
    public void record(PlayerSnapshot actor, String type, String value, long amount) {
        if (closed || actor == null || type == null || amount <= 0) return;
        UUID epoch = readEpoch;
        final UUID database;
        try { database = marriages.databaseGeneration(); }
        catch (IllegalStateException unavailable) { return; }
        var marriage = marriages.view().byPlayer().get(actor.id());
        if (marriage == null || !marriage.married()) return;
        var partner = marriages.directory().identity(marriage.partnerOf(actor.id()));
        var settings = config.snapshot();
        ZoneId zone = taskZone(settings.files().get("config.yml"));
        long now = System.currentTimeMillis();
        if (!fresh(actor, now) || !fresh(partner, now)
                || !CoupleTaskService.partnersNear(actor.point(), partner.point(), settings.tasks().distance())) return;
        var definition = settings.tasks().forDay(DailyTaskLedger.daySerial(marriage.marriedAt(), actor.seenAt(), zone));
        if (!pending.offer(new Observation(actor.id(), marriage.id(), actor.seenAt(), definition,
                new CoupleTaskService.Event(type, value, amount), zone, settings.generation(), epoch, database)))
            marriages.notifyLive(actor.liveId(), "task-busy");
    }
    /** 供实体采样器复用同一套关系、世界和距离资格判断。 */
    public boolean nearbyPartner(PlayerSnapshot actor) {
        if (closed || actor == null) return false;
        var marriage = marriages.view().byPlayer().get(actor.id());
        if (marriage == null || !marriage.married()) return false;
        var partner = marriages.directory().identity(marriage.partnerOf(actor.id()));
        long now = System.currentTimeMillis();
        return fresh(actor, now) && fresh(partner, now)
                && CoupleTaskService.partnersNear(actor.point(), partner.point(), config.snapshot().tasks().distance());
    }
    private static boolean fresh(PlayerSnapshot player, long now) {
        return player != null && player.seenAt() <= now && now - player.seenAt() <= 2500;
    }
    private void flush() {
        if (closed || pending.isEmpty()) return;
        UUID epoch = readEpoch;
        final UUID database;
        try { database = marriages.databaseGeneration(); }
        catch (IllegalStateException unavailable) { pending.clear(); return; }
        var collected = new ArrayList<Observation>(); pending.drainTo(collected, 4096);
        UUID generation = config.snapshot().generation();
        var groups = new LinkedHashMap<BatchKey, Observation>();
        for (var observation : collected) {
            if (!observation.generation().equals(generation) || !observation.epoch().equals(epoch)
                    || !observation.database().equals(database)) continue;
            var marriage = marriages.view().byPlayer().get(observation.actor());
            if (marriage == null || !marriage.id().equals(observation.relationship())) continue;
            var key = new BatchKey(marriage.id(), DailyTaskLedger.daySerial(marriage.marriedAt(), observation.at(), observation.zone()),
                    observation.event().type(), observation.event().value());
            groups.merge(key, observation, (a,b) -> new Observation(a.actor(), a.relationship(), a.at(), a.definition(),
                new CoupleTaskService.Event(a.event().type(), a.event().value(),
                    Math.min(1_000_000_000L, Math.min(1_000_000_000L,a.event().amount()) + Math.min(1_000_000_000L,b.event().amount()))), a.zone(), a.generation(), a.epoch(), a.database()));
        }
        if (groups.isEmpty()) return;
        // IO 开始前核对生命周期，数据库租约原子校验代次；不持有 Region 锁等待事务。
        marriages.submitAtGeneration(null, database, r -> {
            var completed = new ArrayList<DailyTask>();
            if (!currentRead(epoch, generation)) return completed;
            for (var o : groups.values()) {
                var outcome = ledger.record(r, o.actor(), o.relationship(), o.at(), o.definition(), o.event(), o.zone());
                if (outcome.rewarded()) completed.add(outcome.task());
            }
            return completed;
        }, completed -> {
            if (!currentRead(epoch, generation) || !currentDatabase(database)) return;
            for (var task : completed) for (var marriage : marriages.view().couples()) {
                if (!marriage.id().equals(task.coupleId())) continue;
                for (UUID id : List.of(marriage.playerOne(),marriage.playerTwo()))
                    marriages.notifyIdentity(id, "task-completed", "task", task.definition().name(), "bond", task.definition().bondReward());
            }
        }, error -> {});
    }
    public void load(PlayerSnapshot actor, Consumer<DailyTask> result) {
        load(actor, result, error -> {});
    }
    /** 异步读取今日任务；失败回调避免界面永久停留在加载状态。 */
    public void load(PlayerSnapshot actor, Consumer<DailyTask> result, Consumer<Throwable> failed) {
        if (closed || actor == null) { failed.accept(new CancellationException("任务读取已失效")); return; }
        final ConfigurationManager.Snapshot settings;
        final UUID databaseGeneration, requestEpoch = readEpoch;
        try { settings = config.snapshot(); databaseGeneration = marriages.databaseGeneration(); }
        catch (RuntimeException unavailable) { failed.accept(unavailable); return; }
        marriages.submitAtGeneration(actor.liveId(), databaseGeneration, r -> {
            // current 会初始化当天任务；失效请求必须在任何仓库写入之前结束。
            if (!currentRead(requestEpoch, settings.generation())) return null;
            var live = marriages.directory().live(actor.liveId());
            if (live == null || !live.id().equals(actor.id()) || !live.identityKey().equals(actor.identityKey())) return null;
            var marriage = r.findByPlayer(actor.id());
            RuleViolation.require(marriage != null && marriage.married(), "married-required");
            long now = System.currentTimeMillis();
            ZoneId zone = taskZone(settings.files().get("config.yml"));
            var task = ledger.current(r, marriage, now, settings.tasks().forDay(DailyTaskLedger.daySerial(marriage.marriedAt(), now, zone)), zone);
            if (task == null) {
                config.warn("情侣任务记录校验失败，已保留原始数据；关系编号：" + marriage.id());
                throw new RuleViolation("task-data-invalid");
            }
            return task;
        }, task -> {
            if (task == null || !currentRead(requestEpoch, settings.generation()) || !currentDatabase(databaseGeneration))
                failed.accept(new CancellationException("任务读取已失效"));
            else result.accept(task);
        }, failed);
    }
    private boolean currentRead(UUID requestEpoch, UUID generation) {
        return !closed && requestEpoch.equals(readEpoch) && generation.equals(config.snapshot().generation());
    }
    private boolean currentDatabase(UUID generation) {
        try { return generation.equals(marriages.databaseGeneration()); }
        catch (IllegalStateException unavailable) { return false; }
    }
    public void reload() {readEpoch = UUID.randomUUID(); pending.clear();}
    private static ZoneId taskZone(org.bukkit.configuration.file.YamlConfiguration config) {
        try { return ZoneId.of(config.getString("timezone", "Asia/Shanghai")); }
        catch (RuntimeException invalid) { return ZoneId.of("Asia/Shanghai"); }
    }
    @Override public void close() {closed = true; readEpoch = UUID.randomUUID(); pending.clear(); timer.cancel();}
}
