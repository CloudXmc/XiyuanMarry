package cn.mcxyd.xiyuanmarry.model;

/** 日序从结婚时刻起计算，不随第三十天循环归零；避免跨周期重用奖励编号。 */
public record DailyTask(String coupleId, long daySerial, TaskDefinition definition, long progress, boolean completed) {
    public int cycleDay() { return (int) (daySerial % 30) + 1; }
    public String key() { return coupleId + ":" + daySerial; }
}
