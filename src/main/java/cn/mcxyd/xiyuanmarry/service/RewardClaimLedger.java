package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.model.RewardTicket;
import cn.mcxyd.xiyuanmarry.repository.DatabaseManager;
import cn.mcxyd.xiyuanmarry.repository.MarriageRepository;
import com.google.gson.Gson;
import java.util.UUID;
import static cn.mcxyd.xiyuanmarry.service.RuleViolation.require;

/** 奖励领取的数据库状态；只从异步 IO 上下文调用。 */
final class RewardClaimLedger {
    static final String INBOX = "reward-inbox";
    static final String TOKENS = "reward-claims";
    private final DatabaseManager database;
    private final Gson gson = new Gson();
    RewardClaimLedger(DatabaseManager database) { this.database = database; }
    record Claim(UUID generation, UUID token, RewardTicket ticket) {}
    Claim reserve(UUID recipient, UUID id, long now) {
        return reserve(database.generation(), recipient, id, now);
    }
    Claim reserve(UUID generation, UUID recipient, UUID id, long now) {
        return database.use(generation, r -> r.transaction(tx -> {
            RewardTicket ticket = read(tx, id);
            if (ticket == null || !ticket.recipient().equals(recipient)) return null;
            require(ticket.state().equals("COMMITTED"), "delivery-review");
            RewardTicket claiming = ticket.withState("CLAIMING", now);
            UUID token = UUID.randomUUID();
            tx.put(INBOX, id.toString(), gson.toJson(claiming));
            tx.put(TOKENS, id.toString(), token.toString());
            return new Claim(generation, token, claiming);
        }));
    }
    boolean complete(Claim claim, long now) { return finish(claim, null, now); }
    boolean restore(Claim claim, long now) { return finish(claim, "COMMITTED", now); }
    boolean review(Claim claim, long now) { return finish(claim, "REVIEW", now); }
    private boolean finish(Claim claim, String state, long now) {
        try { return database.use(claim.generation(), r -> r.transaction(tx -> {
            RewardTicket current = read(tx, claim.ticket().id());
            if (current == null || !current.state().equals("CLAIMING")
                    || !claim.token().toString().equals(tx.get(TOKENS, current.id().toString()))) return false;
            if (state == null) tx.remove(INBOX, current.id().toString());
            else tx.put(INBOX, current.id().toString(), gson.toJson(current.withState(state, now)));
            tx.remove(TOKENS, current.id().toString());
            return true;
        })); } catch (DatabaseManager.StaleGenerationException ignored) {
            // 不把旧票据写入新库；旧库保留 CLAIMING，回切后仍需核对，不自动重试。
            return false;
        }
    }
    private RewardTicket read(MarriageRepository repository, UUID id) {
        String json = repository.get(INBOX, id.toString());
        return json == null ? null : gson.fromJson(json, RewardTicket.class);
    }
}
