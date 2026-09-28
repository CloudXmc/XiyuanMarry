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
        for(String name:new String[]{"main_menu.yml","propose.yml","wedding_plan.yml"}){
            YamlConfiguration yaml=load("gui/"+name);
            List<String> layout=yaml.getStringList("layout");
            assertTrue(layout.size()>=1&&layout.size()<=6,name);
            for(String row:layout)assertEquals(9,row.length(),name+" row width");
            assertNotNull(yaml.getConfigurationSection("icons"));
        }
    }
    private void assertYaml(String resource){assertNotNull(load(resource),resource);}
    private YamlConfiguration load(String resource){
        var stream=getClass().getClassLoader().getResourceAsStream(resource);
        assertNotNull(stream,resource);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8));
    }
}

