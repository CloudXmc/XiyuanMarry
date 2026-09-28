package cn.mcxyd.xiyuanmarry.model;

public record TaskDefinition(String id, String type, String name, String selector, long target, long bondReward) {
    public TaskDefinition {
        if (id == null || id.isBlank() || name == null || name.isBlank() || type == null || selector == null
                || target < 1 || target > 1_000_000_000L || bondReward < 0 || bondReward > 1_000_000)
            throw new IllegalArgumentException("任务定义无效");
    }
}
