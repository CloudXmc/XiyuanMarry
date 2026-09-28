package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.MarriageRepository;
import java.util.*;
import com.google.gson.Gson;

public final class DailyTaskLedger {
    public static final String BUCKET = "daily-tasks";
    private final Gson json = new Gson();
    public static final String BIOME_BUCKET = "daily-task-biomes";
    private record SeenBiomes(long daySerial, Set<String> names) {}
    public record Outcome(DailyTask task, boolean rewarded) {}
    public static long daySerial(long marriedAt, long now) {
        return now <= marriedAt ? 0 : (now - marriedAt) / 86_400_000L;
    }
    public DailyTask current(MarriageRepository repository, MarriageRecord marriage, long now, TaskDefinition definition) {
        long day = daySerial(marriage.marriedAt(), now);
        String raw = repository.get(BUCKET, marriage.id());
        DailyTask saved = raw == null ? null : json.fromJson(raw, DailyTask.class);
        // 已分配任务保留定义快照；热重载或时钟回拨不得重置进度、重复给奖。
        if (saved != null && saved.daySerial() >= day) return saved;
        DailyTask created = new DailyTask(marriage.id(), day, definition, 0, false);
        repository.put(BUCKET, marriage.id(), json.toJson(created));
        return created;
    }
    public Outcome record(MarriageRepository repository, UUID actor, String relationshipId, long now,
                          TaskDefinition definition, CoupleTaskService.Event event) {
        return repository.transaction(r -> {
            MarriageRecord marriage = r.findByPlayer(actor);
            if (marriage == null || !marriage.married() || !marriage.id().equals(relationshipId)
                    || marriage.state() == MarriageState.DIVORCE_PENDING && marriage.divorceAt() <= now)
                return new Outcome(null, false);
            DailyTask before = current(r, marriage, now, definition);
            TaskDefinition assigned = before.definition();
            if (before.completed() || before.daySerial() != daySerial(marriage.marriedAt(), now) || event.amount() <= 0
                    || !assigned.type().equals(event.type())
                    || !assigned.selector().equals("*") && !assigned.selector().equalsIgnoreCase(event.value()))
                return new Outcome(before, false);
            long amount = event.amount();
            if (assigned.type().equals("BIOME")) {
                String name = event.value() == null ? "" : event.value().toLowerCase(Locale.ROOT);
                if (name.isBlank() || name.length() > 128) return new Outcome(before, false);
                String raw = r.get(BIOME_BUCKET, relationshipId);
                SeenBiomes saved = raw == null ? null : json.fromJson(raw, SeenBiomes.class);
                Set<String> seen = saved != null && saved.daySerial() == before.daySerial() && saved.names() != null
                    ? new HashSet<>(saved.names()) : new HashSet<>();
                seen.removeIf(v -> v == null || v.length() > 128);
                if (seen.size() >= 256 || !seen.add(name)) return new Outcome(before, false);
                // 去重水位与进度、奖励同事务提交，双方重复观察或重启不能重复计数。
                r.put(BIOME_BUCKET, relationshipId, json.toJson(new SeenBiomes(before.daySerial(), seen)));
                amount = 1;
            }
            long remaining = assigned.target() - before.progress();
            long progress = amount >= remaining ? assigned.target() : before.progress() + amount;
            boolean completed = progress >= assigned.target();
            DailyTask after = new DailyTask(before.coupleId(), before.daySerial(), assigned, progress, completed);
            if (completed && assigned.bondReward() > 0 && !r.addBond(actor, assigned.bondReward()))
                throw new IllegalStateException("任务羁绊提交失败，回滚进度");
            r.put(BUCKET, relationshipId, json.toJson(after));
            return new Outcome(after, completed);
        });
    }
}
