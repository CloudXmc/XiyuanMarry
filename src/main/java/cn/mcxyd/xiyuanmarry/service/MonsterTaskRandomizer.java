package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.model.TaskDefinition;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/** 为每对情侣和每天稳定抽取一种常见怪物，任务记录保存抽取后的定义，避免刷新或重启换目标。 */
public final class MonsterTaskRandomizer {
    public static final String RANDOM_COMMON_MONSTER = "RANDOM_COMMON_MONSTER";
    private static final Map<String, String> NAMES = names();
    public static final List<String> COMMON_MONSTERS = List.copyOf(NAMES.keySet());

    private MonsterTaskRandomizer() {}

    public static TaskDefinition resolve(TaskDefinition definition, String coupleId, long daySerial) {
        if (definition == null || !"KILL_SPECIFIC".equals(definition.type())
                || !RANDOM_COMMON_MONSTER.equalsIgnoreCase(definition.selector())) return definition;
        if (coupleId == null || coupleId.isBlank()) throw new IllegalArgumentException("情侣关系编号不能为空");
        long seed = 1125899906842597L;
        for (int i = 0; i < coupleId.length(); i++) seed = 31 * seed + coupleId.charAt(i);
        seed ^= daySerial * 0x9E3779B97F4A7C15L;
        String monster = COMMON_MONSTERS.get(new SplittableRandom(seed).nextInt(COMMON_MONSTERS.size()));
        String display = displayName(monster);
        String name = definition.name().replace("{monster}", display);
        if (name.equals(definition.name())) name = definition.name() + "：" + display;
        return new TaskDefinition(definition.id(), definition.type(), name, monster,
                definition.target(), definition.bondReward());
    }

    public static String displayName(String entityType) {
        return NAMES.getOrDefault(entityType, entityType);
    }

    private static Map<String, String> names() {
        var names = new LinkedHashMap<String, String>();
        names.put("ZOMBIE", "僵尸");
        names.put("SKELETON", "骷髅");
        names.put("CREEPER", "苦力怕");
        names.put("SPIDER", "蜘蛛");
        names.put("CAVE_SPIDER", "洞穴蜘蛛");
        names.put("DROWNED", "溺尸");
        names.put("HUSK", "尸壳");
        names.put("STRAY", "流浪者");
        names.put("PHANTOM", "幻翼");
        names.put("SLIME", "史莱姆");
        names.put("MAGMA_CUBE", "岩浆怪");
        names.put("BLAZE", "烈焰人");
        names.put("GHAST", "恶魂");
        names.put("ENDERMAN", "末影人");
        names.put("WITCH", "女巫");
        names.put("PILLAGER", "掠夺者");
        return Collections.unmodifiableMap(names);
    }
}
