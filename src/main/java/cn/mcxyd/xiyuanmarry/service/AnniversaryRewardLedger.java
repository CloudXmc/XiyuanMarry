package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.model.MarriageRecord;
import cn.mcxyd.xiyuanmarry.model.MarriageState;
import cn.mcxyd.xiyuanmarry.model.RewardDefinition;
import cn.mcxyd.xiyuanmarry.model.RewardTicket;
import cn.mcxyd.xiyuanmarry.repository.MarriageRepository;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 纪念日入箱事务；只接收关系快照和奖励定义，在 IO 上下文使用。 */
final class AnniversaryRewardLedger {
    private static final String INBOX = "reward-inbox";
    private static final String ISSUED = "reward-issued";
    private final Gson gson = new Gson();

    List<RewardTicket> issue(MarriageRepository repository, MarriageRecord expected, RewardDefinition definition, long now) {
        return repository.transaction(tx -> {
            MarriageRecord current = tx.findByPlayer(expected.playerOne());
            // 扫描快照仅作候选；入队后可能离婚、再婚或修改日期，必须在同一事务重新核对。
            if (current == null || !current.id().equals(expected.id())
                    || !current.contains(expected.playerOne()) || !current.contains(expected.playerTwo())
                    || current.state() != MarriageState.MARRIED || current.marriedAt() <= 0
                    || now < current.marriedAt() || (now - current.marriedAt()) / 86_400_000L < definition.days())
                return List.of();

            var tickets = new ArrayList<RewardTicket>(2);
            for (UUID recipient : List.of(current.playerOne(), current.playerTwo())) {
                String marker = current.id() + ":" + definition.days() + ":" + recipient;
                // 保留旧版单方标记及已领取记录；只生成尚未发放的一方，不重发历史票据。
                if (tx.get(ISSUED, marker) != null) continue;
                var ticket = new RewardTicket(UUID.randomUUID(), recipient, current.id(), now, now,
                        "COMMITTED", definition.days(), 0, definition.money(), definition.experience(), definition.commands());
                tx.put(INBOX, ticket.id().toString(), gson.toJson(ticket));
                tx.put(ISSUED, marker, ticket.id().toString());
                tickets.add(ticket);
            }
            // 双方缺失票据与标记全部提交或全部回滚，避免第二方写入失败留下单边奖励。
            return List.copyOf(tickets);
        });
    }
}
