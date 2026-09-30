package cn.mcxyd.xiyuanmarry.config;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.List;

/** 默认节点与注释补全。 */
final class ConfigurationDefaults {
    static boolean merge(YamlConfiguration target, YamlConfiguration defaults, String resource) {
        boolean changed = upgradeDefaultBrand(target, defaults, resource);
        changed |= upgradeKnownDefaults(target, resource);
        changed |= upgradeGuiTo45Slots(target, resource);
        changed |= upgradeInboxMenus(target,defaults,resource);
        changed |= upgradeAlignedMenus(target,defaults,resource);
        if(resource.equals("messages.yml")&&defaults.contains("teleport-cooldown")
                &&"<yellow>传送冷却中，请稍后再试。</yellow>".equals(target.getString("teleport-cooldown"))){
            target.set("teleport-cooldown",defaults.getString("teleport-cooldown"));changed=true;
        }
        // 已存在的奖励目录属于服主定义；空目录和删除名次都不是缺失配置。
        var protectedRoots = resource.equals("rewards.yml")
                ? List.of("anniversaries", "level-up", "weekly-top.rewards").stream().filter(target::contains).toList()
                : List.<String>of();
        for (String key : defaults.getKeys(true)) {
            if (!target.contains(key) && protectedRoots.stream().anyMatch(root -> key.startsWith(root + "."))) continue;
            if (!target.contains(key)) {
                // 先创建新节，避免对子节点补全前写入的节注释被 Bukkit 丢弃。
                if (defaults.isConfigurationSection(key)) target.createSection(key);
                else target.set(key, defaults.get(key));
                changed = true;
            }
            if (target.getComments(key).isEmpty()) {
                var comments = defaults.getComments(key);
                if (comments.isEmpty()) comments = List.of("用途：" + key + "；默认值见发行配置；修改后 reload 生效，不自动迁移历史数据。");
                target.setComments(key, comments); changed = true;
            }
        }
        return changed;
    }

    private static boolean upgradeAlignedMenus(YamlConfiguration target,YamlConfiguration defaults,String resource){
        if(!resource.startsWith("gui/")||defaults.getStringList("layout").isEmpty())return false;
        // 服主预留的同名图标不能因升级布局而被意外激活。
        if(resource.equals("gui/rank.yml")&&target.contains("icons.R")){
            var existing=target.getConfigurationSection("icons.R");
            var expected=defaults.getConfigurationSection("icons.R");
            if(existing==null||expected==null||!existing.getValues(true).equals(expected.getValues(true)))return false;
        }
        var previous=switch(resource){
            case "gui/main_menu.yml"->List.of("#########","#IATARAB#","#ACQAAWA#","#AAGAUAA#","#########");
            case "gui/wedding_plan.yml"->List.of("#########","#LAIAGAA#","#AAASACA#","#AAAAAAA#","#########");
            case "gui/propose.yml","gui/send_invite.yml","gui/invitation.yml","gui/gift.yml","gui/rank.yml","gui/partner_info.yml"
                    ->List.of("DDDDDDDDD","#DDDDDDD#","#DDDDDDD#","#DDDDDDD#","#########");
            case "gui/task.yml"->List.of("DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","#########");
            default->List.<String>of();
        };
        // 只迁移已发布的默认排列；不改图标、动作、标题和服主自定义布局。
        var replacement=defaults.getStringList("layout");
        var current=target.getStringList("layout");
        // 2.8.3 的导航栏被旧迁移移除后只剩 21 个 D，仍需继续升级到当前列表布局。
        boolean legacyList=previous.contains("DDDDDDDDD")&&current.equals(
                List.of("#########","#DDDDDDD#","#DDDDDDD#","#DDDDDDD#","#########"));
        boolean mixedList=previous.contains("DDDDDDDDD")&&current.equals(
                List.of("DDDDDDDDD","#DDDDDDD#","#DDDDDDD#","#DDDDDDD#","#########"));
        boolean fullTaskList=resource.equals("gui/task.yml")&&current.equals(
                List.of("DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","#########"));
        boolean taskPeriodFirst=resource.equals("gui/task.yml")&&current.equals(
                List.of("DDDHDDDDD","DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","#########"));
        boolean rankWithoutReturn=resource.equals("gui/rank.yml")&&current.equals(
                List.of("DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","#########"));
        if(!previous.isEmpty()&&(current.equals(previous)||legacyList||mixedList||fullTaskList||taskPeriodFirst||rankWithoutReturn)&&!current.equals(replacement)){
            target.set("layout",replacement);return true;
        }
        return false;
    }

    private static boolean upgradeInboxMenus(YamlConfiguration target,YamlConfiguration defaults,String resource){
        boolean changed=false;
        if(resource.equals("gui/main_menu.yml")&&!target.contains("icons.Q")
                &&defaults.contains("icons.Q")&&target.getStringList("layout").equals(
                    List.of("#########","#IATARAB#","#ACAAAWA#","#AAGAUAA#","#########"))){
            target.set("layout",List.of("#########","#IATARAB#","#ACQAAWA#","#AAGAUAA#","#########"));changed=true;
        }
        if(List.of("gui/invitation.yml","gui/gift.yml").contains(resource)&&defaults.contains("icons.D.lore")
                &&target.getStringList("icons.D.lore").equals(List.of("<gray>{entry.description}</gray>","<yellow>左键点击使用</yellow>"))){
            target.set("icons.D.lore",defaults.getStringList("icons.D.lore"));changed=true;
        }
        if(resource.equals("messages.yml")){
            var old=java.util.Map.of(
                "help-menu","<gray>/marry menu — 打开结婚系统主菜单，也可直接输入 /marry</gray>",
                "help-acceptinvitation","<gray>/marry acceptinvitation — 接受有效的婚礼请帖</gray>",
                "help-denyinvitation","<gray>/marry denyinvitation — 拒绝有效的婚礼请帖</gray>",
                "request-ambiguous","<yellow>你有多种请求，请指定 proposal 或 invitation。</yellow>");
            for(var entry:old.entrySet())if(defaults.contains(entry.getKey())&&entry.getValue().equals(target.getString(entry.getKey()))){
                target.set(entry.getKey(),defaults.getString(entry.getKey()));changed=true;
            }
        }
        return changed;
    }

    private static boolean upgradeGuiTo45Slots(YamlConfiguration target, String resource) {
        if (!resource.startsWith("gui/")) return false;
        var current = target.getStringList("layout");
        if (current.size() == 6 && "P###F###N".equals(current.get(5))) {
            var layout = new java.util.ArrayList<>(current.subList(0, 5));
            layout.set(4, "#########");
            target.set("layout", layout);
            return true;
        }
        // 精确识别 2.8.4 默认列表布局，保留 30 个条目并腾出固定底栏。
        if (current.size() == 5 && "#########".equals(current.get(0))
                && current.subList(1, 4).stream().allMatch("#DDDDDDD#"::equals)
                && "DDDDDDDDD".equals(current.get(4))
                && target.getConfigurationSection("icons") != null
                && target.getConfigurationSection("icons").contains("D")) {
            target.set("layout", List.of("DDDDDDDDD", "#DDDDDDD#", "#DDDDDDD#", "#DDDDDDD#", "#########"));
            return true;
        }
        if (resource.equals("gui/main_menu.yml") && current.equals(List.of("#########","#IATARAB#","#ACAAAWA#","#AAGAUAA#","#AAAAAAA#"))
                || resource.equals("gui/wedding_plan.yml") && current.equals(List.of("#########","#LAIAGAA#","#AAASACA#","#AAAAAAA#","#AAAAAAA#"))) {
            var layout = new java.util.ArrayList<>(current);
            layout.set(4, "#########");
            target.set("layout", layout);
            return true;
        }
        return false;
    }

    private static boolean upgradeKnownDefaults(YamlConfiguration target, String resource) {
        boolean changed=false;
        if(resource.startsWith("gui/"))for(String field:List.of("open","close")){
            String key="sounds."+field;
            // 仅迁移发行版错误音效键；保留自定义资源包音效及音量/音调。
            if(("minecraft:ui.chest."+field).equals(target.getString(key))){target.set(key,"minecraft:block.chest."+field);changed=true;}
        }
        if(resource.equals("messages.yml") && "点击查看榜单；榜内返回按钮可切换类型".equals(target.getString("rank-select-description"))){
            target.set("rank-select-description","点击查看榜单；可使用 /marry rank ＜类型＞ 切换榜单");changed=true;
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
