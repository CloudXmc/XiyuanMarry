package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.model.MarriageRecord;
import cn.mcxyd.xiyuanmarry.model.MarriageState;
import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.model.WeddingPlan;
import cn.mcxyd.xiyuanmarry.repository.MarriageRepository;
import cn.mcxyd.xiyuanmarry.repository.DatabaseManager;
import cn.mcxyd.xiyuanmarry.repository.TransactionRollbackException;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import com.google.gson.Gson;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;

import static cn.mcxyd.xiyuanmarry.service.RuleViolation.require;

/** 礼物状态先持久化再确认领取；物品快照只跨异步边界传递，不跨线程持有 Inventory。 */
public final class GiftService implements AutoCloseable {
    public static final String BUCKET = "gift-inbox";
    private static final long CLAIM_TIMEOUT = 5 * 60_000L;
    private static final int MAX_BYTES = 40_000;
    private final MarriageService marriages;
    private final UnifiedScheduler scheduler;
    private final DatabaseManager database;
    private final Gson gson;
    private final ConcurrentHashMap<UUID, GiftSendAttempt> pendingSends = new ConcurrentHashMap<>();
    private final Object sendLifecycle = new Object();
    private volatile boolean closed;

    private record Gift(UUID id, UUID sender, UUID recipient, long created, long updated, String state, String item, String kind, String weddingId, UUID claimToken, UUID claimGeneration) {}
    public record Pending(UUID id, UUID sender, long created, boolean weddingGift) {}

    public GiftService(MarriageService marriages, UnifiedScheduler scheduler) {
        this(marriages, scheduler, null);
    }

    public GiftService(MarriageService marriages, UnifiedScheduler scheduler, DatabaseManager database) {
        this.marriages = marriages; this.scheduler = scheduler; this.database = database; this.gson = marriages.json();
    }

    public void send(PlayerSnapshot actor) {
        if (closed) return;
        Player player = org.bukkit.Bukkit.getPlayer(actor.liveId());
        if (player == null || !player.isOnline()) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir() || held.getAmount() <= 0) { marriages.notifyLive(actor.liveId(), "gift-empty"); return; }
        byte[] bytes = held.serializeAsBytes();
        if (bytes.length > MAX_BYTES) { marriages.notifyLive(actor.liveId(), "gift-too-large"); return; }
        var marriage = marriages.view().byPlayer().get(actor.id());
        if (marriage == null || !marriage.married() || marriage.state() != MarriageState.MARRIED) { marriages.notifyLive(actor.liveId(), "married-required"); return; }
        UUID recipient = marriage.partnerOf(actor.id());
        Gift gift = new Gift(UUID.randomUUID(), actor.id(), recipient, System.currentTimeMillis(), System.currentTimeMillis(), "RESERVED", Base64.getEncoder().encodeToString(bytes), "PARTNER", marriage.id(), null, null);
        GiftSendAttempt pending = new GiftSendAttempt(actor.liveId(), bytes, database == null ? marriages.databaseGeneration() : database.generation());
        if (!track(gift.id(), pending)) return;
        player.getInventory().setItemInMainHand(null);
        marriages.submitAtGeneration(actor.liveId(), pending.generation(), r -> {
            if (!pending.begin()) throw new CancellationException("礼物发送已取消");
            MarriageRecord current = r.findByPlayer(actor.id());
            require(current != null && current.state() == MarriageState.MARRIED && current.id().equals(marriage.id()), "married-required");
            r.put(BUCKET, gift.id().toString(), gson.toJson(gift));
            return gift;
        }, committed -> confirmSend(gift, pending, "gift-sent"),
                error -> failSend(gift.id(), pending, error), pending::committed);
    }

    public void weddingGift(PlayerSnapshot guest, UUID newlywed) {
        if (closed) return;
        Player player = org.bukkit.Bukkit.getPlayer(guest.liveId());
        if (player == null || !player.isOnline()) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir() || held.getAmount() <= 0) { marriages.notifyLive(guest.liveId(), "gift-empty"); return; }
        byte[] bytes = held.serializeAsBytes();
        if (bytes.length > MAX_BYTES) { marriages.notifyLive(guest.liveId(), "gift-too-large"); return; }
        MarriageRecord engagement = marriages.view().byPlayer().get(newlywed);
        if (engagement == null || engagement.state() != MarriageState.ENGAGED || !engagement.type().equals("WEDDING")) { marriages.notifyLive(guest.liveId(), "wedding-gift-invalid"); return; }
        Gift gift = new Gift(UUID.randomUUID(), guest.id(), newlywed, System.currentTimeMillis(), System.currentTimeMillis(), "RESERVED", Base64.getEncoder().encodeToString(bytes), "WEDDING", engagement.id(), null, null);
        GiftSendAttempt pending = new GiftSendAttempt(guest.liveId(), bytes, database == null ? marriages.databaseGeneration() : database.generation());
        if (!track(gift.id(), pending)) return;
        player.getInventory().setItemInMainHand(null);
        marriages.submitAtGeneration(guest.liveId(), pending.generation(), r -> {
            if (!pending.begin()) throw new CancellationException("礼物发送已取消");
            MarriageRecord current = r.findByPlayer(newlywed);
            String planJson = r.get("weddings", engagement.id());
            WeddingPlan plan = planJson == null ? WeddingPlan.empty() : gson.fromJson(planJson, WeddingPlan.class);
            require(current != null && current.id().equals(engagement.id())
                    && WeddingGiftPolicy.canSend(plan, guest.id(), current, System.currentTimeMillis()), "wedding-gift-invalid");
            r.put(BUCKET, gift.id().toString(), gson.toJson(gift));
            return gift;
        }, reserved -> confirmSend(gift, pending, "wedding-gift-sent"),
                error -> failSend(gift.id(), pending, error), pending::committed);
    }

    private boolean track(UUID id, GiftSendAttempt pending) {
        // 此短锁内不调度、不读写库存、不执行 IO；关闭后不再登记新尝试。
        synchronized (sendLifecycle) {
            if (closed) return false;
            pendingSends.put(id, pending);
            return true;
        }
    }

    private void confirmSend(Gift gift, GiftSendAttempt pending, String message) {
        if (closed) { reviewSend(gift.id(), pending); return; }
        marriages.submitAtGeneration(null, pending.generation(), r -> {
            if (closed || !pending.persisted()) return false;
            Gift current = read(r, gift.id());
            if (current == null || !"RESERVED".equals(current.state())) return false;
            r.put(BUCKET, gift.id().toString(), gson.toJson(withState(current, "COMMITTED")));
            return true;
        }, ok -> {
            if (!ok) { reviewSend(gift.id(), pending); return; }
            pending.finish();
            pendingSends.remove(gift.id(), pending);
            if (!closed) {
                marriages.notifyLive(pending.liveId(), message);
                marriages.notifyIdentity(gift.recipient(), "reward-pending");
            }
        }, error -> reviewSend(gift.id(), pending));
    }

    public void list(PlayerSnapshot actor) {
        marriages.submit(actor.liveId(), r -> inboxMessages(r, actor.id()), inbox -> scheduler.player(actor.liveId(), p -> {
            for (var notice : inbox) marriages.notifyLive(actor.liveId(), notice.key(), notice.values().toArray());
        }));
    }

    List<InboxMessage> inboxMessages(MarriageRepository repository, UUID recipient) {
        recover(repository, System.currentTimeMillis());
        var pending = new ArrayList<Pending>();
        boolean review = false;
        MarriageRecord current = repository.findByPlayer(recipient);
        for (Gift gift : all(repository)) if (visibleTo(gift, recipient, current)) {
            if ("COMMITTED".equals(gift.state())) pending.add(new Pending(gift.id(), gift.sender(), gift.created(), isWedding(gift)));
            else if ("CLAIMING".equals(gift.state()) || "REVIEW".equals(gift.state())) review = true;
        }
        pending.sort(Comparator.comparingLong(Pending::created).thenComparing(Pending::id));
        var result = new ArrayList<InboxMessage>();
        if (pending.isEmpty() && !review) result.add(InboxMessage.of("gift-none"));
        if (!pending.isEmpty()) {
            result.add(InboxMessage.of("gift-pending", "count", pending.size()));
            for (var item : pending) result.add(InboxMessage.of(item.weddingGift() ? "wedding-gift-id" : "gift-id", "id", item.id(), "sender", marriages.name(item.sender())));
        }
        if (review) result.add(InboxMessage.of("delivery-review"));
        return List.copyOf(result);
    }

    public void claim(PlayerSnapshot actor, UUID id) {
        if (closed) return;
        UUID generation = database == null ? marriages.databaseGeneration() : database.generation();
        marriages.submitAtGeneration(actor.liveId(), generation, r -> { recover(r, System.currentTimeMillis()); Gift gift = read(r, id); MarriageRecord current = r.findByPlayer(actor.id()); require(gift != null && visibleTo(gift, actor.id(), current), "gift-missing"); require(!gift.state().equals("CLAIMING"), "delivery-review"); require(gift.state().equals("COMMITTED"), "gift-missing"); Gift claiming = claiming(gift, UUID.randomUUID(), generation); r.put(BUCKET, id.toString(), gson.toJson(claiming)); return claiming; }, gift -> scheduler.player(actor.liveId(), p -> insert(p, gift, actor, generation), () -> markReview(id, giftToken(gift), generation)), error -> marriages.notifyLive(actor.liveId(), error instanceof DatabaseManager.StaleGenerationException ? "delivery-review" : "gift-missing"));
    }

    private void insert(Player player, Gift gift, PlayerSnapshot actor, UUID generation) {
        if (closed || (database != null ? !database.isCurrent(generation) : !generation.equals(marriages.databaseGeneration()))
                || !player.isOnline() || player.isDead()) {
            markReview(gift.id(), gift.claimToken(), generation);
            return;
        }
        PlayerSnapshot current = marriages.directory().capture(player);
        if (!current.id().equals(actor.id()) || !current.identityKey().equals(actor.identityKey())) { markReview(gift.id(), gift.claimToken(), generation); marriages.notifyLive(actor.liveId(), "delivery-review"); return; }
        ItemStack item;
        try { item = ItemStack.deserializeBytes(Base64.getDecoder().decode(gift.item())); } catch (RuntimeException ex) { markReview(gift.id(), gift.claimToken(), generation); marriages.notifyLive(player.getUniqueId(), "gift-invalid"); return; }
        ItemStack[] before = cloneContents(player.getInventory().getContents());
        var leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) { player.getInventory().setContents(before); releaseClaim(gift.id(), gift.claimToken(), generation); marriages.notifyLive(player.getUniqueId(), "inventory-full"); return; }
        UUID live = actor.liveId();
        marriages.submitAtGeneration(null, generation, r -> { Gift currentGift = read(r, gift.id()); if (currentGift == null || !currentGift.state().equals("CLAIMING") || !Objects.equals(currentGift.claimToken(), gift.claimToken()) || !Objects.equals(currentGift.claimGeneration(), generation)) return false; r.remove(BUCKET, gift.id().toString()); return true; }, ok -> { if (ok) marriages.notifyLive(live, "reward-received"); else marriages.notifyLive(live, "delivery-review"); }, error -> marriages.notifyLive(live, "delivery-review"));
    }

    private void restore(UUID live, byte[] bytes) { scheduler.player(live, p -> { ItemStack item = ItemStack.deserializeBytes(bytes); var leftovers = p.getInventory().addItem(item); if (!leftovers.isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(), leftovers.values().iterator().next()); }); }
    private void failSend(UUID id, GiftSendAttempt pending, Throwable error) {
        pendingSends.remove(id, pending);
        if (pending.returnOnce(TransactionRollbackException.confirmed(error))) {
            // 回滚已确认时没有票据可删；更不能在新数据库里按旧编号删除。
            restore(pending.liveId(), pending.item());
        } else reviewSend(id, pending);
    }
    private void reviewSend(UUID id, GiftSendAttempt pending) {
        pendingSends.remove(id, pending);
        if (pending.review()) {
            markReservedReview(id, pending.generation());
            marriages.notifyLive(pending.liveId(), "delivery-review");
        }
    }
    private void releaseClaim(UUID id, UUID token, UUID generation) { marriages.submitAtGeneration(null, generation, r -> { Gift gift = read(r, id); if (gift != null && gift.state().equals("CLAIMING") && Objects.equals(gift.claimToken(), token) && Objects.equals(gift.claimGeneration(), generation)) r.put(BUCKET, id.toString(), gson.toJson(withState(gift, "COMMITTED"))); return null; }, x -> {} , x -> {}); }
    private void markReview(UUID id, UUID token, UUID generation) { marriages.submitAtGeneration(null, generation, r -> { Gift gift = read(r, id); if (gift != null && gift.state().equals("CLAIMING") && Objects.equals(gift.claimToken(), token) && Objects.equals(gift.claimGeneration(), generation)) r.put(BUCKET, id.toString(), gson.toJson(withState(gift, "REVIEW"))); return null; }, x -> {} , x -> {}); }
    private void markReservedReview(UUID id, UUID generation) { marriages.submitAtGeneration(null, generation, r -> { Gift gift = read(r, id); if (gift != null && gift.state().equals("RESERVED")) r.put(BUCKET, id.toString(), gson.toJson(withState(gift, "REVIEW"))); return null; }, x -> {}, x -> {}); }
    private void recover(MarriageRepository r, long now) { for (Gift gift : all(r)) if (gift.state().equals("RESERVED") && now - gift.updated() > CLAIM_TIMEOUT) r.put(BUCKET, gift.id().toString(), gson.toJson(withState(gift, "REVIEW"))); }
    private Gift withState(Gift gift, String state) { return new Gift(gift.id(), gift.sender(), gift.recipient(), gift.created(), System.currentTimeMillis(), state, gift.item(), gift.kind(), gift.weddingId(), null, null); }
    private Gift claiming(Gift gift, UUID token, UUID generation) { return new Gift(gift.id(), gift.sender(), gift.recipient(), gift.created(), System.currentTimeMillis(), "CLAIMING", gift.item(), gift.kind(), gift.weddingId(), token, generation); }
    private UUID giftToken(Gift gift) { return gift == null ? null : gift.claimToken(); }
    private boolean isWedding(Gift gift) { return "WEDDING".equals(gift.kind()); }
    private boolean visibleTo(Gift gift, UUID actor, MarriageRecord current) {
        if (!isWedding(gift)) return gift.recipient().equals(actor);
        return WeddingGiftPolicy.canClaim(gift.weddingId() == null ? null : UUID.fromString(gift.weddingId()), actor, current);
    }
    private Gift read(MarriageRepository r, UUID id) { String raw = r.get(BUCKET, id.toString()); return raw == null ? null : gson.fromJson(raw, Gift.class); }
    private List<Gift> all(MarriageRepository r) { List<Gift> out = new ArrayList<>(); for (String raw : r.entries(BUCKET).values()) { try { Gift gift = gson.fromJson(raw, Gift.class); if (gift != null && gift.id() != null) out.add(gift); } catch (RuntimeException ignored) {} } return out; }
    private static ItemStack[] cloneContents(ItemStack[] source) { ItemStack[] copy = new ItemStack[source.length]; for (int i = 0; i < source.length; i++) copy[i] = source[i] == null ? null : source[i].clone(); return copy; }

    @Override public void close() {
        Map<UUID, GiftSendAttempt> closing;
        synchronized (sendLifecycle) {
            closed = true;
            closing = Map.copyOf(pendingSends);
            pendingSends.clear();
        }
        for (var entry : closing.entrySet()) {
            GiftSendAttempt pending = entry.getValue();
            if (pending.returnOnce(false)) restore(pending.liveId(), pending.item());
            else reviewSend(entry.getKey(), pending);
        }
    }
}
