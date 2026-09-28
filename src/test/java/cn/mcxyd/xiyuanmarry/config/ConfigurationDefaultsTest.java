package cn.mcxyd.xiyuanmarry.config;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ConfigurationDefaultsTest {
    @Test void explicitRewardListsAreNotRepopulatedDuringUpgrade() {
        var defaults = new YamlConfiguration();
        defaults.set("weekly-top.enabled", true);
        defaults.set("weekly-top.rewards.1.money", 5000);
        defaults.set("weekly-top.rewards.2.money", 3000);
        defaults.set("weekly-top.rewards.3.money", 1500);
        defaults.set("anniversaries.7.money", 500);
        var actual = new YamlConfiguration();
        actual.set("weekly-top.rewards.3.money", 42);
        actual.createSection("anniversaries");
        ConfigurationDefaults.merge(actual, defaults, "rewards.yml");
        assertEquals(42, actual.getLong("weekly-top.rewards.3.money"));
        assertFalse(actual.contains("weekly-top.rewards.1"));
        assertFalse(actual.contains("weekly-top.rewards.2"));
        assertTrue(actual.getConfigurationSection("anniversaries").getKeys(false).isEmpty());
        assertTrue(actual.getBoolean("weekly-top.enabled"));
    }
    @Test void missingCatalogueReceivesDefaultsButExistingScalarsStayUnchanged() {
        var defaults = new YamlConfiguration(); defaults.set("weekly-top.hour", 20); defaults.set("weekly-top.rewards.1.money", 5000);
        var actual = new YamlConfiguration(); actual.set("weekly-top.hour", 18);
        ConfigurationDefaults.merge(actual, defaults, "rewards.yml");
        assertEquals(18, actual.getInt("weekly-top.hour"));
        assertEquals(5000, actual.getLong("weekly-top.rewards.1.money"));
        assertFalse(actual.getComments("weekly-top.hour").isEmpty());
    }
}
