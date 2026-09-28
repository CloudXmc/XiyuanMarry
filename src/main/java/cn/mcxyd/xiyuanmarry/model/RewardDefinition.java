package cn.mcxyd.xiyuanmarry.model;

import java.util.List;

public record RewardDefinition(long days, long money, long experience, List<String> commands) {
    public RewardDefinition {
        if (days < 1 || money < 0 || experience < 0 || commands == null) throw new IllegalArgumentException();
        commands = List.copyOf(commands);
    }
}
