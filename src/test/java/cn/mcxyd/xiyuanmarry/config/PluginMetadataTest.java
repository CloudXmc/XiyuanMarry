package cn.mcxyd.xiyuanmarry.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginMetadataTest {
    @Test void pluginDescriptorParsesAndDeclaresOptionalPlaceholderApi() {
        YamlConfiguration descriptor = YamlConfiguration.loadConfiguration(
                new File("src/main/resources/plugin.yml"));

        assertEquals("2.10.47", descriptor.getString("version"));
        assertEquals("cn.mcxyd.xiyuanmarry.XiyuanMarryPlugin", descriptor.getString("main"));
        assertEquals("xiaota", descriptor.getString("author"));
        assertTrue(descriptor.getBoolean("folia-supported"));
        assertTrue(descriptor.getStringList("softdepend").contains("PlaceholderAPI"));
    }
}










