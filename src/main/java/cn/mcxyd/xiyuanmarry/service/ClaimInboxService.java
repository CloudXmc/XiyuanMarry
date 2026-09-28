package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.repository.DatabaseManager;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 同一次事务读取两类收件箱，避免嵌套提交争锁或跨数据库代次拼接列表。 */
public final class ClaimInboxService implements AutoCloseable {
    private final MarriageService marriages;
    private final RewardService rewards;
    private final GiftService gifts;
    private final UnifiedScheduler scheduler;
    private final DatabaseManager database;
    private final ConfigurationManager config;
    private final MessageService messages;
    private volatile boolean closed;

    public ClaimInboxService(MarriageService marriages, RewardService rewards, GiftService gifts,
                             UnifiedScheduler scheduler, DatabaseManager database,
                             ConfigurationManager config, MessageService messages) {
        this.marriages = marriages; this.rewards = rewards; this.gifts = gifts;
        this.scheduler = scheduler; this.database = database; this.config = config; this.messages = messages;
    }

    public void list(PlayerSnapshot actor) {
        if (closed) return;
        UUID databaseGeneration = database.generation(), configGeneration = config.snapshot().generation();
        marriages.submit(actor.liveId(), repository -> {
            if (!current(databaseGeneration, configGeneration)) return List.<InboxMessage>of();
            var result = new ArrayList<>(rewards.inboxMessages(repository, actor.id()));
            result.addAll(gifts.inboxMessages(repository, actor.id()));
            return List.copyOf(result);
        }, result -> {
            if (!current(databaseGeneration, configGeneration)) return;
            scheduler.player(actor.liveId(), player -> {
                if (!current(databaseGeneration, configGeneration)) return;
                var live = marriages.directory().live(actor.liveId());
                if (live == null || !live.id().equals(actor.id()) || !live.identityKey().equals(actor.identityKey())) return;
                // 已回到玩家实体上下文，直接输出同一批快照，避免再排队产生旧结果窗口。
                for (var notice : result) messages.send(player, notice.key(), notice.values().toArray());
            });
        });
    }

    private boolean current(UUID databaseGeneration, UUID configGeneration) {
        return !closed && database.isCurrent(databaseGeneration) && config.snapshot().generation().equals(configGeneration);
    }

    @Override public void close() { closed = true; }
}
