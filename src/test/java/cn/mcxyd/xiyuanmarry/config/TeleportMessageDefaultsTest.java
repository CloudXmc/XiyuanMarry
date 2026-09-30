package cn.mcxyd.xiyuanmarry.config;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class TeleportMessageDefaultsTest {
    @Test void oldCooldownMessageUpgradesButCustomTextSurvives(){
        var defaults=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile());
        assertTrue(defaults.getString("teleport-cooldown").contains("{minutes}"));
        assertTrue(defaults.getString("teleport-cooldown").contains("{seconds}"));
        var actual=new YamlConfiguration();actual.set("teleport-cooldown","<yellow>传送冷却中，请稍后再试。</yellow>");
        ConfigurationDefaults.merge(actual,defaults,"messages.yml");assertEquals(defaults.getString("teleport-cooldown"),actual.getString("teleport-cooldown"));
        actual.set("teleport-cooldown","我的冷却提示 {remaining-seconds}");
        ConfigurationDefaults.merge(actual,defaults,"messages.yml");assertEquals("我的冷却提示 {remaining-seconds}",actual.getString("teleport-cooldown"));
    }
}
