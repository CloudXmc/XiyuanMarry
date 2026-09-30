package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.config.RewardCatalog;
import cn.mcxyd.xiyuanmarry.model.RewardTicket;
import cn.mcxyd.xiyuanmarry.model.MarriageRecord;
import cn.mcxyd.xiyuanmarry.model.MarriageState;
import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.model.RewardDefinition;
import cn.mcxyd.xiyuanmarry.repository.DatabaseManager;
import cn.mcxyd.xiyuanmarry.repository.MarriageRepository;
import cn.mcxyd.xiyuanmarry.scheduler.TaskHandle;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import com.google.gson.Gson;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** 纪念日奖励出站箱；票据先入库，领取时才执行经验和管理员配置的命令。 */
public final class RewardService implements AutoCloseable {
    private static final String BUCKET = "reward-inbox";
    private static final long SCAN_SECONDS = 30;
    private final MarriageService marriages;
    private final ConfigurationManager config;
    private final UnifiedScheduler scheduler;
    private final DatabaseManager database;
    private final Gson gson;
    private final AnniversaryRewardLedger anniversaryLedger = new AnniversaryRewardLedger();
    private final WeeklyRewardLedger weeklyLedger = new WeeklyRewardLedger();
    private final RewardClaimService claims;
    private record PublishedCatalog(UUID generation, UUID databaseGeneration, RewardCatalog catalog, long activatedAt) {}
    private volatile PublishedCatalog settings;
    private volatile TaskHandle timer;
    private volatile boolean closed;

    public record Pending(UUID id, long days, int rank, long money, long experience) {}

    public RewardService(MarriageService marriages, ConfigurationManager config, UnifiedScheduler scheduler,
                         DatabaseManager database, IoDispatcher io, Logger logger) {
        this.marriages = marriages; this.config = config; this.scheduler = scheduler; this.gson = marriages.json();
        this.database = database;
        this.claims = new RewardClaimService(marriages, config, database, io, scheduler, logger); reload();
    }

    public void start() { timer = scheduler.runRepeatingAsync(this::scan, 1, SCAN_SECONDS, java.util.concurrent.TimeUnit.SECONDS); }

    public void reload() {
        var snapshot = config.snapshot();
        var parsed = RewardCatalog.parse(snapshot.files().get("rewards.yml"), snapshot.files().get("config.yml"));
        settings = new PublishedCatalog(snapshot.generation(), database.generation(), parsed, System.currentTimeMillis());
        claims.reload();
    }

    private boolean current(PublishedCatalog expected) {
        return !closed && settings == expected && expected.generation().equals(config.snapshot().generation())
                && database.isCurrent(expected.databaseGeneration());
    }

    private void scan() {
        PublishedCatalog expected = settings;
        if (expected == null || !current(expected)) return;
        long now = System.currentTimeMillis();
        for (MarriageRecord marriage : marriages.view().couples()) {
            if (marriage.state() != MarriageState.MARRIED || marriage.marriedAt() <= 0) continue;
            long days = Math.max(0, Duration.ofMillis(Math.max(0, now - marriage.marriedAt())).toDays());
            for (RewardDefinition definition : expected.catalog().anniversaries().values())
                if (definition.days() <= days) issue(marriage, definition, expected);
        }
        scanWeekly(expected);
    }

    private void scanWeekly(PublishedCatalog expected) {
        var options = expected.catalog();
        if (!options.weeklyEnabled() || options.weekly().isEmpty()) return;
        var schedule = options.schedule();
        // 不把异步扫描时的旧榜单带进事务；IO 队列内读取同一数据库代次的关系。
        marriages.submitAtGeneration(null, expected.databaseGeneration(), r -> current(expected)
                ? weeklyLedger.settle(r, options.board(), schedule.day(), schedule.hour(), schedule.zone(),
                        options.weekly(), options::level, System.currentTimeMillis(), expected.activatedAt()) : null, result -> {
            if (result == null || !result.settled() || !current(expected)) return;
            if (result.legacyDetected()) {
                config.warn("周榜 " + result.week() + " 检测到旧版本发奖记录，已保留原票据并封账；如有差异请人工核对。");
                return;
            }
            for (var ticket : result.tickets()) marriages.notifyIdentity(ticket.recipient(), "reward-pending");
            if (result.winners().isEmpty()) return;
            // 广播只在票据事务提交后发送；提交后崩溃可能漏广播，但绝不重新发奖。
            marriages.broadcast("rank-weekly", "week", result.week(), "board", result.board());
            for (var winner : result.winners()) marriages.broadcast("rank-weekly-winner",
                    "rank", winner.rank(), "player1", marriages.name(winner.one()),
                    "player2", marriages.name(winner.two()));
        }, ignored -> {});
    }

    static String weeklyAnnouncementMarker(String board, String weekKey) { return board + ":" + weekKey; }
    static String weeklyWeekKey(ZonedDateTime local, DayOfWeek day, int hour) {
        return new WeeklyRewardSchedule(local.getZone(), day, hour).previousOrSame(local.toInstant()).toLocalDate().toString();
    }

    private void issue(MarriageRecord marriage, RewardDefinition definition, PublishedCatalog expected) {
        marriages.submitAtGeneration(null, expected.databaseGeneration(), r -> current(expected)
                ? anniversaryLedger.issue(r, marriage, definition, System.currentTimeMillis()) : List.<RewardTicket>of(),
                tickets -> {
                    if (current(expected)) for (var ticket : tickets) marriages.notifyIdentity(ticket.recipient(), "reward-pending");
                }, ignored -> {});
    }

    public void list(PlayerSnapshot actor) {
        list(actor, () -> {});
    }

    public void list(PlayerSnapshot actor, Runnable next) {
        marriages.submit(actor.liveId(), r -> inboxMessages(r, actor.id()), inbox -> scheduler.player(actor.liveId(), p -> {
            for (var notice : inbox) marriages.notifyLive(actor.liveId(), notice.key(), notice.values().toArray());
            next.run();
        }));
    }

    List<InboxMessage> inboxMessages(MarriageRepository repository, UUID recipient) {
        var inbox = RewardInbox.forRecipient(all(repository), recipient);
        var result = new ArrayList<InboxMessage>();
        if (inbox.available().isEmpty() && inbox.review().isEmpty()) result.add(InboxMessage.of("reward-none"));
        if (!inbox.available().isEmpty()) result.add(InboxMessage.of("reward-pending", "count", inbox.available().size()));
        for (RewardTicket item : inbox.available()) {
            if (item.anniversaryDays() > 0) result.add(InboxMessage.of("reward-id", "id", item.id(), "days", item.anniversaryDays(), "experience", item.experience()));
            else result.add(InboxMessage.of("reward-weekly-id", "id", item.id(), "rank", item.rank(), "experience", item.experience()));
        }
        for (UUID id : inbox.review()) result.add(InboxMessage.of("reward-review-id", "id", id));
        return List.copyOf(result);
    }

    /** 找不到奖励票据时回调 false，让 GiftService 继续处理物品礼物。 */
    public void claim(PlayerSnapshot actor, UUID id, Consumer<Boolean> next) {
        if (actor == null || id == null) {
            if (next != null) next.accept(true);
            return;
        }
        claims.claim(actor, id, next);
    }

    private List<RewardTicket> all(MarriageRepository r) {
        List<RewardTicket> out = new ArrayList<>();
        for (var entry : r.entries(BUCKET).entrySet()) try {
            RewardTicket ticket = gson.fromJson(entry.getValue(), RewardTicket.class);
            if (valid(ticket) && entry.getKey().equals(ticket.id().toString())) out.add(ticket);
        } catch (RuntimeException ignored) {}
        return out;
    }
    private boolean valid(RewardTicket ticket) { return ticket != null && ticket.id() != null && ticket.recipient() != null
            && ticket.state() != null && ticket.commands() != null && ticket.created() >= 0 && ticket.updated() >= 0; }

    @Override public void close() { closed = true; if (timer != null) timer.cancel(); settings = null; claims.close(); }
}
