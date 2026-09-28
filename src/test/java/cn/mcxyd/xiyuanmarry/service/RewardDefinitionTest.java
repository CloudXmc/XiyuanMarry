package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.model.RewardDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RewardDefinitionTest {
    @Test void copiesCommandsAndKeepsRewardValues() {
        var definition = new RewardDefinition(30, 2000, 500, List.of("give {player} cake 1"));
        assertEquals(30, definition.days());
        assertEquals(List.of("give {player} cake 1"), definition.commands());
    }

    @Test void rejectsInvalidMilestoneValues() {
        assertThrows(IllegalArgumentException.class, () -> new RewardDefinition(0, 0, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RewardDefinition(7, -1, 0, List.of()));
    }
}
