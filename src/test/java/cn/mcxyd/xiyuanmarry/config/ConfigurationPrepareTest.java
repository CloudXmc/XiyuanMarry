package cn.mcxyd.xiyuanmarry.config;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConfigurationPrepareTest {
    @TempDir Path root;
    @Test void invalidReloadDoesNotWriteMergedDefaultsIntoExistingFiles() throws Exception {
        var plugin=mock(JavaPlugin.class);when(plugin.getDataFolder()).thenReturn(root.toFile());
        when(plugin.getResource(anyString())).thenAnswer(call->getClass().getClassLoader().getResourceAsStream(call.getArgument(0)));
        var names=new ArrayList<>(List.of("config.yml","messages.yml","tasks.yml","rank.yml","rewards.yml","weddings.yml"));
        for(String name:ConfigurationManager.GUI_NAMES)names.add("gui/"+name+".yml");
        for(String name:names){var path=root.resolve(name);Files.createDirectories(path.getParent());try(var source=plugin.getResource(name)){Files.copy(source,path);}}
        var invalid="identity:\n  mode: INVALID\n";Files.writeString(root.resolve("config.yml"),invalid);
        var before=new HashMap<String,String>();for(String name:names)before.put(name,Files.readString(root.resolve(name)));
        assertThrows(IllegalArgumentException.class,()->new ConfigurationManager(plugin).prepare());
        for(String name:names)assertEquals(before.get(name),Files.readString(root.resolve(name)),name);
    }
}
