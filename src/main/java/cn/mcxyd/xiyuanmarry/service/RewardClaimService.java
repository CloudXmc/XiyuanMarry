package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.repository.DatabaseManager;
import cn.mcxyd.xiyuanmarry.scheduler.TaskHandle;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import org.bukkit.Bukkit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import static cn.mcxyd.xiyuanmarry.service.RewardClaimAttempt.Outcome;

/** 领取编排：IO 预占 → 玩家经验 → 全局经济/命令 → 同代次 IO 确认。 */
final class RewardClaimService implements AutoCloseable {
    private static final int MAX_PENDING = 128;
    private static final class Operation {
        final PlayerSnapshot actor;
        final UUID id, databaseGeneration, configGeneration;
        final Consumer<Boolean> next;
        final RewardClaimAttempt attempt = new RewardClaimAttempt();
        final AtomicBoolean finalQueued = new AtomicBoolean(), replied = new AtomicBoolean();
        volatile RewardClaimLedger.Claim claim;
        volatile TaskHandle timeout;
        volatile String message;
        Operation(PlayerSnapshot actor, UUID id, UUID database, UUID config, Consumer<Boolean> next) {
            this.actor = actor; this.id = id; databaseGeneration = database; configGeneration = config; this.next = next;
        }
    }
    private final MarriageService marriages;
    private final ConfigurationManager config;
    private final DatabaseManager database;
    private final IoDispatcher io;
    private final UnifiedScheduler scheduler;
    private final Logger logger;
    private final RewardClaimLedger ledger;
    private final ConcurrentHashMap<UUID, Operation> pending = new ConcurrentHashMap<>();
    private volatile TaskHandle economyRefresh;
    private volatile EconomyGateway economy;
    private volatile boolean closed;

    RewardClaimService(MarriageService marriages, ConfigurationManager config, DatabaseManager database,
                       IoDispatcher io, UnifiedScheduler scheduler, Logger logger) {
        this.marriages = marriages; this.config = config; this.database = database;
        this.io = io; this.scheduler = scheduler; this.logger = logger; ledger = new RewardClaimLedger(database);
    }
    void reload() {
        for (var operation : List.copyOf(pending.values())) finish(operation, Outcome.RETRY, "reward-claim-cancelled");
        var previous = economyRefresh;
        if (previous != null) previous.cancel();
        economy = null;
        economyRefresh = scheduler.runGlobal(() -> {
            if (!closed) economy = Bukkit.getPluginManager().isPluginEnabled("Vault")
                    ? VaultEconomyGateway.createIfAvailable() : null;
        });
    }
    void claim(PlayerSnapshot actor, UUID id, Consumer<Boolean> next) {
        Operation operation;
        synchronized (pending) {
            if (closed) { next.accept(true); return; }
            if (pending.size() >= MAX_PENDING || pending.containsKey(actor.liveId())) {
                marriages.notifyLive(actor.liveId(), "busy"); next.accept(true); return;
            }
            operation = new Operation(actor, id, database.generation(), config.snapshot().generation(), next);
            pending.put(actor.liveId(), operation);
        }
        try {
            operation.timeout = scheduler.runAsyncLater(() -> finish(operation, Outcome.RETRY, "reward-claim-timeout"), 30, TimeUnit.SECONDS);
            if (!operation.attempt.active()) operation.timeout.cancel();
            if (!io.submit(() -> reserve(operation))) finish(operation, Outcome.RETRY, "busy");
        } catch (RuntimeException failure) { failed(operation, failure); }
    }
    private boolean current(Operation operation) {
        return !closed && operation.attempt.active() && pending.get(operation.actor.liveId()) == operation
                && operation.configGeneration.equals(config.snapshot().generation())
                && database.isCurrent(operation.databaseGeneration);
    }
    private void reserve(Operation operation) {
        if (!current(operation)) { finish(operation, Outcome.RETRY, "reward-claim-cancelled"); return; }
        try {
            operation.claim = ledger.reserve(operation.databaseGeneration, operation.actor.id(), operation.id, System.currentTimeMillis());
            if (operation.claim == null) {
                boolean giftFallback;
                synchronized (operation) {
                    giftFallback = current(operation) && operation.attempt.finish(Outcome.CONSUMED);
                }
                if (giftFallback) conclude(operation, false, null);
                else finish(operation, Outcome.RETRY, "reward-claim-cancelled");
                return;
            }
            if (!current(operation)) {
                finish(operation, Outcome.RETRY, "reward-claim-cancelled"); finalizeLater(operation); return;
            }
            deliver(operation);
        } catch (RuleViolation failure) { finish(operation, Outcome.REVIEW, failure.key()); }
        catch (DatabaseManager.StaleGenerationException failure) { finish(operation, Outcome.RETRY, "reward-claim-cancelled"); }
        catch (RuntimeException failure) { failed(operation, failure); }
    }
    private void deliver(Operation operation) {
        var ticket = operation.claim.ticket();
        EconomyGateway gateway = economy;
        if (ticket.money() > 0 && gateway == null) {
            finish(operation, Outcome.RETRY, "reward-economy-unavailable"); return;
        }
        scheduler.player(operation.actor.liveId(), player -> {
            if (!current(operation) || player.isDead()
                    || !marriages.directory().capture(player).id().equals(operation.actor.id())) {
                finish(operation, Outcome.RETRY, "reward-claim-cancelled"); return;
            }
            if (!operation.attempt.startEffects()) return;
            try {
                if (ticket.experience() > 0) player.giveExp(Math.toIntExact(ticket.experience()));
                scheduler.runGlobal(() -> payAndRunCommands(operation, gateway));
            } catch (RuntimeException failure) { failed(operation, failure); }
        }, () -> finish(operation, Outcome.RETRY, "offline"));
    }
    private void payAndRunCommands(Operation operation, EconomyGateway gateway) {
        if (!current(operation)) { finish(operation, Outcome.RETRY, "reward-claim-cancelled"); return; }
        var ticket = operation.claim.ticket();
        try {
            // liveId 是 Bukkit 提供的实际 UUID；OFFLINE_NAME 的内部持久 ID 不能当作经济账户。
            if (ticket.money() > 0 && !gateway.deposit(operation.actor.liveId(), ticket.money())) {
                finish(operation, Outcome.REVIEW, "reward-economy-failed"); return;
            }
            for (String command : ticket.commands()) {
                if (!current(operation)) { finish(operation, Outcome.REVIEW, "delivery-review"); return; }
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", operation.actor.name()))) {
                    finish(operation, Outcome.REVIEW, "delivery-review"); return;
                }
            }
            finish(operation, Outcome.CONSUMED, "reward-received");
        } catch (RuntimeException failure) { failed(operation, failure); }
    }
    private void failed(Operation operation, RuntimeException failure) {
        logger.log(Level.SEVERE, "奖励领取中断，票据结果需核对；不自动重复发放", failure);
        finish(operation, Outcome.REVIEW, "delivery-review");
    }
    private void finish(Operation operation, Outcome outcome, String message) {
        synchronized (operation) {
            if (!operation.attempt.active()) return;
            operation.message = message; operation.attempt.finish(outcome);
            if (outcome == Outcome.RETRY && operation.attempt.outcome() == Outcome.REVIEW) operation.message = "delivery-review";
        }
        if (operation.timeout != null) operation.timeout.cancel();
        // 预占事务可能仍在运行：其回调负责补交清理，当前立即释放用户等待状态。
        if (operation.claim == null) conclude(operation, true, operation.message);
        else finalizeLater(operation);
    }
    private void finalizeLater(Operation operation) {
        var claim = operation.claim;
        if (claim == null || operation.attempt.active() || !operation.finalQueued.compareAndSet(false, true)) return;
        if (!io.submit(() -> {
            try {
                boolean confirmed = switch (operation.attempt.outcome()) {
                    case RETRY -> ledger.restore(claim, System.currentTimeMillis());
                    case REVIEW -> ledger.review(claim, System.currentTimeMillis());
                    case CONSUMED -> ledger.complete(claim, System.currentTimeMillis());
                };
                if (!confirmed) logger.warning("奖励票据未确认：" + operation.id + "；旧代次或状态已改变，保留记录供核对。");
                conclude(operation, true, confirmed ? operation.message : "delivery-review");
            } catch (RuntimeException failure) {
                logger.log(Level.SEVERE, "奖励确认事务失败，保留领取中记录，不自动重试发奖", failure);
                conclude(operation, true, "delivery-review");
            }
        })) {
            logger.warning("奖励确认队列不可用，保留票据：" + operation.id);
            conclude(operation, true, "delivery-review");
        }
    }
    private void conclude(Operation operation, boolean handled, String message) {
        pending.remove(operation.actor.liveId(), operation);
        if (operation.timeout != null) operation.timeout.cancel();
        if (!operation.replied.compareAndSet(false, true)) return;
        if (!closed && message != null) marriages.notifyLive(operation.actor.liveId(), message);
        try { operation.next.accept(handled || closed); }
        catch (RuntimeException failure) { logger.log(Level.WARNING, "奖励领取后续回调失败", failure); }
    }
    @Override public void close() {
        closed = true;
        for (var operation : List.copyOf(pending.values())) {
            finish(operation, Outcome.RETRY, "delivery-review");
            conclude(operation, true, null);
        }
        pending.clear(); economy = null;
        var refresh = economyRefresh;
        if (refresh != null) refresh.cancel();
        // 禁用时已取消的 IO 不再强制等待；CLAIMING 记录留存，不自动重新发奖。
    }
}
