package cn.mcxyd.xiyuanmarry.config;
import cn.mcxyd.xiyuanmarry.model.TaskDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;

/** 配置层产生只读任务目录；业务不访问 YAML。 */
public record TaskCatalog(double distance, List<TaskDefinition> schedule, List<String> deferredTypes) {
    public static final Set<String> IMPLEMENTED = Set.copyOf(cn.mcxyd.xiyuanmarry.service.CoupleTaskService.TYPES);
    public TaskCatalog { schedule = List.copyOf(schedule); deferredTypes = List.copyOf(deferredTypes); }
    public TaskDefinition forDay(long serial) {return schedule.get((int)(Math.max(0, serial) % 30));}
    public static TaskCatalog parse(YamlConfiguration yaml, long reward) {
        double distance = yaml.getDouble("default-trigger-distance", 50);
        if (!Double.isFinite(distance) || distance < 1 || distance > 256 || yaml.getInt("cycle-days", 30) != 30)
            throw new IllegalArgumentException("任务距离必须在1至256格，周期必须为30天");
        var definitions = new LinkedHashMap<String, TaskDefinition>();
        var deferred = new ArrayList<String>();
        var ids = new HashSet<String>();
        for (var row : yaml.getMapList("tasks")) {
            String id = Objects.toString(row.get("id"), "");
            if (!ids.add(id)) throw new IllegalArgumentException("重复任务编号：" + id);
            String type = Objects.toString(row.get("type"), "").toUpperCase(Locale.ROOT);
            if (type.startsWith("COMMON_")) type = type.substring(7);
            if (type.equals("EAT_FOOD")) type = "EAT";
            if (!cn.mcxyd.xiyuanmarry.service.CoupleTaskService.TYPES.contains(type))
                throw new IllegalArgumentException("未知任务类型：" + type);
            if (!IMPLEMENTED.contains(type)) {deferred.add(type); continue;}
            long target = integer(row.get("target"));
            long bond = row.containsKey("bond-reward") ? integer(row.get("bond-reward")) : reward;
            definitions.put(id, new TaskDefinition(id, type, Objects.toString(row.get("name"), ""),
                Objects.toString(row.get("value"), "*").toUpperCase(Locale.ROOT), target, bond));
        }
        if (definitions.isEmpty()) throw new IllegalArgumentException("至少配置一个已实现的任务类型");
        var schedule = new ArrayList<TaskDefinition>();
        var order = yaml.getStringList("daily-order");
        if (!order.isEmpty()) {
            if (order.size() != 30) throw new IllegalArgumentException("daily-order必须有30项");
            for (String id : order) {
                var definition = definitions.get(id);
                if (definition == null) throw new IllegalArgumentException("未定义或尚未实现的每日任务：" + id);
                schedule.add(definition);
            }
        } else {
            var available = List.copyOf(definitions.values());
            for (int day = 0; day < 30; day++) schedule.add(available.get(day % available.size()));
        }
        return new TaskCatalog(distance, schedule, deferred);
    }
    private static long integer(Object value) {
        if (!(value instanceof Number n) || n.doubleValue() != n.longValue())
            throw new IllegalArgumentException("任务数量必须为整数");
        return n.longValue();
    }
}
