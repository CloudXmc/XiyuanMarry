package cn.mcxyd.xiyuanmarry.listener;

import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import cn.mcxyd.xiyuanmarry.service.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import io.papermc.paper.registry.RegistryAccess;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 不直接修改计数器，真实触发点击监听器后执行下一 tick 的确认回调。 */
class CoupleTaskInventoryConfirmationTest {
    final MarriageService marriages=mock(MarriageService.class);
    final PlayerDirectory directory=mock(PlayerDirectory.class);
    final DailyTaskService tasks=mock(DailyTaskService.class);
    final UnifiedScheduler scheduler=mock(UnifiedScheduler.class);
    final Player player=mock(Player.class);
    final Inventory inventory=mock(Inventory.class);
    final InventoryView view=mock(InventoryView.class);
    final List<Runnable> callbacks=new ArrayList<>();
    final UUID id=UUID.randomUUID();
    final PlayerSnapshot snapshot=new PlayerSnapshot(id,id,"name:test","Test",60,null,1);
    CoupleTaskListener listener;
    BrewerInventory brewing;
    @BeforeAll static void initializeMenus() {
        // Paper 菜单枚举在类初始化时读取注册表；仅模拟此测试不涉及的菜单元数据。
        try (MockedStatic<RegistryAccess> access = mockStatic(RegistryAccess.class)) {
            var registryAccess = mock(RegistryAccess.class, c -> mock(Registry.class));
            access.when(RegistryAccess::registryAccess).thenReturn(registryAccess);
            InventoryType.values();
        }
    }
    @BeforeEach void setup() {
        when(marriages.directory()).thenReturn(directory); when(directory.capture(player)).thenReturn(snapshot);
        when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true);
        when(player.getOpenInventory()).thenReturn(view); when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getType()).thenReturn(InventoryType.ANVIL);
        when(scheduler.runEntityLater(eq(player),any(),eq(1L))).thenAnswer(c -> {callbacks.add(c.getArgument(1));return mock(TaskHandle.class);});
        when(scheduler.runRegionLater(any(),any(),eq(1L))).thenAnswer(c -> {callbacks.add(c.getArgument(1));return mock(TaskHandle.class);});
        listener=new CoupleTaskListener(marriages,tasks,scheduler); ready();
    }
    ItemStack item(int signature) {
        var item=mock(ItemStack.class); when(item.getType()).thenReturn(Material.IRON_SWORD);
        when(item.getAmount()).thenReturn(1); when(item.serializeAsBytes()).thenReturn(new byte[]{(byte)signature});return item;
    }
    void ready() { var first=item(1);var result=item(2);when(inventory.getItem(0)).thenReturn(first); when(inventory.getItem(1)).thenReturn(null);when(inventory.getItem(2)).thenReturn(result); }
    void click(InventoryAction action) {
        var event=mock(InventoryClickEvent.class);when(event.getWhoClicked()).thenReturn(player);when(event.getView()).thenReturn(view);
        when(event.getRawSlot()).thenReturn(2);when(event.getAction()).thenReturn(action);listener.inventoryResult(event);
    }
    void taken() {when(inventory.getItem(0)).thenReturn(null);when(inventory.getItem(2)).thenReturn(null);}
    void drain() {var batch=List.copyOf(callbacks);callbacks.clear();batch.forEach(Runnable::run);}
    void closedWindow() {var e=mock(InventoryCloseEvent.class);when(e.getPlayer()).thenReturn(player);listener.inventoryClose(e);}
    void brew() {
        var world=mock(World.class);var block=mock(Block.class);var stand=mock(BrewingStand.class);
        brewing=mock(BrewerInventory.class);var potion=item(10);
        when(world.getUID()).thenReturn(UUID.randomUUID());when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(block.getWorld()).thenReturn(world);when(block.getLocation()).thenReturn(new Location(world,0,64,0));
        when(block.getState()).thenReturn(stand);when(stand.getBlock()).thenReturn(block);when(stand.getInventory()).thenReturn(brewing);
        when(brewing.getType()).thenReturn(InventoryType.BREWING);when(brewing.getHolder()).thenReturn(stand);
        when(brewing.getItem(2)).thenReturn(potion);when(view.getTopInventory()).thenReturn(brewing);
        var e=mock(BrewEvent.class);when(e.getBlock()).thenReturn(block);when(e.getResults()).thenReturn(List.of(potion,potion,potion));
        listener.brewed(e);
    }
    @AfterEach void cleanup() {if(listener!=null)listener.close();}

    @Test void repeatedClicksConfirmOneConsumedAnvilResultOnce() {
        click(InventoryAction.PICKUP_ALL);click(InventoryAction.PICKUP_ALL);taken();drain();
        verify(tasks).record(snapshot,"ANVIL","*",1);verifyNoMoreInteractions(tasks);
    }
    @Test void unchangedResultDoesNotCountAndNextOperationCanSucceed() {
        click(InventoryAction.PICKUP_ALL);drain();verifyNoInteractions(tasks);
        click(InventoryAction.PICKUP_ALL);taken();drain();verify(tasks).record(snapshot,"ANVIL","*",1);
    }
    @Test void resultOnlyChangeWithoutConsumingInputsDoesNotCount() {
        click(InventoryAction.PICKUP_ALL);var changed=item(3);when(inventory.getItem(2)).thenReturn(changed);drain();verifyNoInteractions(tasks);
    }
    @Test void oldAnvilCallbackCannotCountAfterReload() {
        click(InventoryAction.PICKUP_ALL);listener.reload();taken();drain();verifyNoInteractions(tasks);
    }
    @Test void oldAnvilCallbackCannotCountAfterQuit() {
        click(InventoryAction.PICKUP_ALL);var e=mock(PlayerQuitEvent.class);when(e.getPlayer()).thenReturn(player);
        listener.quit(e);taken();drain();verifyNoInteractions(tasks);
    }
    @Test void oldAnvilCallbackCannotConsumeNewWindowConfirmation() {
        click(InventoryAction.PICKUP_ALL);listener.reload();ready();click(InventoryAction.PICKUP_ALL);taken();drain();
        verify(tasks).record(snapshot,"ANVIL","*",1);verifyNoMoreInteractions(tasks);
    }
    @Test void closeWindowRejectsPreviousConfirmation() {
        click(InventoryAction.PICKUP_ALL);closedWindow();taken();drain();verifyNoInteractions(tasks);
    }
    @Test void closedListenerDoesNotReadInventoryInLateCallback() {
        click(InventoryAction.PICKUP_ALL);listener.close();clearInvocations(player);drain();
        verify(player,never()).getOpenInventory();verifyNoInteractions(tasks);
    }
    @Test void cancelledSchedulingCanBeRetriedWithoutRetainingConfirmation() {
        when(scheduler.runEntityLater(eq(player),any(),eq(1L))).thenThrow(new IllegalStateException("stopped"));
        assertThrows(IllegalStateException.class,()->click(InventoryAction.PICKUP_ALL));
        doAnswer(c -> {callbacks.add(c.getArgument(1));return mock(TaskHandle.class);}).when(scheduler).runEntityLater(eq(player),any(),eq(1L));
        click(InventoryAction.PICKUP_ALL);taken();drain();verify(tasks).record(snapshot,"ANVIL","*",1);
    }
    @Test void realBrewThenConsumedPotionCountsOnlyOnce() {
        brew();drain();click(InventoryAction.PICKUP_ALL);click(InventoryAction.PICKUP_ALL);
        when(brewing.getItem(2)).thenReturn(null);drain();
        verify(tasks).record(snapshot,"BREW","*",1);verifyNoMoreInteractions(tasks);
    }
    @Test void preReloadBrewCallbackCannotMintNewCredit() {
        brew();listener.reload();drain();click(InventoryAction.PICKUP_ALL);
        when(brewing.getItem(2)).thenReturn(null);drain();verifyNoInteractions(tasks);
    }
    @Test void postReloadPickupCallbackCannotReadNewWindow() {
        brew();drain();click(InventoryAction.PICKUP_ALL);listener.reload();clearInvocations(player);drain();
        verify(player,never()).getOpenInventory();verifyNoInteractions(tasks);
    }
    @Test void ineffectiveClickCannotClaimPotionRemovedBySomeoneElse() {
        brew();drain();click(InventoryAction.NOTHING);when(brewing.getItem(2)).thenReturn(null);
        drain();verifyNoInteractions(tasks);
    }
}
