package cn.mcxyd.xiyuanmarry.model;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 保持序列化字段兼容；旧纪念日票据没有 rank 时使用 0。 */
public record RewardTicket(UUID id, UUID recipient, String relationship, long created, long updated,
                           String state, long anniversaryDays, int rank, long money, long experience,
                           List<String> commands) {
    public RewardTicket {
        Objects.requireNonNull(id);
        Objects.requireNonNull(recipient);
        Objects.requireNonNull(state);
        commands = commands == null ? List.of() : List.copyOf(commands);
    }
    public RewardTicket withState(String next, long now) {
        return new RewardTicket(id, recipient, relationship, created, now, next, anniversaryDays, rank, money, experience, commands);
    }
}
