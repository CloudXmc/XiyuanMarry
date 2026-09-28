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
    private java.util.function.BiConsumer<Player,String[]> command=(p,a)->{};
    private volatile boolean closed;
    public GuiFactory(ConfigurationManager c,MarriageService m,WeddingService w,MessageService msg,UnifiedScheduler s,DailyTaskService tasks){
        config=c;marriages=m;wedding=w;messages=msg;scheduler=s;this.tasks=tasks;
        icons=new IconFactory(msg.renderer());content=new MenuContentProvider(c,m,msg);
    }
    public void commands(java.util.function.BiConsumer<Player,String[]> handler){command=handler;}
    public void cancel(UUID player){requests.cancel(player);}
    public void reload(){requests.clear();}
    @Override public void close(){closed=true;requests.clear();}
    public void open(Player player,String page,String mode,int index){
        if(!player.hasPermission("marry.use")){messages.send(player,"no-permission");return;}
        if(closed)return;requests.cancel(player.getUniqueId());
        var actor=marriages.directory().capture(player);var snapshot=config.snapshot();
        if(page.equals("task")){
            UUID token=requests.begin(actor.liveId(),System.currentTimeMillis());
            if(token==null){messages.send(player,"busy");return;}
            messages.send(player,"task-loading");
            tasks.load(actor,task->scheduler.player(actor.liveId(),owner->{
                if(closed||!snapshot.generation().equals(config.snapshot().generation())
                    ||!requests.consume(actor.liveId(),token,System.currentTimeMillis()))return;
                var current=marriages.view().byPlayer().get(actor.id());
                if(current==null||!current.id().equals(task.coupleId())||!current.married())return;
                render(owner,page,mode,index,content.tasks(task));
            }));
            return;
        }
        render(player,page,mode,index,content.entries(actor,page,mode));
    }
    private void render(Player player,String page,String mode,int index,List<MenuContentProvider.Entry> entries){
        var snapshot=config.snapshot();var layout=snapshot.menus().get(page);
        if(layout==null){messages.send(player,"invalid-argument");return;}
        var slots=layout.dynamicSlots();int capacity=Math.max(1,slots.size());
        int last=Math.max(0,(entries.size()-1)/capacity);int number=Math.max(0,Math.min(index,last));
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
            actions.put(slot,new XiyuanHolder.Action(template.action(),entry.value()));
        }
        var holder=new XiyuanHolder(player.getUniqueId(),snapshot.generation(),page,mode,number,actions);
        var inventory=Bukkit.createInventory(holder,layout.size(),messages.renderer().gui(messages.parse(layout.title())));
        holder.attach(inventory);items.forEach(inventory::setItem);player.openInventory(inventory);
        if(entries.isEmpty()&&!slots.isEmpty())messages.send(player,"empty");
    }
    public void activate(Player player,XiyuanHolder holder,XiyuanHolder.Action action){
        if(!player.hasPermission("marry.use")){messages.send(player,"no-permission");return;}
        if(!holder.generation().equals(config.snapshot().generation())){open(player,holder.page(),holder.mode(),holder.index());return;}
        switch(action.key()){
            case "normal-marriage" -> open(player,"propose","NORMAL",0);
            case "wedding-marriage" -> open(player,"propose","WEDDING",0);
            case "wedding-plan" -> open(player,"wedding_plan","",0);
            case "invitations" -> open(player,"send_invite","",0);
            case "rank" -> open(player,"rank","",0);
            case "previous-page" -> open(player,holder.page(),holder.mode(),holder.index()-1);
            case "next-page" -> open(player,holder.page(),holder.mode(),holder.index()+1);
            case "back" -> {
                if(holder.page().equals("main_menu")){
                    player.closeInventory();String back=action.value().strip();
                    if(back.startsWith("/"))back=back.substring(1);
                    if(!back.isBlank()&&back.length()<=128&&back.chars().noneMatch(Character::isISOControl))player.performCommand(back);
                }else if(holder.page().equals("rank")&&!holder.mode().isBlank())open(player,"rank","",0);
                else open(player,"main_menu","",0);
            }
            case "info","task","tp","cancel" -> command.accept(player,new String[]{action.key()});
            case "gifts" -> command.accept(player,new String[]{"claim"});
            case "set-wedding-location" -> command.accept(player,new String[]{"setweddingloc"});
            case "start-wedding" -> command.accept(player,new String[]{"startw"});
            case "select" -> select(player,holder,action.value());
            default -> config.warn("忽略未注册的GUI动作："+action.key());
        }
    }
    private void select(Player player,XiyuanHolder holder,String value){
        if(holder.page().equals("task")){open(player,"task","",holder.index());return;}
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
