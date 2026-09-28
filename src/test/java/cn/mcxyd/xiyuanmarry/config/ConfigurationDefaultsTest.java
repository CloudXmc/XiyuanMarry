package cn.mcxyd.xiyuanmarry.config;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ConfigurationDefaultsTest {
    @Test void upgradesDefaultInboxLoreButPreservesCustomizedLore(){
        var original=java.util.List.of("<gray>{entry.description}</gray>","<yellow>左键点击使用</yellow>");
        var replacement=java.util.List.of("<gray>{entry.description}</gray>","<yellow>{entry.hint}</yellow>","<red>风险</red>");
        var actual=new YamlConfiguration();actual.set("icons.D.lore",original);
        var defaults=new YamlConfiguration();defaults.set("icons.D.lore",replacement);
        ConfigurationDefaults.merge(actual,defaults,"gui/invitation.yml");
        assertEquals(replacement,actual.getStringList("icons.D.lore"));
        actual.set("icons.D.lore",java.util.List.of("自定义说明","自定义操作"));
        ConfigurationDefaults.merge(actual,defaults,"gui/invitation.yml");
        assertEquals(java.util.List.of("自定义说明","自定义操作"),actual.getStringList("icons.D.lore"));
    }
    @Test void receivedInvitationEntryOnlyMigratesExactDefaultLayout(){
        var old=java.util.List.of("#########","#IATARAB#","#ACAAAWA#","#AAGAUAA#","#########");
        var next=java.util.List.of("#########","#IATARAB#","#ACQAAWA#","#AAGAUAA#","#########");
        var actual=new YamlConfiguration();actual.set("layout",old);
        var defaults=new YamlConfiguration();defaults.set("layout",next);defaults.set("icons.Q.action","received-invitations");
        ConfigurationDefaults.merge(actual,defaults,"gui/main_menu.yml");
        assertEquals(next,actual.getStringList("layout"));
        actual.set("layout",old);actual.set("icons.Q.action","custom");
        ConfigurationDefaults.merge(actual,defaults,"gui/main_menu.yml");
        assertEquals(old,actual.getStringList("layout"));assertEquals("custom",actual.getString("icons.Q.action"));
    }
    @Test void customizedMainMenuWithEmptyFooterIsNotOverwritten() {
        var actual=new YamlConfiguration();var layout=java.util.List.of("#########","#AAAAAAA#","#AAAAAAA#","#IATARAB#","#AAAAAAA#");actual.set("layout",layout);
        ConfigurationDefaults.merge(actual,new YamlConfiguration(),"gui/main_menu.yml");
        assertEquals(layout,actual.getStringList("layout"));
    }
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

    @Test void legacyGuiLayoutMigratesTo45SlotsWithoutNavigationIcons() {
        var actual = new YamlConfiguration();
        actual.set("title", "自定义标题");
        actual.set("layout", java.util.List.of("#########", "#DDDDDDD#", "#DDDDDDD#", "#DDDDDDD#", "#AAAAAAA#", "P###F###N"));
        actual.createSection("icons.D"); actual.createSection("icons.P"); actual.createSection("icons.N"); actual.createSection("icons.F");
        actual.set("icons.D.name", "自定义图标");
        var defaults = new YamlConfiguration();
        assertTrue(ConfigurationDefaults.merge(actual, defaults, "gui/task.yml"));
        assertEquals(5, actual.getStringList("layout").size());
        assertEquals("#########", actual.getStringList("layout").get(4));
        assertTrue(actual.contains("icons.P"));
        assertTrue(actual.contains("icons.N"));
        assertTrue(actual.contains("icons.F"));
        assertEquals("自定义标题", actual.getString("title"));
        assertEquals("自定义图标", actual.getString("icons.D.name"));
    }

    @Test void customizedNonLegacyLayoutIsNotOverwritten() {
        var actual=new YamlConfiguration(); actual.set("layout",java.util.List.of("#########","#DDDDDDD#","#DDDDDDD#","#DDDDDDD#","#DDDDDDD#"));
        var defaults=new YamlConfiguration();
        assertFalse(ConfigurationDefaults.merge(actual,defaults,"gui/propose.yml"));
        assertEquals("#DDDDDDD#",actual.getStringList("layout").get(4));
    }

    @Test void upgradesShipped284ListLayoutAndKeepsThirtySlotsAndNavigationDefinitions() {
        var actual=new YamlConfiguration();
        actual.set("layout",java.util.List.of("#########","#DDDDDDD#","#DDDDDDD#","#DDDDDDD#","DDDDDDDDD"));
        actual.createSection("icons.D");actual.createSection("icons.P");
        var defaults=new YamlConfiguration();
        assertTrue(ConfigurationDefaults.merge(actual,defaults,"gui/propose.yml"));
        assertEquals("DDDDDDDDD",actual.getStringList("layout").getFirst());
        assertEquals("#########",actual.getStringList("layout").getLast());
        assertEquals(30,actual.getStringList("layout").stream().mapToInt(r->(int)r.chars().filter(c->c=='D').count()).sum());
        assertTrue(actual.contains("icons.P"));
    }

    @Test void upgradesShippedMainMenuEmptyFooterToSeparatorRow() {
        var actual=new YamlConfiguration();actual.set("layout",java.util.List.of("#########","#IATARAB#","#ACAAAWA#","#AAGAUAA#","#AAAAAAA#"));
        assertTrue(ConfigurationDefaults.merge(actual,new YamlConfiguration(),"gui/main_menu.yml"));
        assertEquals("#########",actual.getStringList("layout").getLast());
    }
}
