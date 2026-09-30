package cn.mcxyd.xiyuanmarry.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResourceYamlTest {
    @Test void allYamlResourcesParseAndGuiLayoutsAreNineWide() {
        assertYaml("config.yml"); assertYaml("messages.yml"); assertYaml("tasks.yml");
        assertYaml("rank.yml"); assertYaml("rewards.yml"); assertYaml("weddings.yml");
        for(String name:new String[]{"main_menu.yml","propose.yml","wedding_plan.yml","partner_info.yml","invitation.yml","send_invite.yml","gift.yml","task.yml","rank.yml"}){
            YamlConfiguration yaml=load("gui/"+name);
            List<String> layout=yaml.getStringList("layout");
            assertEquals(5,layout.size(),name);
            for(String row:layout)assertEquals(9,row.length(),name+" row width");
            if(name.equals("task.yml")) assertEquals("########R",layout.getLast(),name+" bottom row with return button");
            else assertEquals("#########",layout.getLast(),name+" fixed bottom separator row");
            assertNotNull(yaml.getConfigurationSection("icons"));
            assertFalse(yaml.getConfigurationSection("icons").contains("P"),name);
            assertFalse(yaml.getConfigurationSection("icons").contains("N"),name);
            assertFalse(yaml.getConfigurationSection("icons").contains("F"),name);
        }
    }
    private void assertYaml(String resource){assertNotNull(load(resource),resource);}
    private YamlConfiguration load(String resource){
        var stream=getClass().getClassLoader().getResourceAsStream(resource);
        assertNotNull(stream,resource);
        var yaml=new YamlConfiguration();
        assertDoesNotThrow(()->{try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){yaml.load(reader);}},resource);
        return yaml;
    }
}

