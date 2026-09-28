package cn.mcxyd.xiyuanmarry.config;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.File;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class RewardCatalogTest {
    private YamlConfiguration main() { return YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml")); }
    private YamlConfiguration rewards() { return YamlConfiguration.loadConfiguration(new File("src/main/resources/rewards.yml")); }
    @Test void defaultsParseAndSnapshotDoesNotChangeWithSource() {
        var source = rewards(); var config = main();
        var catalog = RewardCatalog.parse(source, config);
        assertEquals(3, catalog.weekly().size());
        assertEquals(5000, catalog.weekly().get(1).money());
        source.set("weekly-top.rewards.1.money", 1);
        config.set("bond.levels.2.required", 99999);
        assertEquals(5000, catalog.weekly().get(1).money());
        assertEquals(2, catalog.level(100));
        assertThrows(UnsupportedOperationException.class, () -> catalog.weekly().clear());
    }
    @ParameterizedTest @ValueSource(strings={"0", "1001", "first", "01", "-1"})
    void invalidRankFailsRatherThanSilentlySkipping(String rank) {
        var file = rewards();
        file.set("weekly-top.rewards." + rank, Map.of("money", 1));
        assertThrows(IllegalArgumentException.class, () -> RewardCatalog.parse(file, main()));
    }
    @ParameterizedTest @ValueSource(strings={"weekly-top.hour", "weekly-top.rewards.1.money", "weekly-top.rewards.1.experience"})
    void numericStringsAreNotSilentlyCoerced(String path) {
        var file = rewards(); file.set(path, "20");
        assertThrows(IllegalArgumentException.class, () -> RewardCatalog.parse(file, main()));
    }
    @Test void negativeMoneyAndMalformedCommandsAreRejected() {
        var negative = rewards(); negative.set("weekly-top.rewards.1.money", -1);
        assertThrows(IllegalArgumentException.class, () -> RewardCatalog.parse(negative, main()));
        var badCommands = rewards(); badCommands.set("weekly-top.rewards.1.commands", "say wrong");
        assertThrows(IllegalArgumentException.class, () -> RewardCatalog.parse(badCommands, main()));
    }
    @Test void explicitDisableAndSparseRewardsRemainEffective() {
        var file = rewards(); file.set("weekly-top.enabled", false);
        file.set("weekly-top.rewards", null);
        file.createSection("weekly-top.rewards.3").set("money", 1500);
        var catalog = RewardCatalog.parse(file, main());
        assertFalse(catalog.weeklyEnabled());
        assertEquals(java.util.Set.of(3), catalog.weekly().keySet());
    }
    @Test void invalidWeekdayHourAndBoardFailValidation() {
        for (var entry : Map.<String,Object>of("weekly-top.day-of-week", "not-a-day",
                "weekly-top.hour", 24, "weekly-top.board", "missing").entrySet()) {
            var file = rewards(); file.set(entry.getKey(), entry.getValue());
            assertThrows(IllegalArgumentException.class, () -> RewardCatalog.parse(file, main()));
        }
    }
}
