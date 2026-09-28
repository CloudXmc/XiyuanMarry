package cn.mcxyd.xiyuanmarry.service;

import java.util.List;

/** 异步查询只生成消息键与不可变占位符，不携带任何 Bukkit 实时对象。 */
public record InboxMessage(String key, List<Object> values) {
    public InboxMessage { values = List.copyOf(values); }
    public static InboxMessage of(String key, Object... values) {
        return new InboxMessage(key, List.of(values));
    }
}
