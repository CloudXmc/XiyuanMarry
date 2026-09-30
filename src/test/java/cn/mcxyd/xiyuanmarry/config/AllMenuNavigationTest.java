package cn.mcxyd.xiyuanmarry.config;

import cn.mcxyd.xiyuanmarry.gui.GuiLayout;
import cn.mcxyd.xiyuanmarry.message.TextRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class AllMenuNavigationTest {
    static Stream<String> menus() { return ConfigurationManager.GUI_NAMES.stream(); }
    private YamlConfiguration read(String name) {
        return YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui/"+name+".yml").toFile());
    }
    private String symbol(String name) { return name.equals("main_menu") ? "X" : "R"; }
    private List<String> previous(String name) {
        return switch(name) {
            case "main_menu" -> List.of("#########","#ACAIAWA#","#ATAUARA#","#AQABAGA#","#########");
            case "wedding_plan" -> List.of("#########","#LUVYIAA#","#AAAAAAA#","#GASACAA#","#########");
            case "partner_info" -> List.of("#########","#AAAAAAA#","#AAADAAA#","#AAAAAAA#","#########");
            case "task" -> List.of("DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","DDDDDDDDH","#########");
            default -> List.of("DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","#########");
        };
    }
    @ParameterizedTest @MethodSource("menus")
    void everyMenuHasOneVisibleGradientBackButton(String name) {
        var layout=GuiLayout.parse(read(name));
        var visible=layout.rows().stream().flatMapToInt(String::chars)
                .filter(c -> c!='A' && "back".equals(layout.icons().get((char)c).action())).toArray();
        assertEquals(1,visible.length,name);
        var icon=layout.icons().get((char)visible[0]);
        assertTrue(icon.name().contains("<gradient:"));
        var renderer=new TextRenderer();
        var title=renderer.gui(renderer.parse(icon.name()));
        assertEquals("返回主菜单",PlainTextComponentSerializer.plainText().serialize(title));
        notItalic(title);
        assertTrue(icon.lore().size()>=2);
        icon.lore().forEach(line -> notItalic(renderer.gui(renderer.parse(line))));
        assertEquals(45,layout.size());
    }
    @ParameterizedTest @MethodSource("menus")
    void releasedDefaultUpgradesWithoutChangingUserTextAndIsIdempotent(String name) {
        var target=read(name);var defaults=read(name);
        target.set("layout",previous(name));target.set("icons."+symbol(name),null);
        target.set("title","服主自定义标题");target.set("icons.#.name","服主自定义分隔板");
        ConfigurationDefaults.merge(target,defaults,"gui/"+name+".yml");
        assertEquals(defaults.getStringList("layout"),target.getStringList("layout"));
        assertEquals("back",target.getString("icons."+symbol(name)+".action"));
        assertEquals("服主自定义标题",target.getString("title"));
        assertEquals("服主自定义分隔板",target.getString("icons.#.name"));
        assertDoesNotThrow(() -> GuiLayout.parse(target));
        var serialized=target.saveToString();
        ConfigurationDefaults.merge(target,defaults,"gui/"+name+".yml");
        assertEquals(serialized,target.saveToString());
    }
    @ParameterizedTest @MethodSource("menus")
    void dormantCustomActionIsNeverActivatedByMigration(String name) {
        var target=read(name);target.set("layout",previous(name));
        target.set("icons."+symbol(name)+".action","tp");
        ConfigurationDefaults.merge(target,read(name),"gui/"+name+".yml");
        assertEquals(previous(name),target.getStringList("layout"));
        assertEquals("tp",target.getString("icons."+symbol(name)+".action"));
    }
    @ParameterizedTest @MethodSource("menus")
    void customLayoutIsNeverOverwritten(String name) {
        var target=read(name);var custom=List.of("#########","#AAAAAAA#","#AAAAAAA#","#AAAAAAA#","#########");
        target.set("layout",custom);
        ConfigurationDefaults.merge(target,read(name),"gui/"+name+".yml");
        assertEquals(custom,target.getStringList("layout"));
    }
    private void notItalic(Component c) {
        assertEquals(TextDecoration.State.FALSE,c.decoration(TextDecoration.ITALIC));
        c.children().forEach(this::notItalic);
    }
}
