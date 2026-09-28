package cn.mcxyd.xiyuanmarry.config;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.List;

/** 默认节点与注释补全。 */
final class ConfigurationDefaults {
    static boolean merge(YamlConfiguration target, YamlConfiguration defaults, String resource) {
        boolean changed = upgradeDefaultBrand(target, defaults, resource);
        // 已存在的奖励目录属于服主定义；空目录和删除名次都不是缺失配置。
        var protectedRoots = resource.equals("rewards.yml")
                ? List.of("anniversaries", "level-up", "weekly-top.rewards").stream().filter(target::contains).toList()
                : List.<String>of();
        for (String key : defaults.getKeys(true)) {
            if (!target.contains(key) && protectedRoots.stream().anyMatch(root -> key.startsWith(root + "."))) continue;
            if (!target.contains(key) && !defaults.isConfigurationSection(key)) {
                target.set(key, defaults.get(key)); changed = true;
            }
            if (target.getComments(key).isEmpty()) {
                var comments = defaults.getComments(key);
                if (comments.isEmpty()) comments = List.of("用途：" + key + "；默认值见发行配置；修改后 reload 生效，不自动迁移历史数据。");
                target.setComments(key, comments); changed = true;
            }
        }
        return changed;
    }

    private static boolean upgradeDefaultBrand(YamlConfiguration target, YamlConfiguration defaults, String resource) {
        var keys = switch (resource) {
            case "messages.yml" -> List.of("prefix", "ring-lore", "help-title", "admin-help-title");
            case "gui/main_menu.yml" -> List.of("title");
            default -> List.<String>of();
        };
        String previous = Character.toString(0x559C) + Character.toString(0x7F18);
        boolean changed = false;
        for (String key : keys) {
            String current = defaults.getString(key);
            // 只更新原版默认文案；服主修改过的文字、配色和其他配置保持原值。
            if (current != null && current.contains("结婚系统")
                    && current.replace("结婚系统", previous).equals(target.get(key))) {
                target.set(key, current);
                changed = true;
            }
        }
        return changed;
    }
}
