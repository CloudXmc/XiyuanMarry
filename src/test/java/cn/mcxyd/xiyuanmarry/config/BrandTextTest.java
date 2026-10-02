package cn.mcxyd.xiyuanmarry.config;
import cn.mcxyd.xiyuanmarry.message.TextRenderer;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BrandTextTest {
    private static final String BRAND = "结婚系统";
    private static final String OLD_BRAND = Character.toString(0x559C) + Character.toString(0x7F18);
    private static final List<String> KEYS = List.of("prefix", "ring-lore", "help-title", "admin-help-title");
    private YamlConfiguration read(String name) throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream(name); assertNotNull(stream);
        var yaml = new YamlConfiguration();
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) { yaml.load(reader); }
        return yaml;
    }
    @Test void resourcesDropPreviousBrandAndPreservePluginIdentity() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/resources"))) {
            for (var file : paths.filter(Files::isRegularFile).toList())
                assertFalse(Files.readString(file).contains(OLD_BRAND), file.toString());
        }
        var y = read("plugin.yml");
        assertEquals("XiyuanMarry", y.getString("name"));
        assertEquals("cn.mcxyd.xiyuanmarry.XiyuanMarryPlugin", y.getString("main"));
        assertEquals("/marry help", y.getString("commands.marry.usage"));
        assertEquals("/marryadmin help", y.getString("commands.marryadmin.usage"));
    }
    @Test void displayedBrandHasFourDistinctGradientColours() throws Exception {
        var y = read("messages.yml");
        for (var key : KEYS) assertGradient(y.getString(key));
        assertGradient(read("gui/main_menu.yml").getString("title"));
    }
    @Test void marriageAnnouncementUsesHeartAndAuspiciousWording() throws Exception {
        var announcement = read("messages.yml").getString("married");
        assertNotNull(announcement);
        assertTrue(announcement.contains("❤️"));
        assertTrue(announcement.contains("喜结连理"));
        assertTrue(announcement.contains("{player1}") && announcement.contains("{player2}"));
        assertTrue(announcement.contains("<gradient:") && announcement.contains("</gradient>"));
    }
    @Test void previousDefaultMessagesUpgradeAndPersistIdempotently() throws Exception {
        var defaults = read("messages.yml"); var target = new YamlConfiguration();
        target.loadFromString(defaults.saveToString());
        for (var key : KEYS) target.set(key, defaults.getString(key).replace(BRAND, OLD_BRAND));
        assertTrue(ConfigurationDefaults.merge(target, defaults, "messages.yml"));
        for (var key : KEYS) assertEquals(defaults.getString(key), target.getString(key));
        var saved = new YamlConfiguration(); saved.loadFromString(target.saveToString());
        assertFalse(ConfigurationDefaults.merge(saved, defaults, "messages.yml"));
    }
    @Test void previousDefaultGuiTitleUpgradesAndPreservesLayoutAndBorder() throws Exception {
        var defaults = read("gui/main_menu.yml"); var target = new YamlConfiguration();
        target.loadFromString(defaults.saveToString());
        target.set("title", defaults.getString("title").replace(BRAND, OLD_BRAND));
        ConfigurationDefaults.merge(target, defaults, "gui/main_menu.yml");
        assertEquals(defaults.getString("title"), target.getString("title"));
        assertEquals(defaults.getStringList("layout"), target.getStringList("layout"));
        assertEquals(defaults.getString("icons.#.name"), target.getString("icons.#.name"));
        assertEquals(defaults.getStringList("icons.#.lore"), target.getStringList("icons.#.lore"));
    }
    @Test void customTextAndUnrelatedSettingsArePreserved() throws Exception {
        var defaults = read("messages.yml"); var target = new YamlConfiguration();
        String custom = "<gold>我的服务器 · " + OLD_BRAND + "</gold>";
        target.set("prefix", custom); ConfigurationDefaults.merge(target, defaults, "messages.yml");
        assertEquals(custom, target.getString("prefix"));
        var other = new YamlConfiguration(); other.set("server-label", BRAND);
        var config = new YamlConfiguration(); config.set("server-label", OLD_BRAND);
        ConfigurationDefaults.merge(config, other, "config.yml");
        assertEquals(OLD_BRAND, config.getString("server-label"));
    }
    private void assertGradient(String source) {
        assertNotNull(source); assertTrue(source.contains("<gradient:"));
        var plain = new StringBuilder(); var colours = new ArrayList<TextColor>();
        collect(new TextRenderer().parse(source), null, plain, colours);
        int start = plain.indexOf(BRAND); assertTrue(start >= 0);
        var brandColours = colours.subList(start, start + BRAND.length());
        assertTrue(brandColours.stream().allMatch(Objects::nonNull));
        assertEquals(4L, brandColours.stream().distinct().count());
    }
    private void collect(Component c, TextColor parent, StringBuilder text, List<TextColor> colours) {
        TextColor colour = c.color() == null ? parent : c.color();
        if (c instanceof TextComponent t) {
            text.append(t.content());
            for (int i = 0; i < t.content().length(); i++) colours.add(colour);
        }
        for (var child : c.children()) collect(child, colour, text, colours);
    }
}
