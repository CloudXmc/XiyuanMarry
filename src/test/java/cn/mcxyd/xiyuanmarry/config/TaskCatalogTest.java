package cn.mcxyd.xiyuanmarry.config;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TaskCatalogTest {
    private YamlConfiguration yaml(String type) {
        var y = new YamlConfiguration(); y.set("tasks", List.of(Map.of("id", "build", "type", type, "name", "共筑爱巢", "target", 10))); return y;
    }
    @Test void mapsLegacyTypesAndRepeatsDefinitionsAcrossThirtyDays() {
        var catalog = TaskCatalog.parse(yaml("COMMON_PLACE_BLOCK"), 20);
        assertEquals(30, catalog.schedule().size()); assertEquals("PLACE_BLOCK", catalog.forDay(30).type());
        assertEquals(20, catalog.forDay(0).bondReward());
    }
    @Test void rejectsInvalidDistanceAndUnknownOrder() {
        var y = yaml("PLACE_BLOCK"); y.set("default-trigger-distance", -1);
        assertThrows(IllegalArgumentException.class, () -> TaskCatalog.parse(y, 20));
        y.set("default-trigger-distance", 50); y.set("daily-order", Collections.nCopies(30, "missing"));
        assertThrows(IllegalArgumentException.class, () -> TaskCatalog.parse(y, 20));
    }
    @Test void unknownTypesCannotBeScheduled() {
        assertThrows(IllegalArgumentException.class, () -> TaskCatalog.parse(yaml("UNKNOWN_TASK"), 20));
    }
    @Test void allEighteenTypesCanBeScheduled() {
        for(String type:cn.mcxyd.xiyuanmarry.service.CoupleTaskService.TYPES) {
            assertEquals(type,TaskCatalog.parse(yaml(type),20).forDay(0).type());
        }
    }
}
