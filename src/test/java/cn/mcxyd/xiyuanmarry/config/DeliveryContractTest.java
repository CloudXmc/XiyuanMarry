package cn.mcxyd.xiyuanmarry.config;
import cn.mcxyd.xiyuanmarry.gui.GuiLayout;
import cn.mcxyd.xiyuanmarry.message.TextRenderer;
import cn.mcxyd.xiyuanmarry.model.IdentityResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import net.kyori.adventure.text.*;import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;import java.nio.charset.StandardCharsets;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DeliveryContractTest {
    YamlConfiguration read(String name)throws Exception {
        var stream=getClass().getClassLoader().getResourceAsStream(name);assertNotNull(stream,name);
        var y=new YamlConfiguration();try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){y.load(reader);}return y;
    }
    @Test void allGuiResourcesParseKeepBorderAndTeachActions()throws Exception {
        for(String name:ConfigurationManager.GUI_NAMES){
            var y=read("gui/"+name+".yml");var layout=GuiLayout.parse(y);
            assertEquals(45,layout.size());assertFalse(layout.icons().containsKey('A'));
            assertFalse(layout.icons().containsKey('P'));assertFalse(layout.icons().containsKey('N'));assertFalse(layout.icons().containsKey('F'));
            var border=layout.icons().get('#');assertEquals("WHITE_STAINED_GLASS_PANE",border.material().name());
            assertEquals("§e✧ 闪闪发光的边框 ✧",border.name());
            assertEquals(List.of("§7嘿嘿嘿～","§7戳我干嘛呀小坏蛋～"),border.lore());
            for(var icon:layout.icons().entrySet())if(icon.getKey()!='#'){assertTrue(icon.getValue().lore().size()>=2);assertFalse(icon.getValue().action().isBlank());}
        }
        read("weddings.yml");read("rewards.yml");read("rank.yml");
    }
    @Test void metadataAndLibrariesMatchBuildContract()throws Exception {
        var y=read("plugin.yml");assertEquals("xiaota",y.getString("author"));assertEquals("1.21.11",y.getString("api-version"));
        assertTrue(y.getBoolean("folia-supported"));assertTrue(y.getString("commands.marry.usage").contains("help"));
        assertEquals(4,y.getStringList("libraries").size());assertTrue(y.isConfigurationSection("permissions.marry.use"));
    }
    @Test void taskDefaultsHaveThirtyUsableDaysAndEveryMessageParses()throws Exception {
        var config=read("config.yml");var messages=read("messages.yml");
        ConfigurationManager.validate(config,messages);
        var tasks=TaskCatalog.parse(read("tasks.yml"),config.getLong("bond.task-complete"));
        assertEquals(30,tasks.schedule().size());assertEquals(18,tasks.schedule().stream().map(t->t.type()).distinct().count());
        var text=new TextRenderer();
        for(String key:messages.getKeys(true)){if(messages.isString(key))assertNotNull(text.parse(messages.getString(key)));}
    }
    @Test void legacyAndGradientRenderingDisableItalicsRecursively() {
        var text=new TextRenderer();
        for(String source:List.of("&2&l标题","§e标题","<gradient:#FF92B4:#B79CFF><italic>标题</italic></gradient>")){
            Component parsed=text.gui(text.parse(source));assertEquals("标题",PlainTextComponentSerializer.plainText().serialize(parsed));notItalic(parsed);
        }
        String attacker="<click:run_command:'/op nobody'>文字</click>";
        var rendered=text.format("<gray>{message}</gray>","message",attacker);
        assertEquals(attacker,PlainTextComponentSerializer.plainText().serialize(rendered));assertNull(rendered.clickEvent());
    }
    void notItalic(Component c){assertEquals(TextDecoration.State.FALSE,c.decoration(TextDecoration.ITALIC));c.children().forEach(this::notItalic);}
    @Test void identityModesPreserveRealUuidAndNormalizeOfflineName() {
        UUID live=UUID.randomUUID();String online=IdentityResolver.key("ONLINE_UUID",true,live,"Alice");
        assertEquals(live,IdentityResolver.storageId(online,live));
        assertEquals(IdentityResolver.key("OFFLINE_NAME",true,live,"Alice"),IdentityResolver.key("OFFLINE_NAME",true,UUID.randomUUID(),"ALICE"));
        assertThrows(IllegalArgumentException.class,()->IdentityResolver.key("INVALID",true,live,"Alice"));
    }
    @Test void invalidIdentityConfigurationIsRejected()throws Exception {
        var c=read("config.yml");var m=read("messages.yml");ConfigurationManager.validate(c,m);
        c.set("identity.mode","BAD");assertThrows(IllegalArgumentException.class,()->ConfigurationManager.validate(c,m));
    }
    @Test void invalidWeddingTimingConfigurationIsRejected()throws Exception {
        var m=read("messages.yml");
        var invite=read("config.yml");invite.set("wedding.invite-expire-hours",0);
        assertThrows(IllegalArgumentException.class,()->ConfigurationManager.validate(invite,m));
        var timeout=read("config.yml");timeout.set("wedding.ceremony-timeout-seconds",86401);
        assertThrows(IllegalArgumentException.class,()->ConfigurationManager.validate(timeout,m));
        var oath=read("config.yml");oath.set("wedding.oath-text","");
        assertThrows(IllegalArgumentException.class,()->ConfigurationManager.validate(oath,m));
    }
}
