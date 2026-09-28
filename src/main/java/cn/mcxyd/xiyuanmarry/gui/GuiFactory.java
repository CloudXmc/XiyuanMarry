package cn.mcxyd.xiyuanmarry.gui;
import cn.mcxyd.xiyuanmarry.config.*;import cn.mcxyd.xiyuanmarry.message.*;
import cn.mcxyd.xiyuanmarry.model.*;import cn.mcxyd.xiyuanmarry.service.*;import cn.mcxyd.xiyuanmarry.scheduler.*;
import org.bukkit.Bukkit;import org.bukkit.entity.Player;import java.util.*;

/** 只管理玩家所有者上下文的界面渲染；数据列表由独立组件提供。 */
public final class GuiFactory implements AutoCloseable {
    private final ConfigurationManager config;private final MarriageService marriages;
    private final WeddingService wedding;private final MessageService messages;private final UnifiedScheduler scheduler;
    private final DailyTaskService tasks;private final IconFactory icons;private final MenuContentProvider content;
    private final GuiRequestTracker requests=new GuiRequestTracker();
    private final ClaimInboxService inbox;
    private java.util.function.BiConsumer<Player,String[]> command=(p,a)->{};
    private volatile boolean closed;
    public GuiFactory(ConfigurationManager c,MarriageService m,WeddingService w,MessageService msg,UnifiedScheduler s,DailyTaskService tasks){
        this(c,m,w,msg,s,tasks,null);
    }
    public GuiFactory(ConfigurationManager c,MarriageService m,WeddingService w,MessageService msg,UnifiedScheduler s,DailyTaskService tasks,ClaimInboxService inbox){
        config=c;marriages=m;wedding=w;messages=msg;scheduler=s;this.tasks=tasks;
        this.inbox=inbox;
        icons=new IconFactory(msg.renderer());content=new MenuContentProvider(c,m,msg);
    }
    public void commands(java.util.function.BiConsumer<Player,String[]> handler){command=handler;}
    public List<String> searchCandidates(String page){
        if(!Set.of("propose","normal","send_invite").contains(page.toLowerCase(Locale.ROOT)))return List.of();
        return marriages.directory().all().stream().map(PlayerSnapshot::name).toList();
    }
    public void cancel(UUID player){requests.cancel(player);}
    public void reload(){requests.clear();}
    @Override public void close(){closed=true;requests.clear();}
    public void open(Player player,String page,String mode,int index){
        openFiltered(player,page,mode,"");
    }
    public void openFiltered(Player player,String page,String mode,String filter){
        if(!player.hasPermission("marry.use")){messages.send(player,"no-permission");return;}
        if(closed)return;requests.cancel(player.getUniqueId());
        var actor=marriages.directory().capture(player);var snapshot=config.snapshot();
        if(actor==null||player.isDead())return;
        UUID databaseGeneration=marriages.databaseGeneration();
        if(page.equals("gift")||page.equals("invitation")){
            if(page.equals("gift")&&inbox==null){messages.send(player,"feature-unavailable");return;}
            UUID token=requests.begin(actor.liveId(),System.currentTimeMillis());
            if(token==null){messages.send(player,"busy");return;}
            messages.send(player,"menu-loading");
            if(page.equals("gift"))inbox.load(actor,(owner,notices)->
                completeOpen(owner,actor,snapshot.generation(),databaseGeneration,token,page,mode,filter,content.inbox(notices)));
            else marriages.submitAtGeneration(actor.liveId(),databaseGeneration,
                repository->wedding.pendingInvitations(repository,actor.id()),
                list->scheduler.player(actor.liveId(),owner->completeOpen(owner,actor,snapshot.generation(),
                    databaseGeneration,token,page,mode,filter,content.invitations(list))),error->{});
            return;
        }
        if(page.equals("task")){
            UUID token=requests.begin(actor.liveId(),System.currentTimeMillis());
            if(token==null){messages.send(player,"busy");return;}
            messages.send(player,"task-loading");
            tasks.load(actor,task->scheduler.player(actor.liveId(),owner->{
                if(closed||!snapshot.generation().equals(config.snapshot().generation())
                    ||!requests.consume(actor.liveId(),token,System.currentTimeMillis()))return;
                var current=marriages.view().byPlayer().get(actor.id());
                if(current==null||!current.id().equals(task.coupleId())||!current.married())return;
                render(owner,page,mode,0,content.tasks(task));
            }));
            return;
        }
        render(player,page,mode,0,MenuContentProvider.filter(content.entries(actor,page,mode),filter));
    }
    private void completeOpen(Player owner,PlayerSnapshot actor,UUID configGeneration,UUID databaseGeneration,
            UUID token,String page,String mode,String filter,List<MenuContentProvider.Entry> entries){
        if(closed||!configGeneration.equals(config.snapshot().generation())
                ||!databaseGeneration.equals(marriages.databaseGeneration())||owner.isDead()||!owner.hasPermission("marry.use"))return;
        var current=marriages.directory().live(actor.liveId());
        if(current==null||!current.id().equals(actor.id())||!current.identityKey().equals(actor.identityKey())
                ||!requests.consume(actor.liveId(),token,System.currentTimeMillis()))return;
        render(owner,page,mode,0,MenuContentProvider.filter(entries,filter));
    }
    void render(Player player,String page,String mode,int index,List<MenuContentProvider.Entry> entries){
        var snapshot=config.snapshot();var layout=snapshot.menus().get(page);
        if(layout==null){messages.send(player,"invalid-argument");return;}
        var slots=layout.dynamicSlots();int capacity=Math.max(1,slots.size());
        // 45 格菜单不再提供分页按钮；超出可视区域的条目保持不可选，避免无入口的隐藏页。
        int number=0;
        var actions=new HashMap<Integer,XiyuanHolder.Action>();
        var items=new HashMap<Integer,org.bukkit.inventory.ItemStack>();
        for(int i=0;i<layout.size();i++){
            char ch=layout.rows().get(i/9).charAt(i%9);if(ch=='A'||ch=='D')continue;
            var icon=layout.icons().get(ch);items.put(i,icons.create(icon));
            if(!icon.action().isEmpty())actions.put(i,new XiyuanHolder.Action(icon.action(),icon.returnCommand()));
        }
        for(int j=0;j<slots.size();j++){
            int n=number*capacity+j;if(n>=entries.size())break;
            var entry=entries.get(n);int slot=slots.get(j);var template=layout.icons().get('D');
            items.put(slot,icons.create(template,entry.placeholders()));
            if(!entry.value().isBlank())actions.put(slot,new XiyuanHolder.Action(template.action(),entry.value()));
        }
        var holder=new XiyuanHolder(player.getUniqueId(),snapshot.generation(),page,mode,number,actions);
        var inventory=Bukkit.createInventory(holder,layout.size(),messages.renderer().gui(messages.parse(layout.title())));
        holder.attach(inventory);items.forEach(inventory::setItem);player.openInventory(inventory);playSound(player, layout, layout.sounds().open());
        if(entries.isEmpty()&&!slots.isEmpty())messages.send(player,"empty");
        if(entries.size()>slots.size())messages.send(player,"menu-overflow","shown",slots.size(),"total",entries.size(),"page",page.equals("propose")&&mode.equals("NORMAL")?"normal":page);
    }
    public void playClick(Player player, XiyuanHolder holder) { var layout=config.snapshot().menus().get(holder.page()); if(layout!=null) playSound(player,layout,layout.sounds().click()); }
    public void playClose(Player player, String page) { var layout=config.snapshot().menus().get(page); if(layout!=null) playSound(player,layout,layout.sounds().close()); }
    private void playSound(Player player, GuiLayout layout, String name) { try { player.playSound(net.kyori.adventure.sound.Sound.sound(net.kyori.adventure.key.Key.key(name), net.kyori.adventure.sound.Sound.Source.MASTER, layout.sounds().volume(), layout.sounds().pitch())); } catch (RuntimeException ex) { config.warn("GUI 音效配置无效："+name); } }
    public void activate(Player player,XiyuanHolder holder,XiyuanHolder.Action action){
        activate(player,holder,action,false);
    }
    public void activate(Player player,XiyuanHolder holder,XiyuanHolder.Action action,boolean alternate){
        if(closed)return;
        if(!player.hasPermission("marry.use")){messages.send(player,"no-permission");return;}
        if(!holder.generation().equals(config.snapshot().generation())){open(player,holder.page(),holder.mode(),holder.index());return;}
        switch(action.key()){
            case "normal-marriage" -> open(player,"propose","NORMAL",0);
            case "wedding-marriage" -> open(player,"propose","WEDDING",0);
            case "wedding-plan" -> open(player,"wedding_plan","",0);
            case "invitations" -> open(player,"send_invite","",0);
            case "received-invitations" -> open(player,"invitation","",0);
            case "rank" -> open(player,"rank","",0);
            case "info" -> {
                var actor = marriages.directory().capture(player);
                if (actor != null && marriages.view().byPlayer().containsKey(actor.id())) open(player,"partner_info","",0);
                else command.accept(player,new String[]{"info"});
            }
            case "task","tp","cancel" -> command.accept(player,new String[]{action.key()});
            case "gifts" -> open(player,"gift","",0);
            case "set-wedding-location" -> command.accept(player,new String[]{"setweddingloc"});
            case "start-wedding" -> command.accept(player,new String[]{"startw"});
            case "select" -> select(player,holder,action.value(),alternate);
            default -> config.warn("忽略未注册的GUI动作："+action.key());
        }
    }
    private void select(Player player,XiyuanHolder holder,String value,boolean alternate){
        if(value.isBlank())return;
        if(holder.page().equals("gift")){
            player.closeInventory();command.accept(player,new String[]{"claim",value});return;
        }
        if(holder.page().equals("invitation")){
            var actor=marriages.directory().capture(player);
            if(actor!=null){player.closeInventory();wedding.respond(actor,value,!alternate);}return;
        }
        if(holder.page().equals("task")){open(player,"task","",holder.index());return;}
        if(holder.page().equals("partner_info")){marriages.info(player,UUID.fromString(value));return;}
        if(holder.page().equals("rank")){
            if(holder.mode().isBlank())open(player,"rank",value,0);
            else marriages.info(player,UUID.fromString(value));
            return;
        }
        if(holder.page().equals("propose")||holder.page().equals("send_invite")){
            var target=marriages.directory().live(UUID.fromString(value));
            if(target==null){messages.send(player,"offline");return;}
            var actor=marriages.directory().capture(player);
            if(holder.page().equals("propose"))marriages.propose(actor,target,holder.mode());else wedding.invite(actor,target);
        }
    }
}
