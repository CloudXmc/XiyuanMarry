package cn.mcxyd.xiyuanmarry.config;

import cn.mcxyd.xiyuanmarry.model.RewardDefinition;
import cn.mcxyd.xiyuanmarry.service.RankingService;
import cn.mcxyd.xiyuanmarry.service.WeeklyRewardSchedule;
import org.bukkit.configuration.ConfigurationSection;
import java.time.*;
import java.util.*;

/** 校验后一次发布完整目录，扫描中不得读取正在重载的可变 Map。 */
public record RewardCatalog(Map<Long, RewardDefinition> anniversaries, boolean weeklyEnabled, String board,
                            WeeklyRewardSchedule schedule, Map<Integer, RewardDefinition> weekly, List<Long> thresholds) {
    public RewardCatalog {
        anniversaries = Map.copyOf(anniversaries);
        weekly = Map.copyOf(weekly);
        thresholds = List.copyOf(thresholds);
    }
    public int level(long bond) {
        int level = 1;
        for (int i = 1; i < thresholds.size(); i++) if (bond >= thresholds.get(i)) level = i + 1;
        return level;
    }
    public static RewardCatalog parse(ConfigurationSection file, ConfigurationSection main) {
        Map<Long, RewardDefinition> anniversaries = new LinkedHashMap<>();
        var dates = section(file, "anniversaries");
        if (dates != null) for (String key : dates.getKeys(false)) {
            long days = numericKey(key, Long.MAX_VALUE);
            anniversaries.put(days, definition(dates, key, days));
        }
        var config = section(file, "weekly-top");
        String board = config == null ? "bond" : config.getString("board", "bond").toLowerCase(Locale.ROOT);
        if (!RankingService.BOARDS.contains(board)) throw new IllegalArgumentException("周榜类型必须为 bond/duration/online/total");
        DayOfWeek day;
        try { day = DayOfWeek.valueOf(config == null ? "SUNDAY" : config.getString("day-of-week", "SUNDAY").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("周榜星期无效", e); }
        int hour = config == null || !config.contains("hour") ? 20 : (int) number(config, "hour", 23);
        if (config != null && config.contains("enabled") && !config.isBoolean("enabled"))
            throw new IllegalArgumentException("周榜 enabled 必须为布尔值");
        boolean enabled = config != null && config.getBoolean("enabled", true);
        Map<Integer, RewardDefinition> weekly = new LinkedHashMap<>();
        var ranks = config == null ? null : section(config, "rewards");
        if (ranks != null) for (String key : ranks.getKeys(false)) {
            int rank = (int) numericKey(key, 1000);
            weekly.put(rank, definition(ranks, key, rank));
        }
        var levels = new ArrayList<Long>();
        for (int i = 1; i <= 10; i++) levels.add(main.getLong("bond.levels." + i + ".required"));
        return new RewardCatalog(anniversaries, enabled, board,
                new WeeklyRewardSchedule(ZoneId.of(main.getString("timezone", "Asia/Shanghai")), day, hour), weekly, levels);
    }
    private static ConfigurationSection section(ConfigurationSection root, String key) {
        if (root.contains(key) && !root.isConfigurationSection(key))
            throw new IllegalArgumentException("奖励配置必须为映射：" + key);
        return root.getConfigurationSection(key);
    }
    private static long numericKey(String key, long max) {
        try {
            long value = Long.parseLong(key);
            if (value < 1 || value > max || !Long.toString(value).equals(key)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("奖励键必须为 1 至 " + max + " 的整数：" + key, e); }
    }
    private static RewardDefinition definition(ConfigurationSection root, String key, long days) {
        var entry = section(root, key);
        if (entry == null) throw new IllegalArgumentException("奖励项缺失：" + key);
        List<String> commands = List.of();
        if (entry.contains("commands")) {
            var raw = entry.getList("commands");
            if (raw == null || raw.stream().anyMatch(value -> !(value instanceof String)))
                throw new IllegalArgumentException("奖励 commands 必须为文本列表：" + key);
            commands = raw.stream().map(String.class::cast).toList();
        }
        return new RewardDefinition(days, number(entry, "money", Long.MAX_VALUE),
                number(entry, "experience", Integer.MAX_VALUE), commands);
    }
    private static long number(ConfigurationSection root, String key, long max) {
        if (!root.contains(key)) return 0;
        if (!root.isInt(key) && !root.isLong(key)) throw new IllegalArgumentException("奖励字段必须为整数：" + key);
        long value = root.getLong(key);
        if (value < 0 || value > max) throw new IllegalArgumentException("奖励字段超出范围：" + key);
        return value;
    }
}
