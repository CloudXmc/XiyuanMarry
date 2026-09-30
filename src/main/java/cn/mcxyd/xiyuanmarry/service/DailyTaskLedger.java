package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.MarriageRepository;
import java.util.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class DailyTaskLedger {
    public static final String BUCKET = "daily-tasks";
    private final Gson json = new Gson();
    public static final String BIOME_BUCKET = "daily-task-biomes";
    private record SeenBiomes(long daySerial, Set<String> names) {}
    public record Outcome(DailyTask task, boolean rewarded) {}
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    /**
     * 任务日按结婚当地日期计算：结婚当天为第 0 个序号（界面显示第 1 天），跨自然日才切换。
     * 这样不会因为结婚时间接近午夜而让“结婚当天”被错误延后到 24 小时后。
     */
    public static long daySerial(long marriedAt, long now) {
        return daySerial(marriedAt, now, DEFAULT_ZONE);
    }
    public static long daySerial(long marriedAt, long now, ZoneId zone) {
        Objects.requireNonNull(zone, "zone");
        if (now < marriedAt) return 0;
        LocalDate marriedDate = Instant.ofEpochMilli(marriedAt).atZone(zone).toLocalDate();
        LocalDate currentDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        return Math.max(0, ChronoUnit.DAYS.between(marriedDate, currentDate));
    }
    /** 返回 null 表示持久记录不完整；不能把未知完成状态当成新任务覆盖。 */
    public DailyTask current(MarriageRepository repository, MarriageRecord marriage, long now, TaskDefinition definition) {
        return current(repository, marriage, now, definition, DEFAULT_ZONE);
    }
    public DailyTask current(MarriageRepository repository, MarriageRecord marriage, long now, TaskDefinition definition, ZoneId zone) {
        long day = daySerial(marriage.marriedAt(), now, zone);
        String raw = repository.get(BUCKET, marriage.id());
        DailyTask saved = raw == null ? null : readTask(raw, marriage.id());
        if (raw != null && saved == null) return null;
        if (saved != null && saved.definition().type().equals("BIOME") && biomeNames(repository, saved) == null) return null;
        // 已分配任务保留定义快照；热重载或时钟回拨不得重置进度、重复给奖。
        if (saved != null && saved.daySerial() >= day) return saved;
        DailyTask created = new DailyTask(marriage.id(), day, definition, 0, false);
        if (definition.type().equals("BIOME") && biomeNames(repository, created) == null) return null;
        repository.put(BUCKET, marriage.id(), json.toJson(created));
        return created;
    }
    public Outcome record(MarriageRepository repository, UUID actor, String relationshipId, long now,
                          TaskDefinition definition, CoupleTaskService.Event event) {
        return record(repository, actor, relationshipId, now, definition, event, DEFAULT_ZONE);
    }
    public Outcome record(MarriageRepository repository, UUID actor, String relationshipId, long now,
                          TaskDefinition definition, CoupleTaskService.Event event, ZoneId zone) {
        if (repository == null || actor == null || relationshipId == null || definition == null || event == null)
            return new Outcome(null, false);
        return repository.transaction(r -> {
            MarriageRecord marriage = r.findByPlayer(actor);
            if (marriage == null || !marriage.married() || !marriage.id().equals(relationshipId)
                    || marriage.state() == MarriageState.DIVORCE_PENDING && marriage.divorceAt() <= now)
                return new Outcome(null, false);
            DailyTask before = current(r, marriage, now, definition, zone);
            if (before == null) return new Outcome(null, false);
            TaskDefinition assigned = before.definition();
            if (before.completed() || before.daySerial() != daySerial(marriage.marriedAt(), now, zone) || event.amount() <= 0
                    || !assigned.type().equals(event.type())
                    || !assigned.selector().equals("*") && (event.value() == null || !assigned.selector().equalsIgnoreCase(event.value())))
                return new Outcome(before, false);
            long amount = event.amount();
            if (assigned.type().equals("BIOME")) {
                String name = event.value() == null ? "" : event.value().toLowerCase(Locale.ROOT);
                if (name.isBlank() || name.length() > 128) return new Outcome(before, false);
                Set<String> seen = biomeNames(r, before);
                if (seen == null) return new Outcome(before, false);
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
    private DailyTask readTask(String raw, String relationship) {
        try {
            var source = JsonParser.parseString(raw).getAsJsonObject();
            if (!present(source, "coupleId", "daySerial", "definition", "progress", "completed")
                    || !source.getAsJsonPrimitive("daySerial").isNumber()
                    || !source.getAsJsonPrimitive("progress").isNumber()
                    || !source.getAsJsonPrimitive("completed").isBoolean()
                    || !present(source.getAsJsonObject("definition"), "id", "type", "name", "selector", "target", "bondReward")) return null;
            var saved = json.fromJson(source, DailyTask.class);
            // 关系、日序、进度与完成标记必须一致；配置重载不改已分配定义。
            if (!relationship.equals(saved.coupleId()) || saved.daySerial() < 0 || saved.progress() < 0
                    || !CoupleTaskService.TYPES.contains(saved.definition().type())
                    || saved.progress() > saved.definition().target()
                    || saved.completed() != (saved.progress() == saved.definition().target())) return null;
            return saved;
        } catch (RuntimeException invalid) { return null; }
    }
    private static boolean present(JsonObject object, String... keys) {
        for (String key : keys) if (!object.has(key) || object.get(key).isJsonNull()) return false;
        return true;
    }
    /** 每个群系只产生一次进度；缺失或不一致的水位不能重新计数。 */
    private Set<String> biomeNames(MarriageRepository repository, DailyTask task) {
        String raw = repository.get(BIOME_BUCKET, task.coupleId());
        if (raw == null) return task.progress() == 0 ? new HashSet<>() : null;
        try {
            var source = JsonParser.parseString(raw).getAsJsonObject();
            if (!present(source, "daySerial", "names") || !source.getAsJsonPrimitive("daySerial").isNumber()) return null;
            var saved = json.fromJson(source, SeenBiomes.class);
            if (saved.daySerial() < 0 || saved.daySerial() > task.daySerial()) return null;
            var seen = new HashSet<String>();
            for (var value : source.getAsJsonArray("names")) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
                String name = value.getAsString();
                if (name.isBlank() || name.length() > 128 || !name.equals(name.toLowerCase(Locale.ROOT)) || !seen.add(name)) return null;
            }
            if (seen.size() > 256) return null;
            if (saved.daySerial() < task.daySerial()) return task.progress() == 0 ? new HashSet<>() : null;
            return seen.size() == task.progress() ? seen : null;
        } catch (RuntimeException invalid) { return null; }
    }
}
