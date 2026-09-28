package cn.mcxyd.xiyuanmarry.gui;
import org.bukkit.Material;import org.bukkit.configuration.file.YamlConfiguration;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class GuiLayoutTest {
 @Test void parses45SlotBottomBorderAndDefaultSounds(){var y=new YamlConfiguration();y.set("layout",java.util.List.of("#########","#DDDDDDD#","#DDDDDDD#","#DDDDDDD#","#########"));y.set("icons.#.material","WHITE_STAINED_GLASS_PANE");y.set("icons.#.lore",java.util.List.of("a","b"));y.set("icons.D.material","PAPER");y.set("icons.D.lore",java.util.List.of("a","b"));y.set("icons.D.action","select");var g=GuiLayout.parse(y,mat->true);assertEquals(45,g.size());assertEquals(10,g.dynamicSlots().get(0));assertEquals("minecraft:ui.button.click",g.sounds().click());}
 @Test void rejectsInvalidSoundRange(){var y=new YamlConfiguration();y.set("layout",java.util.List.of("#########"));y.set("icons.#.material","WHITE_STAINED_GLASS_PANE");y.set("icons.#.lore",java.util.List.of("a","b"));y.set("sounds.volume",5);assertThrows(IllegalArgumentException.class,()->GuiLayout.parse(y));}
}


