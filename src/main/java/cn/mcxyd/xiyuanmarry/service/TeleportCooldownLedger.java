package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.repository.MarriageRepository;
import java.util.UUID;
public final class TeleportCooldownLedger {
    public record Ticket(UUID actor,String token,long until) {}
    public Ticket reserve(MarriageRepository r,UUID actor,long now,long duration) {
        if (duration<0 || duration>86400000L) throw new IllegalArgumentException("传送冷却超出范围");
        return r.transaction(tx->{
            RuleViolation.require(MarriageService.number(tx,"teleport-cooldowns",actor.toString())<=now,"teleport-cooldown");
            var ticket=new Ticket(actor,UUID.randomUUID().toString(),now+duration);
            tx.put("teleport-cooldowns",actor.toString(),Long.toString(ticket.until()));
            tx.put("teleport-tokens",actor.toString(),ticket.token());
            return ticket;
        });
    }
    public boolean release(MarriageRepository r,Ticket ticket) {
        return r.transaction(tx->{
            String key=ticket.actor().toString();
            if (!ticket.token().equals(tx.get("teleport-tokens",key))) return false;
            tx.remove("teleport-cooldowns",key);
            tx.remove("teleport-tokens",key);
            return true;
        });
    }
}
