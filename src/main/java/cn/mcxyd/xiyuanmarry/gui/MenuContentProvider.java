package cn.mcxyd.xiyuanmarry.gui;
import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.service.*;
import java.util.*;

/** 动态内容仅依赖不可变玩家/数据库快照，不创建或保存 Inventory。 */
public final class MenuContentProvider {
    public record Entry(String value,Map<String,Object> tokens) {
        public Entry {tokens=Map.copyOf(tokens);}
        public Object[] placeholders() {var out=new ArrayList<Object>();tokens.forEach((k,v)->{out.add("entry."+k);out.add(v);});return out.toArray();}
    }
    private final ConfigurationManager config;private final MarriageService marriages;private final MessageService messages;
    private final RankingService rankings=new RankingService();
    public MenuContentProvider(ConfigurationManager c,MarriageService m,MessageService msg) {config=c;marriages=m;messages=msg;}
    public List<Entry> entries(PlayerSnapshot me,String page,String mode) {
        if(page.equals("partner_info")) {
            var marriage = marriages.view().byPlayer().get(me.id());
            if (marriage == null) return List.of();
            int level = marriages.level(marriage.bond());
            var bonus = BondAttributeService.calculate(level,
                    config.config().getBoolean("bond.attributes.enabled", true) && marriage.state() == MarriageState.MARRIED,
                    config.config().getDouble("bond.attributes.per-level.max-health", 1.0),
                    config.config().getDouble("bond.attributes.per-level.attack-damage", 0.25),
                    config.config().getDouble("bond.attributes.per-level.movement-speed", 0.005));
            String name = plain("partner-info-name", "player", marriages.name(marriage.partnerOf(me.id())));
            String description = plain("partner-info-description", "level", level, "title", marriages.title(level),
                    "bond", marriage.bond(), "max-health", format(bonus.maxHealth()),
                    "attack-damage", format(bonus.attackDamage()), "movement-speed", format(bonus.movementSpeed()));
            return List.of(new Entry(me.id().toString(), Map.of("name", name, "description", description)));
        }
        if(page.equals("propose")||page.equals("send_invite"))return marriages.directory().all().stream()
            .filter(p->!p.id().equals(me.id())&&(!page.equals("propose")||!marriages.view().byPlayer().containsKey(p.id())))
            .map(p->new Entry(p.liveId().toString(),Map.of("name",p.name(),"description",p.onlineMinutes()))).toList();
        if(!page.equals("rank"))return List.of();
        if(mode.isBlank())return RankingService.BOARDS.stream().map(board->new Entry(board,
            Map.of("name",boardTitle(board),"description",messages.raw("rank-select-description")))).toList();
        var ranked=rankings.rank(marriages.view().couples(),mode,System.currentTimeMillis(),marriages::level,config.file("rank.yml").getInt("top-size",10));
        var result=new ArrayList<Entry>();
        for(int i=0;i<ranked.size();i++){
            var m=ranked.get(i);long value=switch(mode){case "duration"->RankingService.days(m,System.currentTimeMillis());case "online"->m.sharedSeconds();case "total"->m.totalBond();default->m.bond();};
            String name=plain("rank-entry-name","rank",i+1,"player1",marriages.name(m.playerOne()),"player2",marriages.name(m.playerTwo()));
            String description=plain("rank-metric-"+mode,"value",value,"level",marriages.level(m.bond()));
            result.add(new Entry(m.playerOne().toString(),Map.of("name",name,"description",description)));
        }
        return List.copyOf(result);
    }
    public static List<Entry> filter(List<Entry> entries,String query){
        if(query==null||query.isBlank())return entries;
        String needle=query.strip().toLowerCase(Locale.ROOT);
        return entries.stream().filter(entry->entry.value().toLowerCase(Locale.ROOT).contains(needle)
                ||String.valueOf(entry.tokens().getOrDefault("name","")).toLowerCase(Locale.ROOT).contains(needle)
                ||String.valueOf(entry.tokens().getOrDefault("description","")).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    public List<Entry> invitations(List<WeddingInvitationService.Invitation> invitations){
        return invitations.stream().map(invite->new Entry(invite.weddingId(),Map.of(
                "name",plain("menu-invitation-name","player1",marriages.name(invite.playerOne()),"player2",marriages.name(invite.playerTwo())),
                "description",plain("menu-invitation-description","id",invite.weddingId()),
                "hint",messages.raw("menu-invitation-hint")))).toList();
    }
    public List<Entry> inbox(List<InboxMessage> notices){
        var result=new ArrayList<Entry>();
        for(var notice:notices){
            boolean claimable=Set.of("reward-id","reward-weekly-id","gift-id","wedding-gift-id").contains(notice.key());
            if(!claimable&&!Set.of("reward-review-id","delivery-review").contains(notice.key()))continue;
            String id="";for(int i=0;i+1<notice.values().size();i+=2)if("id".equals(notice.values().get(i)))id=notice.values().get(i+1).toString();
            result.add(new Entry(claimable?id:"",Map.of("name",plain("menu-inbox-"+notice.key()),
                    "description",plain(notice.key(),notice.values().toArray()),
                    "hint",messages.raw(claimable?"menu-claim-hint":"menu-review-hint"))));
        }
        return List.copyOf(result);
    }
    public List<Entry> tasks(DailyTask today) {
        return TaskMenuModel.rows(today,config.snapshot().tasks()).stream().map(row->{
            String status=messages.raw("task-state-"+row.state());
            return new Entry(Integer.toString(row.day()),Map.of(
                "name",plain("task-entry-name","cycle",row.cycle(),"day",row.day(),"task",row.definition().name()),
                "description",plain(row.state().equals("active")||row.state().equals("completed")?"task-entry-description":"task-other-description","status",status,"progress",row.progress(),"target",row.definition().target(),"bond",row.definition().bondReward()),
                "status",status,"progress",row.progress(),"target",row.definition().target(),"bond",row.definition().bondReward()));
        }).toList();
    }
    private String plain(String key,Object...values) {return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(messages.renderer().format(messages.raw(key),values));}
    private String format(double value) {return String.format(Locale.ROOT, "%.3f", value);}
    private String boardTitle(String mode) {String key=switch(mode){case "bond"->"bond-level";case "total"->"bond-total";default->mode;};return config.file("rank.yml").getString("types."+key,messages.raw("rank-header"));}
}
