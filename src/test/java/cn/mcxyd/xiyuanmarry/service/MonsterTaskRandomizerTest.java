package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.model.TaskDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MonsterTaskRandomizerTest {
    @Test
    void randomTaskChoosesStableCommonMonsterPerCoupleAndDay() {
        var definition = new TaskDefinition("kill-specific", "KILL_SPECIFIC",
                "共同击杀指定怪物：{monster}", MonsterTaskRandomizer.RANDOM_COMMON_MONSTER, 3, 20);

        var first = MonsterTaskRandomizer.resolve(definition, "couple-a", 12);
        var second = MonsterTaskRandomizer.resolve(definition, "couple-a", 12);

        assertEquals(first, second);
        assertTrue(MonsterTaskRandomizer.COMMON_MONSTERS.contains(first.selector()));
        assertFalse(first.name().contains("{monster}"));
        assertTrue(first.name().contains(MonsterTaskRandomizer.displayName(first.selector())));
    }

    @Test
    void randomPoolExcludesRareBossAndUncommonEntities() {
        assertFalse(MonsterTaskRandomizer.COMMON_MONSTERS.contains("WITHER"));
        assertFalse(MonsterTaskRandomizer.COMMON_MONSTERS.contains("ENDER_DRAGON"));
        assertFalse(MonsterTaskRandomizer.COMMON_MONSTERS.contains("ELDER_GUARDIAN"));
        assertFalse(MonsterTaskRandomizer.COMMON_MONSTERS.contains("ENDERMITE"));
        assertFalse(MonsterTaskRandomizer.COMMON_MONSTERS.contains("SILVERFISH"));
        assertFalse(MonsterTaskRandomizer.COMMON_MONSTERS.contains("WARDEN"));
    }
}
