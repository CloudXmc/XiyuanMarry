package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.model.RewardTicket;
import cn.mcxyd.xiyuanmarry.model.RewardDefinition;
import cn.mcxyd.xiyuanmarry.repository.MarriageRepository;
import com.google.gson.Gson;
import java.time.*;
import java.util.*;
import java.util.function.LongToIntFunction;

/** 单实例周榜事务；调用者必须位于异步 IO 上下文。 */
final class WeeklyRewardLedger {
    static final String CURSORS = "weekly-cursor";
    static final String SETTLEMENTS = "weekly-settlement";
    static final String WINNERS = "weekly-winners";
    private final Gson gson = new Gson();
    private final RankingService ranking = new RankingService();
    record Cursor(String schedule, long nextAt, String lastWeek) {}
    record Settlement(String week, String board, long created, int winnerCount, boolean legacyDetected) {}
    record Winner(int rank, String relationship, UUID one, UUID two, int level, long bond) {}
    record Result(boolean settled, String week, String board, long created, List<Winner> winners,
                  List<RewardTicket> tickets, boolean legacyDetected) {
        Result { winners = List.copyOf(winners); tickets = List.copyOf(tickets); }
    }
    Result settle(MarriageRepository repository, String board, DayOfWeek day, int hour, ZoneId zone,
                  Map<Integer, RewardDefinition> rewards, LongToIntFunction level, long now) {
        return settle(repository, board, day, hour, zone, rewards, level, now, now);
    }
    Result settle(MarriageRepository repository, String board, DayOfWeek day, int hour, ZoneId zone,
                  Map<Integer, RewardDefinition> rewards, LongToIntFunction level, long now, long activatedAt) {
        if (!RankingService.BOARDS.contains(board)) throw new IllegalArgumentException("周榜类型无效");
        var schedule = new WeeklyRewardSchedule(zone, day, hour);
        Map<Integer, RewardDefinition> catalog = Map.copyOf(rewards);
        if (catalog.isEmpty()) return none(board, now);
        int limit = catalog.keySet().stream().mapToInt(Integer::intValue).max().orElseThrow();
        if (catalog.keySet().stream().anyMatch(rank -> rank < 1 || rank > 1000))
            throw new IllegalArgumentException("周榜奖励名次范围为 1-1000");
        return repository.transaction(r -> {
            String raw = r.get(CURSORS, board);
            Cursor cursor;
            try { cursor = raw == null ? null : gson.fromJson(raw, Cursor.class); }
            catch (RuntimeException invalid) { return none(board, now); }
            if (raw != null && (cursor == null || cursor.schedule() == null || cursor.lastWeek() == null || cursor.nextAt() < 0))
                return none(board, now);
            if (cursor == null || !schedule.signature().equals(cursor.schedule())) {
                cursor = new Cursor(schedule.signature(), schedule.nextOrSame(Instant.ofEpochMilli(Math.min(now, activatedAt))).toInstant().toEpochMilli(),
                        cursor == null ? "" : cursor.lastWeek());
                r.put(CURSORS, board, gson.toJson(cursor));
            }
            if (now < cursor.nextAt()) return none(board, now);
            // 停服错过多期时只结算最近一期；当前指标不能伪装成历史快照。
            var boundary = schedule.previousOrSame(Instant.ofEpochMilli(now));
            String week = boundary.toLocalDate().toString();
            long nextAt = schedule.nextAfter(boundary).toInstant().toEpochMilli();
            if (cursor.lastWeek().compareTo(week) >= 0) {
                r.put(CURSORS, board, gson.toJson(new Cursor(schedule.signature(), nextAt, cursor.lastWeek())));
                return none(board, now);
            }
            String prefix = "weekly:" + week + ":";
            // 旧版没有完整名单：保留既有票据，整期封账，不擅自补发或撤回。
            boolean legacy = r.get("weekly-announced", board + ":" + week) != null
                    || r.entries("reward-issued").keySet().stream().anyMatch(key -> key.startsWith(prefix));
            var winners = new ArrayList<Winner>();
            var tickets = new ArrayList<RewardTicket>();
            if (!legacy) {
                var eligible = r.findAll().stream().filter(m -> m.marriedAt() > 0
                        && m.marriedAt() <= boundary.toInstant().toEpochMilli()).toList();
                var ranked = ranking.rank(eligible, board, now, level, limit);
                for (int index = 0; index < ranked.size(); index++) {
                    int rank = index + 1;
                    RewardDefinition definition = catalog.get(rank);
                    if (definition == null) continue;
                    var marriage = ranked.get(index);
                    winners.add(new Winner(rank, marriage.id(), marriage.playerOne(), marriage.playerTwo(),
                            level.applyAsInt(marriage.bond()), marriage.bond()));
                    for (UUID recipient : List.of(marriage.playerOne(), marriage.playerTwo())) {
                        var ticket = new RewardTicket(UUID.randomUUID(), recipient, marriage.id(), now, now,
                                "COMMITTED", 0, rank, definition.money(), definition.experience(), definition.commands());
                        r.put("reward-inbox", ticket.id().toString(), gson.toJson(ticket));
                        tickets.add(ticket);
                    }
                }
            }
            Result result = new Result(true, week, board, now, winners, tickets, legacy);
            // 票据、名单和单调游标同事务提交；任何异常回滚全部，不留下半份奖励。
            for (var winner : winners) r.put(WINNERS, board + ":" + week + ":" + winner.rank(), gson.toJson(winner));
            r.put(SETTLEMENTS, board + ":" + week, gson.toJson(new Settlement(week, board, now, winners.size(), legacy)));
            r.put(CURSORS, board, gson.toJson(new Cursor(schedule.signature(), nextAt, week)));
            prune(r, now);
            return result;
        });
    }
    private Result none(String board, long now) { return new Result(false, "", board, now, List.of(), List.of(), false); }
    private void prune(MarriageRepository r, long now) {
        long cutoff = now - Duration.ofDays(120).toMillis();
        for (var entry : r.entries(SETTLEMENTS).entrySet()) {
            Settlement historical;
            try { historical = gson.fromJson(entry.getValue(), Settlement.class); }
            catch (RuntimeException invalid) { continue; }
            if (historical == null || historical.created() < 0) continue;
            if (historical.created() < cutoff) {
                String prefix = entry.getKey() + ":";
                for (String key : r.entries(WINNERS).keySet()) if (key.startsWith(prefix)) r.remove(WINNERS, key);
                r.remove(SETTLEMENTS, entry.getKey());
            }
        }
        // 四个榜单各保留一个游标；清理历史快照也不能清理防重水位。
    }
}
