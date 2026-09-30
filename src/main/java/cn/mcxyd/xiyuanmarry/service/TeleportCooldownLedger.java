package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.repository.MarriageRepository;
import java.util.UUID;
public final class TeleportCooldownLedger {
    public record Ticket(UUID actor,String token,long until) {}
    public Ticket reserve(MarriageRepository r,UUID actor,long now,long duration) {
        if (duration<0 || duration>86400000L) throw new IllegalArgumentException("传送冷却超出范围");
        return r.transaction(tx->{
            long until=MarriageService.number(tx,"teleport-cooldowns",actor.toString());
            if(until>now){
                // 从事务读取的既有到期时间计算；向上取整，避免仍受限时显示剩余 0 秒。
                long millis=until-now,seconds=millis/1000+(millis%1000==0?0:1);
                throw new RuleViolation("teleport-cooldown","minutes",seconds/60,"seconds",seconds%60,"remaining-seconds",seconds);
            }
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
