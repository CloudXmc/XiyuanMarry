package cn.mcxyd.xiyuanmarry.config;

import cn.mcxyd.xiyuanmarry.gui.GuiLayout;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RankNavigationTest {
    private YamlConfiguration defaults() {
        return YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui/rank.yml").toFile());
    }
    @Test void lowerRightButtonHasGradientNameAndTeachingLore() {
        var layout=GuiLayout.parse(defaults());
        assertEquals('R',layout.rows().get(3).charAt(8));
        var icon=layout.icons().get('R');
        assertNotNull(icon);
        assertEquals("back",icon.action());
        assertTrue(icon.name().contains("gradient"));
        assertTrue(icon.name().contains("返回主菜单"));
        assertEquals(35,layout.dynamicSlots().size());
        assertTrue(icon.lore().size()>=2);
    }
    @Test void previousDefaultGainsNavigationAndKeepsCustomBorder() {
        var target=defaults();
        target.set("layout",List.of("DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","#########"));
        target.set("icons.R",null);
        target.set("icons.#.name","服主分隔板");
        ConfigurationDefaults.merge(target,defaults(),"gui/rank.yml");
        assertEquals("DDDDDDDDR",target.getStringList("layout").get(3));
        assertEquals("back",target.getString("icons.R.action"));
        assertEquals("服主分隔板",target.getString("icons.#.name"));
        var serialized=target.saveToString();
        ConfigurationDefaults.merge(target,defaults(),"gui/rank.yml");
        assertEquals(serialized,target.saveToString());
    }
    @Test void customizedLayoutAndExistingIconSurviveUpgrade() {
        var target=defaults();
        var custom=List.of("#########","#DDDDDDD#","#AAAAAAA#","#AAAARAA#","#########");
        target.set("layout",custom);
        target.set("icons.R.name","服主返回键");
        ConfigurationDefaults.merge(target,defaults(),"gui/rank.yml");
        assertEquals(custom,target.getStringList("layout"));
        assertEquals("服主返回键",target.getString("icons.R.name"));
    }
    @Test void unusedCustomSymbolDoesNotBecomeNewReturnButton() {
        var target=defaults();
        var previous=List.of("DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","#########");
        target.set("layout",previous);target.set("icons.R.action","tp");
        ConfigurationDefaults.merge(target,defaults(),"gui/rank.yml");
        assertEquals(previous,target.getStringList("layout"));
        assertEquals("tp",target.getString("icons.R.action"));
    }
}
