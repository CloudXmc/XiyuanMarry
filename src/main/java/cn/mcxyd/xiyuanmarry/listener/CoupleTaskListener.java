package cn.mcxyd.xiyuanmarry.listener;

import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.scheduler.TaskHandle;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import cn.mcxyd.xiyuanmarry.service.*;
import io.papermc.paper.event.inventory.ItemCraftedEvent;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;

import java.util.*;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/** 事件只采集不可变值；所有 Inventory/Player 读取回到对应实体或区域上下文。 */
public final class CoupleTaskListener implements Listener, AutoCloseable {
    private final MarriageService marriages;
    private final DailyTaskService tasks;
    private final UnifiedScheduler scheduler;
    private volatile BrewCreditRegistry brewCredits = new BrewCreditRegistry();
    private final ExplorationTracker exploration = new ExplorationTracker();
    private final Map<UUID, UUID> inventorySessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingAnvils = new ConcurrentHashMap<>();
    private final Map<UUID, TaskHandle> samplers = new ConcurrentHashMap<>();
    private record TradeConfirmation(UUID token, int baseline) {}
    private final Map<UUID, TradeConfirmation> pendingTrades = new ConcurrentHashMap<>();
    private volatile UUID lifecycleEpoch = UUID.randomUUID();
    private volatile boolean closed;

    public CoupleTaskListener(MarriageService m, DailyTaskService t, UnifiedScheduler s) { marriages = m; tasks = t; scheduler = s; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void block(BlockPlaceEvent e) { record(e.getPlayer(), "PLACE_BLOCK", e.getBlock().getType().name(), 1); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) { record(e.getPlayer(), "BREAK_BLOCK", e.getBlock().getType().name(), 1); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void kill(EntityDeathEvent e) {
        UUID epoch = lifecycleEpoch;
        if (e.getEntity() instanceof Player) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        String category = e.getEntity() instanceof Animals ? "KILL_ANIMAL" : e.getEntity() instanceof Enemy ? "KILL_MONSTER" : null;
        String value = e.getEntityType().name();
        scheduler.runEntity(killer, () -> { if (closed || !epoch.equals(lifecycleEpoch) || !killer.isOnline() || killer.isDead()) return; PlayerSnapshot s = marriages.directory().capture(killer);
            if (s == null) return;
            if (category != null) tasks.record(s, category, value, 1); tasks.record(s, "KILL_SPECIFIC", value, 1); });
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void eat(PlayerItemConsumeEvent e) { if (e.getItem().getType().isEdible()) record(e.getPlayer(), "EAT", e.getItem().getType().name(), 1); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void enchant(EnchantItemEvent e) { record(e.getEnchanter(), "ENCHANT", e.getItem().getType().name(), 1); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void smelt(FurnaceExtractEvent e) { route(e.getPlayer(), "SMELT", e.getItemType().name(), e.getItemAmount()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void fish(PlayerFishEvent e) { if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) record(e.getPlayer(), "FISH", "*", 1); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void tame(EntityTameEvent e) { if (e.getOwner() instanceof Player p) route(p, "TAME", e.getEntityType().name(), 1); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void sleep(PlayerBedEnterEvent e) { if (e.getBedEnterResult() == PlayerBedEnterEvent.BedEnterResult.OK) record(e.getPlayer(), "SLEEP", "*", 1); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void experience(PlayerExpChangeEvent e) { if (e.getAmount() > 0) record(e.getPlayer(), "EXPERIENCE", "*", e.getAmount()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void crafted(ItemCraftedEvent e) { record(e.getPlayer(), "CRAFT", e.getCraftedItem().getType().name(), e.getCraftedItem().getAmount()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void traded(PlayerTradeEvent e) {
        Player p = e.getPlayer();
        if (closed || !p.isOnline() || p.isDead()) return;
        UUID id = p.getUniqueId();
        var confirmation = new TradeConfirmation(UUID.randomUUID(), p.getStatistic(Statistic.TRADED_WITH_VILLAGER));
        // 同一确认窗口只采一次基线，避免多次点击各自累加重叠的统计区间。
        if (pendingTrades.putIfAbsent(id, confirmation) != null) return;
        try {
            scheduler.player(id, owner -> {
                if (!pendingTrades.remove(id, confirmation) || closed || !owner.isOnline() || owner.isDead()) return;
                long delta = (long) owner.getStatistic(Statistic.TRADED_WITH_VILLAGER) - confirmation.baseline();
                if (delta > 0) record(owner, "TRADE", "*", delta);
            }, () -> pendingTrades.remove(id, confirmation));
        } catch (RuntimeException rejected) {
            pendingTrades.remove(id, confirmation);
            throw rejected;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void inventoryResult(InventoryClickEvent e) {
        if (closed || !(e.getWhoClicked() instanceof Player p) || !p.isOnline() || p.isDead() || !takesFromSlot(e.getAction())) return;
        Inventory top = e.getView().getTopInventory();
        UUID epoch = lifecycleEpoch;
        UUID id = p.getUniqueId(), session = inventorySessions.computeIfAbsent(id, ignored -> UUID.randomUUID());
        if (top.getType() == InventoryType.ANVIL && e.getRawSlot() == 2 && e.getAction() != InventoryAction.NOTHING) {
            ItemStack result = top.getItem(2), first = top.getItem(0), second = top.getItem(1);
            if (empty(result)) return;
            String resultSig = signature(result), firstSig = signature(first), secondSig = signature(second);
            // 同一玩家在下一 tick 确认前只保留一次领取，防止重复点击观察同一消耗。
            UUID confirmation = UUID.randomUUID();
            if (pendingAnvils.putIfAbsent(id, confirmation) != null) return;
            try { scheduler.runEntityLater(p, () -> {
                if (!pendingAnvils.remove(id, confirmation) || !currentInventory(p, id, session, epoch)) return;
                Inventory after = p.getOpenInventory().getTopInventory();
                if (after.getType() != InventoryType.ANVIL) return;
                boolean resultStillPresent = signature(after.getItem(2)).equals(resultSig);
                boolean inputsUnchanged = signature(after.getItem(0)).equals(firstSig) && signature(after.getItem(1)).equals(secondSig);
                if (!resultStillPresent && !inputsUnchanged) record(p, "ANVIL", "*", 1);
            }, 1); } catch (RuntimeException rejected) { pendingAnvils.remove(id, confirmation); throw rejected; }
        } else if (top.getType() == InventoryType.BREWING && e.getRawSlot() >= 0 && e.getRawSlot() <= 2) {
            int clickedSlot=e.getRawSlot();ItemStack before = top.getItem(clickedSlot); if (empty(before) || top.getHolder() == null) return;
            String slot = blockKey(top) + ":" + clickedSlot, sig = signature(before); int amount = before.getAmount();
            // 点击时绑定实际酿造批次，迟到确认不能领取后来产生的相同药水。
            var credits = brewCredits; var credit = credits.peek(slot, sig, System.currentTimeMillis());
            if (credit == null) return;
            scheduler.runEntityLater(p, () -> { if (!currentInventory(p, id, session, epoch)) return;
                ItemStack after = p.getOpenInventory().getTopInventory().getItem(clickedSlot); int left = after == null ? 0 : after.getAmount();
                if (left < amount && credits.consume(slot, credit.token(), sig, System.currentTimeMillis()))
                    record(p, "BREW", "*", Math.min(amount - left, 64));
            }, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void brewed(BrewEvent e) {
        if (closed) return;
        UUID epoch = lifecycleEpoch;
        var credits = brewCredits;
        String key = blockKey(e.getBlock()); List<String> expected = e.getResults().stream().map(CoupleTaskListener::signature).toList();
        var location = e.getBlock().getLocation().clone();
        scheduler.runRegionLater(location, () -> {
            if (closed || !epoch.equals(lifecycleEpoch) || !(location.getBlock().getState() instanceof BrewingStand stand)) return;
            BrewerInventory contents = stand.getInventory();
            for (int i = 0; i < Math.min(3, expected.size()); i++) {
                ItemStack current = contents.getItem(i);
                if (!empty(current) && signature(current).equals(expected.get(i))) credits.produce(key + ":" + i, expected.get(i), System.currentTimeMillis());
            }
        }, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hopper(InventoryMoveItemEvent e) {
        if (e.getSource().getType() == InventoryType.BREWING) brewCredits.clearBlock(blockKey(e.getSource()));
        if (e.getDestination().getType() == InventoryType.BREWING) brewCredits.clearBlock(blockKey(e.getDestination()));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void inventoryOpen(InventoryOpenEvent e) { if (!closed && e.getPlayer() instanceof Player p) invalidateInventory(p.getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR) public void inventoryClose(InventoryCloseEvent e) { if (!closed && e.getPlayer() instanceof Player p) invalidateInventory(p.getUniqueId()); }
    private void invalidateInventory(UUID id) { inventorySessions.put(id, UUID.randomUUID()); pendingAnvils.remove(id); }
    private boolean currentInventory(Player p, UUID id, UUID session, UUID epoch) {
        return !closed && epoch.equals(lifecycleEpoch) && session.equals(inventorySessions.get(id)) && p.isOnline() && !p.isDead();
    }
    private static boolean takesFromSlot(InventoryAction action) {
        return action != null && switch (action) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE, MOVE_TO_OTHER_INVENTORY,
                    HOTBAR_SWAP, HOTBAR_MOVE_AND_READD, SWAP_WITH_CURSOR, DROP_ALL_SLOT, DROP_ONE_SLOT -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.MONITOR) public void join(PlayerJoinEvent e) {
        startSampling(e.getPlayer());
    }
    /** 数据库异步就绪前可能已有玩家上线；枚举只转交实体句柄，不在全局读取玩家状态。 */
    public void bootstrap() {
        if (closed) return;
        for (Player player : Bukkit.getOnlinePlayers()) scheduler.runEntity(player, () -> startSampling(player));
    }
    private void startSampling(Player p) {
        if (closed || !p.isOnline() || samplers.containsKey(p.getUniqueId())) return;
        UUID id = p.getUniqueId();
        TaskHandle task = scheduler.repeatEntity(p, () -> sample(p), 20);
        TaskHandle previous = samplers.putIfAbsent(id, task);
        if (previous != null) task.cancel();
        else if (closed && samplers.remove(id, task)) task.cancel();
    }
    private void sample(Player p) {
        if (closed || !p.isOnline()) return;
        // 死亡期间保留采样任务，复活后自动恢复；死亡时清空基线，避免把死亡期间的统计跳变算进任务。
        if (p.isDead()) { exploration.forget(p.getUniqueId()); return; }
        boolean owned = Bukkit.isOwnedByCurrentRegion(p.getLocation());
        String biome = owned ? p.getLocation().getBlock().getBiome().getKey().toString() : null;
        PlayerSnapshot s = marriages.directory().capture(p);
        if (s == null) return;
        var progress = exploration.sample(p.getUniqueId(), p.getStatistic(Statistic.AVIATE_ONE_CM), p.isGliding(), p.getWorld().getUID(), biome,
            System.currentTimeMillis(), owned && tasks.nearbyPartner(s));
        if (progress.metres() > 0) tasks.record(s, "ELYTRA", "*", progress.metres());
        if (progress.biome() != null) tasks.record(s, "BIOME", progress.biome(), 1);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void quit(PlayerQuitEvent e) { clear(e.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void death(PlayerDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        // 实体调度器随玩家跨复活继续工作；这里只清基线和待确认操作，不能永久取消采样。
        exploration.forget(id); pendingTrades.remove(id); invalidateInventory(id);
    }
    private void clear(UUID id) { exploration.forget(id); inventorySessions.remove(id); pendingTrades.remove(id); pendingAnvils.remove(id); TaskHandle t = samplers.remove(id); if (t != null) t.cancel(); }

    private void record(Player p, String type, String value, long amount) {
        if (closed || p == null || !p.isOnline() || p.isDead() || amount <= 0) return;
        PlayerSnapshot snapshot = marriages.directory().capture(p);
        if (snapshot != null) tasks.record(snapshot, type, value, amount);
    }
    private void route(Player p, String type, String value, long amount) {
        UUID epoch = lifecycleEpoch;
        scheduler.runEntity(p, () -> { if (epoch.equals(lifecycleEpoch)) record(p, type, value, amount); });
    }
    private static boolean empty(ItemStack item) { return item == null || item.getType().isAir() || item.getAmount() <= 0; }
    private static String signature(ItemStack item) { return empty(item) ? "" : Base64.getEncoder().encodeToString(item.serializeAsBytes()); }
    private static String blockKey(Block b) { return b.getWorld().getUID() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ(); }
    private static String blockKey(Inventory inventory) { return inventory.getHolder() instanceof BrewingStand stand ? blockKey(stand.getBlock()) : "unknown"; }
    @Override public void close() { closed = true; reload(); samplers.values().forEach(TaskHandle::cancel); samplers.clear(); }
    public void reload() { lifecycleEpoch = UUID.randomUUID(); exploration.clear(); var old=brewCredits;brewCredits=new BrewCreditRegistry();old.clear(); inventorySessions.clear(); pendingTrades.clear(); pendingAnvils.clear(); }
}

