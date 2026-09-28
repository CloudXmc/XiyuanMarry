package cn.mcxyd.xiyuanmarry.command;
import cn.mcxyd.xiyuanmarry.gui.GuiFactory;import cn.mcxyd.xiyuanmarry.message.MessageService;import cn.mcxyd.xiyuanmarry.model.*;import cn.mcxyd.xiyuanmarry.service.*;import org.bukkit.command.*;import org.bukkit.entity.Player;import java.util.*;
public final class MarriageSubcommands {
 private static abstract class PlayerCommand implements Subcommand {final String name,perm;final MarriageService m;final PlayerDirectory d;final MessageService msg;PlayerCommand(String n,String p,MarriageService m,PlayerDirectory d,MessageService msg){name=n;perm=p;this.m=m;this.d=d;this.msg=msg;}public String name(){return name;}public List<String> aliases(){return List.of();}public String permission(){return perm;}public boolean playerOnly(){return true;}public List<String> complete(CommandSender s,String[] a){return List.of();}PlayerSnapshot p(CommandSender s){return d.capture((Player)s);}}
 public static void register(MarryCommand root,MarriageService m,WeddingService w,PlayerDirectory d,MessageService msg,GuiFactory gui,GiftService gifts,RingService ring,RewardService rewards){
  root.add(new PlayerCommand("propose","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){PlayerSnapshot p=p(s);if(a.length==0){gui.open((Player)s,"propose","WEDDING",0);return;}var t=d.name(a[0]);if(t==null){msg.send(s,"offline");return;}m.propose(p,t,"WEDDING");}public List<String> complete(CommandSender s,String[] a){return d.all().stream().map(PlayerSnapshot::name).filter(n->a.length==0||n.startsWith(a[0])).toList();}});
  root.add(new PlayerCommand("normal","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){PlayerSnapshot p=p(s);if(a.length==0){gui.open((Player)s,"propose","NORMAL",0);return;}var t=d.name(a[0]);if(t==null){msg.send(s,"offline");return;}m.propose(p,t,"NORMAL");}public List<String> complete(CommandSender s,String[] a){return d.all().stream().map(PlayerSnapshot::name).toList();}});
  root.add(new PlayerCommand("wedding","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){PlayerSnapshot p=p(s);if(a.length==0){gui.open((Player)s,"propose","WEDDING",0);return;}var t=d.name(a[0]);if(t==null){msg.send(s,"offline");return;}m.propose(p,t,"WEDDING");}public List<String> complete(CommandSender s,String[] a){return d.all().stream().map(PlayerSnapshot::name).toList();}});
  root.add(new AcceptSubcommand(m,w,d));
  root.add(new DenySubcommand(m,w,d));
  root.add(new PlayerCommand("info","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){m.info(s,p(s).id());}});
  root.add(new PlayerCommand("partner","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){m.info(s,p(s).id());}});
  root.add(new PlayerCommand("cancel","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){m.cancel(p(s));}});
  root.add(new PlayerCommand("divorce","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){m.divorce(p(s),false);}});
  root.add(new PlayerCommand("withdraw","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){m.divorce(p(s),true);}});
  root.add(new PlayerCommand("block","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(a.length<1){msg.send(s,"invalid-argument");return;}UUID id=m.identityByName(a[0]);if(id==null){msg.send(s,"offline");return;}m.block(p(s),id,true);}});
  root.add(new PlayerCommand("unblock","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(a.length<1){msg.send(s,"invalid-argument");return;}UUID id=m.identityByName(a[0]);if(id==null){msg.send(s,"offline");return;}m.block(p(s),id,false);}});
  root.add(new PlayerCommand("setweddingloc","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){w.setPoint(p(s),"location");}});
  root.add(new PlayerCommand("hunliset","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(a.length<1){msg.send(s,"invalid-argument");return;}w.setPoint(p(s),a[0]);}public List<String> complete(CommandSender s,String[] a){return List.of("nx","nl","ly","1","2","3");}});
  root.add(new PlayerCommand("invite","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(a.length<1){msg.send(s,"invalid-argument");return;}var t=d.name(a[0]);if(t==null){msg.send(s,"offline");return;}w.invite(p(s),t);}});
  root.add(new PlayerCommand("startw","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){w.start(p(s));}});
  root.add(new PlayerCommand("oath","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){w.oath(p(s));}});
  root.add(new InvitationResponseSubcommand(true,w,d,msg));
  root.add(new InvitationResponseSubcommand(false,w,d,msg));
  root.add(new PlayerCommand("chat","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(a.length==0){msg.send(s,"invalid-argument");return;}m.partnerChat(p(s),String.join(" ",a));}});
  root.add(new PlayerCommand("gift","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(a.length!=0){msg.send(s,"invalid-argument");return;}gifts.send(p(s));}});
  root.add(new PlayerCommand("weddinggift","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(a.length!=1){msg.send(s,"invalid-argument");return;}UUID id=m.identityByName(a[0]);if(id==null){msg.send(s,"offline");return;}gifts.weddingGift(p(s),id);}public List<String> complete(CommandSender s,String[] a){return a.length==1?d.all().stream().map(PlayerSnapshot::name).filter(n->n.startsWith(a[0])).toList():List.of();}});
  root.add(new TaskSubcommand(gui,msg));
  root.add(new RankSubcommand(gui,msg));
  root.add(new PlayerCommand("ring","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){ring.toggle(p(s));}});
 }
}

