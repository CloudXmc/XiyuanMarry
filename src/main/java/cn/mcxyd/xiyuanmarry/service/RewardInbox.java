package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.model.RewardTicket;
import java.util.*;

/** 只返回当前领取者的不可变列表；领取中和异常票据不会在菜单中消失。 */
record RewardInbox(List<RewardTicket> available, List<UUID> review) {
    RewardInbox { available = List.copyOf(available); review = List.copyOf(review); }
    static RewardInbox forRecipient(List<RewardTicket> tickets, UUID recipient) {
        var owned = tickets.stream().filter(t -> t.recipient().equals(recipient))
                .sorted(Comparator.comparingLong(RewardTicket::created).thenComparing(RewardTicket::id)).toList();
        return new RewardInbox(owned.stream().filter(t -> t.state().equals("COMMITTED")).toList(),
                owned.stream().filter(t -> !t.state().equals("COMMITTED")).map(RewardTicket::id).toList());
    }
}
