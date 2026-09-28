package cn.mcxyd.xiyuanmarry.config;
import cn.mcxyd.xiyuanmarry.gui.GuiLayout;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class GuiSoundDefaultsTest {
    @Test void shippedMenusUseTheTargetPaperChestSoundKeys() {
        for(String name:ConfigurationManager.GUI_NAMES){
            var yaml=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui/"+name+".yml").toFile());
            assertEquals("minecraft:block.chest.open",yaml.getString("sounds.open"),name);
            assertEquals("minecraft:block.chest.close",yaml.getString("sounds.close"),name);
            assertEquals("minecraft:ui.button.click",yaml.getString("sounds.click"),name);
        }
    }
    @Test void missingSoundSectionUsesVanillaChestSounds() {
        var sounds=GuiLayout.parse(menu()).sounds();
        assertEquals("minecraft:block.chest.open",sounds.open());assertEquals("minecraft:block.chest.close",sounds.close());
    }
    @Test void onlyKnownBrokenDefaultKeysAreMigrated() {
        var yaml=menu();yaml.set("sounds.open","minecraft:ui.chest.open");yaml.set("sounds.close","minecraft:ui.chest.close");
        yaml.set("sounds.click","custom:menu.click");yaml.set("sounds.volume",0.6);
        assertTrue(ConfigurationDefaults.merge(yaml,new YamlConfiguration(),"gui/main_menu.yml"));
        assertEquals("minecraft:block.chest.open",yaml.getString("sounds.open"));assertEquals("minecraft:block.chest.close",yaml.getString("sounds.close"));
        assertEquals("custom:menu.click",yaml.getString("sounds.click"));assertEquals(0.6,yaml.getDouble("sounds.volume"));
    }
    @Test void customSoundKeysRemainUnchanged() {
        var yaml=menu();yaml.set("sounds.open","custom:menu.open");yaml.set("sounds.close","custom:menu.close");
        ConfigurationDefaults.merge(yaml,new YamlConfiguration(),"gui/main_menu.yml");
        assertEquals("custom:menu.open",yaml.getString("sounds.open"));assertEquals("custom:menu.close",yaml.getString("sounds.close"));
    }
    @Test void nonFiniteSoundParametersAreRejectedAtLoadTime() {
        for(String field:List.of("volume","pitch")){var yaml=menu();yaml.set("sounds."+field,Double.NaN);assertThrows(IllegalArgumentException.class,()->GuiLayout.parse(yaml),field);}
    }
    private YamlConfiguration menu(){var yaml=new YamlConfiguration();yaml.set("layout",List.of("#########"));yaml.set("icons.#.material","WHITE_STAINED_GLASS_PANE");return yaml;}
}
