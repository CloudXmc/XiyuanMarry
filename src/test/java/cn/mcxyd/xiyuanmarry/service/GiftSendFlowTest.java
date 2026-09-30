package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.*;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GiftSendFlowTest {
    final UUID live = UUID.randomUUID(), sender = UUID.randomUUID(), recipient = UUID.randomUUID();
    final UUID generation = UUID.randomUUID();
    final PlayerSnapshot actor = new PlayerSnapshot(live, sender, "offline:sender", "Sender", 60, null, 1);
    final MarriageService marriages = mock(MarriageService.class);
    final UnifiedScheduler scheduler = mock(UnifiedScheduler.class);
    final DatabaseManager database = mock(DatabaseManager.class);
    final MarriageRepository repository = mock(MarriageRepository.class);
    final Player player = mock(Player.class);
    final PlayerInventory inventory = mock(PlayerInventory.class);
    final ItemStack item = mock(ItemStack.class);
    final Map<String,String> data = new HashMap<>();
    final Queue<Job> jobs = new ArrayDeque<>();
    final Queue<Runnable> entityTasks = new ArrayDeque<>();
    final Gson gson = new Gson();
    MockedStatic<Bukkit> bukkit; MockedStatic<ItemStack> items; GiftService gifts;

    record Job(UUID generation, Function<MarriageRepository,Object> work, Consumer<Object> committed,
               Consumer<Throwable> failed, Runnable marker) {
        Object write(MarriageRepository repository) { return work.apply(repository); }
        void finish(Object value) { marker.run(); committed.accept(value); }
    }

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        bukkit = mockStatic(Bukkit.class); items = mockStatic(ItemStack.class);
        bukkit.when(() -> Bukkit.getPlayer(live)).thenReturn(player);
        items.when(() -> ItemStack.deserializeBytes(any(byte[].class))).thenReturn(item);
        when(player.isOnline()).thenReturn(true); when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(item);
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        when(item.getType()).thenReturn(mock(Material.class)); when(item.getAmount()).thenReturn(1);
        when(item.serializeAsBytes()).thenReturn(new byte[]{1,2,3});
        when(marriages.json()).thenReturn(gson); when(marriages.name(any())).thenReturn("Sender");
        when(marriages.databaseGeneration()).thenReturn(generation);
        when(database.generation()).thenReturn(generation); when(database.isCurrent(generation)).thenReturn(true);
        when(repository.get(eq(GiftService.BUCKET), anyString())).thenAnswer(c -> data.get(c.getArgument(1)));
        when(repository.entries(GiftService.BUCKET)).thenAnswer(c -> Map.copyOf(data));
        doAnswer(c -> { data.put(c.getArgument(1), c.getArgument(2)); return null; })
                .when(repository).put(eq(GiftService.BUCKET), anyString(), anyString());
        doAnswer(c -> { data.remove(c.getArgument(1)); return null; })
                .when(repository).remove(eq(GiftService.BUCKET), anyString());
        doAnswer(c -> { jobs.add(new Job(null,c.getArgument(1),c.getArgument(2),c.getArgument(3),()->{})); return null; })
                .when(marriages).submit(any(), any(), any(), any());
        doAnswer(c -> { jobs.add(new Job(null,c.getArgument(1),c.getArgument(2),c.getArgument(3),c.getArgument(4))); return null; })
                .when(marriages).submit(any(), any(), any(), any(), any());
        doAnswer(c -> { jobs.add(new Job(c.getArgument(1),c.getArgument(2),c.getArgument(3),c.getArgument(4),()->{})); return null; })
                .when(marriages).submitAtGeneration(any(), any(), any(), any(), any());
        doAnswer(c -> { jobs.add(new Job(c.getArgument(1),c.getArgument(2),c.getArgument(3),c.getArgument(4),c.getArgument(5))); return null; })
                .when(marriages).submitAtGeneration(any(), any(), any(), any(), any(), any());
        doAnswer(c -> { Consumer<Player> action = c.getArgument(1); entityTasks.add(()->action.accept(player)); return null; })
                .when(scheduler).player(eq(live), any());
        doAnswer(c -> { Consumer<Player> action = c.getArgument(1); entityTasks.add(()->action.accept(player)); return null; })
                .when(scheduler).player(eq(live), any(), any());
        var directory = mock(PlayerDirectory.class); when(directory.capture(player)).thenReturn(actor);
        when(marriages.directory()).thenReturn(directory);
        relationship(false); gifts = new GiftService(marriages, scheduler, database);
    }
    void relationship(boolean wedding) {
        var marriage = new MarriageRecord(wedding ? UUID.randomUUID() : sender, recipient,
                wedding ? MarriageState.ENGAGED : MarriageState.MARRIED, wedding ? "WEDDING" : "NORMAL",
                1,0,0,UUID.randomUUID().toString(),1,0,0);
        when(marriages.view()).thenReturn(new MarriageService.View(Map.of(sender,marriage,recipient,marriage),Map.of(),List.of(marriage),Map.of()));
        when(repository.findByPlayer(any())).thenReturn(marriage);
        var plan = WeddingPlan.empty().invite(sender,new WeddingPlan.Invite(true,Long.MAX_VALUE));
        when(repository.get("weddings",marriage.id())).thenReturn(gson.toJson(plan));
    }
    void entities() { for (int n=0;n<30&&!entityTasks.isEmpty();n++) entityTasks.remove().run(); assertTrue(entityTasks.isEmpty()); }
    void writes() { for(int n=0;n<30&&!jobs.isEmpty();n++){var j=jobs.remove();j.finish(j.write(repository));} assertTrue(jobs.isEmpty()); }
    @AfterEach void cleanup() { if(gifts!=null)gifts.close(); if(bukkit!=null)bukkit.close(); if(items!=null)items.close(); }

    @Test void partnerReservationBindsToCapturedGeneration() { gifts.send(actor); assertEquals(generation,jobs.remove().generation()); }
    @Test void concurrentSendAttemptsFromOnePlayerCreateOnlyOneReservation() {
        gifts.send(actor); gifts.send(actor);
        assertEquals(1, jobs.size());
        verify(marriages).notifyLive(live, "busy");
    }
    @Test void weddingReservationAndConfirmationBindToSameGeneration() {
        relationship(true); gifts.weddingGift(actor,recipient); var first=jobs.remove();
        assertEquals(generation,first.generation()); first.finish(first.write(repository));
        assertEquals(generation,jobs.remove().generation());
    }
    @Test void repeatedFailureReturnsItemOnlyOnce() {
        gifts.send(actor); var first=jobs.remove();
        first.failed().accept(new DatabaseManager.StaleGenerationException());
        first.failed().accept(new DatabaseManager.StaleGenerationException());
        entities(); verify(inventory,times(1)).addItem(item);
    }
    @Test void closeThenLateFailureReturnsItemOnlyOnce() {
        gifts.send(actor); var first=jobs.remove(); gifts.close();
        first.failed().accept(new IllegalStateException("closed"));
        entities(); verify(inventory,times(1)).addItem(item);
    }
    @Test void closeBeforeTransactionPreventsLateWrite() {
        gifts.send(actor); var first=jobs.remove(); gifts.close();
        try { first.finish(first.write(repository)); } catch(RuntimeException e) { first.failed().accept(e); }
        entities(); assertTrue(data.isEmpty(),"返还之后不能再生成可领取票据");
        verify(inventory,times(1)).addItem(item);
    }
    @Test void closeDuringCommitCannotRefundInFlightItem() {
        gifts.send(actor); var first=jobs.remove(); var result=first.write(repository);
        gifts.close(); first.finish(result); writes(); entities();
        verify(inventory,never()).addItem(any(ItemStack.class));
        assertFalse(data.values().stream().anyMatch(v->v.contains("COMMITTED")),"停服后未确认票据只能待核对");
    }
    @Test void confirmationCannotOverwriteClaimingTicket() {
        gifts.send(actor); var first=jobs.remove(); first.finish(first.write(repository));
        var entry=data.entrySet().iterator().next(); var value=JsonParser.parseString(entry.getValue()).getAsJsonObject();
        value.addProperty("state","CLAIMING"); data.put(entry.getKey(),value.toString());
        writes(); assertTrue(data.get(entry.getKey()).contains("CLAIMING"));
    }
    @Test void staleReservationIsFrozenInsteadOfAutomaticallyClaimable() {
        UUID id=UUID.randomUUID(); data.put(id.toString(),gson.toJson(Map.of("id",id,"sender",sender,"recipient",recipient,"state","RESERVED","kind","PARTNER","updated",1)));
        var notices=gifts.inboxMessages(repository,recipient);
        assertTrue(data.get(id.toString()).contains("REVIEW"));
        assertTrue(notices.stream().anyMatch(n->n.key().equals("delivery-review")));
    }
    @Test void malformedWeddingIdIsIgnoredInsteadOfBreakingInbox() {
        UUID id=UUID.randomUUID();
        data.put(id.toString(),gson.toJson(Map.of("id",id,"sender",recipient,"recipient",sender,
                "state","COMMITTED","kind","WEDDING","weddingId","not-a-uuid","item","AQID")));
        var notices=assertDoesNotThrow(() -> gifts.inboxMessages(repository,sender));
        assertTrue(notices.stream().anyMatch(n->n.key().equals("gift-none")),"损坏婚礼编号不能让收件箱查询抛异常");
    }
    @Test void incompleteGiftRecordIsIgnoredInsteadOfBreakingInbox() {
        UUID id=UUID.randomUUID();
        data.put(id.toString(),gson.toJson(Map.of("id",id,"state","COMMITTED","kind","PARTNER","item","AQID")));
        var notices=assertDoesNotThrow(() -> gifts.inboxMessages(repository,sender));
        assertTrue(notices.stream().anyMatch(n->n.key().equals("gift-none")),"字段缺失的礼物记录不能让收件箱查询抛异常");
    }
    @Test void confirmedRollbackReturnsOnlyOnceAndNeverDeletesTickets() {
        gifts.send(actor); var first=jobs.remove(); first.write(repository); data.clear();
        var failure=new IllegalStateException("rolled back");
        failure.addSuppressed(new TransactionRollbackException(null));
        first.failed().accept(failure); first.failed().accept(failure);
        entities(); verify(inventory,times(1)).addItem(item);
        verify(repository,never()).remove(anyString(),anyString()); assertTrue(jobs.isEmpty());
    }
    @Test void ambiguousCommitFreezesTicketWithoutReturningItem() {
        gifts.send(actor); var first=jobs.remove(); first.write(repository);
        first.failed().accept(new IllegalStateException("commit result unknown"));
        writes(); entities(); verify(inventory,never()).addItem(any(ItemStack.class));
        assertEquals(1,data.size()); assertTrue(data.values().iterator().next().contains("REVIEW"));
    }
    @Test void completedPartnerSendSurvivesCloseWithoutRefund() {
        gifts.send(actor); writes(); gifts.close(); entities();
        assertTrue(data.values().iterator().next().contains("COMMITTED"));
        verify(inventory,never()).addItem(any(ItemStack.class));
    }
    @Test void completedWeddingSendUsesItsFirstCommitMarker() {
        relationship(true); gifts.weddingGift(actor,recipient); writes(); gifts.close(); entities();
        assertTrue(data.values().iterator().next().contains("COMMITTED"));
        verify(inventory,never()).addItem(any(ItemStack.class));
    }
    UUID claimable() {
        UUID id=UUID.randomUUID();
        data.put(id.toString(),gson.toJson(Map.of("id",id,"sender",recipient,"recipient",sender,"state","COMMITTED","kind","PARTNER","item","AQID")));
        return id;
    }
    @Test void databaseSwitchBetweenReservationAndDeliveryDoesNotInsertItems() {
        gifts.claim(actor,claimable()); writes(); when(database.isCurrent(generation)).thenReturn(false);
        entities(); verify(inventory,never()).addItem(any(ItemStack.class));
        assertTrue(jobs.stream().allMatch(j->generation.equals(j.generation())));
    }
    @Test void closeBetweenReservationAndDeliveryDoesNotInsertItems() {
        gifts.claim(actor,claimable()); writes(); gifts.close(); entities();
        verify(inventory,never()).addItem(any(ItemStack.class));
    }
    @Test void missingSnapshotRetainsGiftForReviewWithoutInsertingItems() {
        UUID gift=claimable(); gifts.claim(actor,gift); writes();
        when(marriages.directory().capture(player)).thenReturn(null);
        assertDoesNotThrow(this::entities); writes();
        verify(inventory,never()).addItem(any(ItemStack.class));
        assertTrue(data.get(gift.toString()).contains("REVIEW"));
        verify(marriages).notifyLive(live,"delivery-review");
    }
    @Test void deadPlayerDoesNotReceiveQueuedGift() {
        gifts.claim(actor,claimable()); writes(); when(player.isDead()).thenReturn(true); entities();
        verify(inventory,never()).addItem(any(ItemStack.class));
    }
    @Test void inventoryWriteFailureFreezesTicketInsteadOfRetryingItem() {
        UUID gift = claimable(); gifts.claim(actor, gift); writes();
        when(inventory.getContents()).thenReturn(new ItemStack[36]);
        when(inventory.addItem(any(ItemStack.class))).thenThrow(new IllegalStateException("inventory failure"));
        entities(); writes();
        assertTrue(data.get(gift.toString()).contains("REVIEW"));
        verify(inventory, times(1)).addItem(any(ItemStack.class));
    }
}
