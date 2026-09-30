package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.MarriageRepository;
import java.util.*;
import java.util.function.Predicate;
import static cn.mcxyd.xiyuanmarry.service.RuleViolation.require;

/** 请帖只在事务内核对有效订婚；跨线程返回不可变摘要，不缓存玩家对象。 */
public final class WeddingInvitationService {
    public record Invitation(String weddingId, UUID playerOne, UUID playerTwo, long expires) {}
    private final MarriageService marriages;
    private final Predicate<String> running;
    public WeddingInvitationService(MarriageService marriages, Predicate<String> running) {
        this.marriages=marriages;this.running=running;
    }
    public List<Invitation> pending(MarriageRepository repository, UUID guest) {
        long now=System.currentTimeMillis();
        var result=new ArrayList<Invitation>();
        for(var marriage:repository.findAll()){
            if(marriage.state()!=MarriageState.ENGAGED || !"WEDDING".equals(marriage.type())
                    || marriage.contains(guest) || running.test(marriage.id())
                    || marriage.createdAt()+marriages.setting("marriage.engagement-hours",48)*3600000L<=now)continue;
            String raw=repository.get("weddings",marriage.id());
            if(raw==null)continue;
            WeddingPlan plan;
            try { plan=marriages.json().fromJson(raw,WeddingPlan.class); }
            catch (RuntimeException invalid) { marriages.warnMetadata("weddings",marriage.id()); continue; }
            if(plan==null||plan.invites()==null){ marriages.warnMetadata("weddings",marriage.id()); continue; }
            var invite=plan.invites().get(guest);
            if(invite!=null&&!invite.accepted()&&invite.expires()>now)
                result.add(new Invitation(marriage.id(),marriage.playerOne(),marriage.playerTwo(),invite.expires()));
        }
        result.sort(Comparator.comparingLong(Invitation::expires).thenComparing(Invitation::weddingId));
        return List.copyOf(result);
    }
    public void respond(PlayerSnapshot guest,String selected,boolean accept) {
        marriages.submit(guest.liveId(),repository->{
            var candidates=pending(repository,guest.id()).stream()
                    .filter(invitation->selected==null||selected.equals(invitation.weddingId())).toList();
            require(!candidates.isEmpty(),"request-missing");
            require(candidates.size()==1,"request-ambiguous");
            var chosen=candidates.getFirst();
            WeddingPlan plan;
            try { plan=marriages.json().fromJson(repository.get("weddings",chosen.weddingId()),WeddingPlan.class); }
            catch (RuntimeException invalid) { marriages.warnMetadata("weddings",chosen.weddingId()); throw new RuleViolation("request-missing"); }
            require(plan!=null&&plan.invites()!=null,"request-missing");
            var marriage=repository.findByPlayer(chosen.playerOne());
            long now=System.currentTimeMillis();
            require(marriage!=null&&marriage.id().equals(chosen.weddingId())
                    &&marriage.state()==MarriageState.ENGAGED&&"WEDDING".equals(marriage.type())
                    &&!running.test(chosen.weddingId())&&chosen.expires()>now,"request-missing");
            long engagementDeadline=Math.addExact(marriage.createdAt(),
                    Math.multiplyExact(marriages.setting("marriage.engagement-hours",48),3_600_000L));
            require(engagementDeadline>now,"request-missing");
            // 回复期限只约束尚未接受的邀请；已接受资格与本次订婚同期限，并在同一事务中写入。
            repository.put("weddings",chosen.weddingId(),marriages.json().toJson(
                    plan.invite(guest.id(),new WeddingPlan.Invite(accept,accept?engagementDeadline:0))));
            return null;
        },ignored->marriages.notifyLive(guest.liveId(),accept?"invite-accepted":"proposal-denied"));
    }
}
