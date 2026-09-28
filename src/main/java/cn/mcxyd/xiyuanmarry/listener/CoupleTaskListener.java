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
    private final BrewCreditRegistry brewCredits = new BrewCreditRegistry();
    private final ExplorationTracker exploration = new ExplorationTracker();
    private final Map<UUID, Long> inventorySessions = new ConcurrentHashMap<>();
    private final Map<UUID, TaskHandle> samplers = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public CoupleTaskListener(MarriageService m, DailyTaskService t, UnifiedScheduler s) { marriages = m; tasks = t; scheduler = s; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void block(BlockPlaceEvent e) { record(e.getPlayer(), "PLACE_BLOCK", e.getBlock().getType().name(), 1); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) { record(e.getPlayer(), "BREAK_BLOCK", e.getBlock().getType().name(), 1); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void kill(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        String category = e.getEntity() instanceof Animals ? "KILL_ANIMAL" : e.getEntity() instanceof Enemy ? "KILL_MONSTER" : null;
        String value = e.getEntityType().name();
        scheduler.runEntity(killer, () -> { if (!killer.isOnline()) return; PlayerSnapshot s = marriages.directory().capture(killer);
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
        Player p = e.getPlayer(); int baseline = p.getStatistic(Statistic.TRADED_WITH_VILLAGER);
        scheduler.runEntityLater(p, () -> { if (!p.isOnline()) return; int delta = p.getStatistic(Statistic.TRADED_WITH_VILLAGER) - baseline;
            if (delta > 0) record(p, "TRADE", "*", Math.min(delta, 64)); }, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void inventoryResult(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        UUID id = p.getUniqueId(); long session = inventorySessions.getOrDefault(id, 0L);
        if (top.getType() == InventoryType.ANVIL && e.getRawSlot() == 2 && e.getAction() != InventoryAction.NOTHING) {
            ItemStack result = top.getItem(2), first = top.getItem(0), second = top.getItem(1);
            if (empty(result)) return;
            String resultSig = signature(result), firstSig = signature(first), secondSig = signature(second);
            scheduler.runEntityLater(p, () -> {
                if (!p.isOnline() || inventorySessions.getOrDefault(id, 0L) != session) return;
                Inventory after = p.getOpenInventory().getTopInventory();
                if (after.getType() != InventoryType.ANVIL) return;
                boolean resultStillPresent = signature(after.getItem(2)).equals(resultSig);
                boolean inputsUnchanged = signature(after.getItem(0)).equals(firstSig) && signature(after.getItem(1)).equals(secondSig);
                if (!resultStillPresent && !inputsUnchanged) record(p, "ANVIL", "*", 1);
            }, 1);
        } else if (top.getType() == InventoryType.BREWING && e.getRawSlot() >= 0 && e.getRawSlot() <= 2) {
            int clickedSlot=e.getRawSlot();ItemStack before = top.getItem(clickedSlot); if (empty(before) || top.getHolder() == null) return;
            String slot = blockKey(top) + ":" + clickedSlot, sig = signature(before); int amount = before.getAmount();
            scheduler.runEntityLater(p, () -> { if (!p.isOnline() || inventorySessions.getOrDefault(id, 0L) != session) return;
                ItemStack after = p.getOpenInventory().getTopInventory().getItem(clickedSlot); int left = after == null ? 0 : after.getAmount();
                if (left < amount) { var credit = brewCredits.peek(slot, sig, System.currentTimeMillis());
                    if (credit != null && brewCredits.consume(slot, credit.token(), sig, System.currentTimeMillis())) record(p, "BREW", "*", Math.min(amount - left, 64)); }
            }, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void brewed(BrewEvent e) {
        String key = blockKey(e.getBlock()); List<String> expected = e.getResults().stream().map(CoupleTaskListener::signature).toList();
        var location = e.getBlock().getLocation().clone();
        scheduler.runRegionLater(location, () -> {
            if (closed || !(location.getBlock().getState() instanceof BrewingStand stand)) return;
            BrewerInventory contents = stand.getInventory();
            for (int i = 0; i < Math.min(3, expected.size()); i++) {
                ItemStack current = contents.getItem(i);
                if (!empty(current) && signature(current).equals(expected.get(i))) brewCredits.produce(key + ":" + i, expected.get(i), System.currentTimeMillis());
            }
        }, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hopper(InventoryMoveItemEvent e) {
        if (e.getSource().getType() == InventoryType.BREWING) brewCredits.clearBlock(blockKey(e.getSource()));
        if (e.getDestination().getType() == InventoryType.BREWING) brewCredits.clearBlock(blockKey(e.getDestination()));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void inventoryOpen(InventoryOpenEvent e) { if (e.getPlayer() instanceof Player p) inventorySessions.merge(p.getUniqueId(), 1L, Long::sum); }
    @EventHandler(priority = EventPriority.MONITOR) public void inventoryClose(InventoryCloseEvent e) { if (e.getPlayer() instanceof Player p) inventorySessions.merge(p.getUniqueId(), 1L, Long::sum); }

    @EventHandler(priority = EventPriority.MONITOR) public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer(); TaskHandle old = samplers.put(p.getUniqueId(), scheduler.repeatEntity(p, () -> sample(p), 20)); if (old != null) old.cancel();
    }
    private void sample(Player p) {
        if (closed || !p.isOnline()) return;
        boolean owned = Bukkit.isOwnedByCurrentRegion(p.getLocation());
        String biome = owned ? p.getLocation().getBlock().getBiome().getKey().toString() : null;
        PlayerSnapshot s = marriages.directory().capture(p);
        var progress = exploration.sample(p.getUniqueId(), p.getStatistic(Statistic.AVIATE_ONE_CM), p.isGliding(), p.getWorld().getUID(), biome,
            System.currentTimeMillis(), owned && tasks.nearbyPartner(s));
        if (progress.metres() > 0) tasks.record(s, "ELYTRA", "*", progress.metres());
        if (progress.biome() != null) tasks.record(s, "BIOME", progress.biome(), 1);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void quit(PlayerQuitEvent e) { clear(e.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void death(PlayerDeathEvent e) { clear(e.getEntity().getUniqueId()); }
    private void clear(UUID id) { TaskHandle t = samplers.remove(id); if (t != null) t.cancel(); exploration.forget(id); inventorySessions.remove(id); }

    private void record(Player p, String type, String value, long amount) { if (p != null && p.isOnline() && amount > 0) tasks.record(marriages.directory().capture(p), type, value, amount); }
    private void route(Player p, String type, String value, long amount) { scheduler.runEntity(p, () -> record(p, type, value, amount)); }
    private static boolean empty(ItemStack item) { return item == null || item.getType().isAir() || item.getAmount() <= 0; }
    private static String signature(ItemStack item) { return empty(item) ? "" : Base64.getEncoder().encodeToString(item.serializeAsBytes()); }
    private static String blockKey(Block b) { return b.getWorld().getUID() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ(); }
    private static String blockKey(Inventory inventory) { return inventory.getHolder() instanceof BrewingStand stand ? blockKey(stand.getBlock()) : "unknown"; }
    @Override public void close() { closed = true; samplers.values().forEach(TaskHandle::cancel); samplers.clear(); exploration.clear(); inventorySessions.clear(); brewCredits.clear(); }
    public void reload() { exploration.clear(); brewCredits.clear(); inventorySessions.clear(); }
}

