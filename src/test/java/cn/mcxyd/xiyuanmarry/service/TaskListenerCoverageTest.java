package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.listener.CoupleTaskListener;
import org.bukkit.event.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TaskListenerCoverageTest {
    @Test void advertisedTaskTypesHaveMonitoredSuccessfulEventSources() {
        Set<String> present = new HashSet<>();
        for (var method : CoupleTaskListener.class.getDeclaredMethods()) {
            var annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) continue;
            assertEquals(EventPriority.MONITOR, annotation.priority());
            present.add(method.getParameterTypes()[0].getSimpleName());
            if (Cancellable.class.isAssignableFrom(method.getParameterTypes()[0])) assertTrue(annotation.ignoreCancelled(), method.getName());
        }
        Set<String> expected = Set.of("BlockPlaceEvent", "BlockBreakEvent", "EntityDeathEvent",
            "PlayerItemConsumeEvent", "EnchantItemEvent", "FurnaceExtractEvent", "PlayerFishEvent",
            "EntityTameEvent", "PlayerBedEnterEvent", "PlayerExpChangeEvent", "ItemCraftedEvent",
            "PlayerTradeEvent", "InventoryClickEvent", "BrewEvent", "InventoryMoveItemEvent",
            "PlayerJoinEvent", "PlayerQuitEvent", "PlayerDeathEvent");
        assertTrue(present.containsAll(expected), "missing=" + new HashSet<>(expected) {{ removeAll(present); }} + ", present=" + present);
        assertFalse(present.contains("PlayerMoveEvent"));
        assertFalse(present.contains("EntityToggleGlideEvent"));
    }
}
