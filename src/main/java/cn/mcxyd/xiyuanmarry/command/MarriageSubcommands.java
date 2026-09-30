package cn.mcxyd.xiyuanmarry.command;
import cn.mcxyd.xiyuanmarry.gui.GuiFactory;import cn.mcxyd.xiyuanmarry.message.MessageService;import cn.mcxyd.xiyuanmarry.model.*;import cn.mcxyd.xiyuanmarry.service.*;import org.bukkit.command.*;import org.bukkit.entity.Player;import java.util.*;
public final class MarriageSubcommands {
 private static abstract class PlayerCommand implements Subcommand {final String name,perm;final MarriageService m;final PlayerDirectory d;final MessageService msg;PlayerCommand(String n,String p,MarriageService m,PlayerDirectory d,MessageService msg){name=n;perm=p;this.m=m;this.d=d;this.msg=msg;}public String name(){return name;}public List<String> aliases(){return List.of();}public String permission(){return perm;}public boolean playerOnly(){return true;}public List<String> complete(CommandSender s,String[] a){return List.of();}PlayerSnapshot p(CommandSender s){var actor=d.capture((Player)s);if(actor==null)msg.send(s,"database-not-ready");return actor;}boolean exact(CommandSender s,String[] a,int size){if(a.length!=size){msg.send(s,"invalid-argument");return false;}return true;}}
 public static void register(MarryCommand root,MarriageService m,WeddingService w,PlayerDirectory d,MessageService msg,GuiFactory gui,GiftService gifts,RingService ring,RewardService rewards){
  root.add(new ProposeSubcommand(m,d,msg,gui));
  root.add(new NormalMarriageSubcommand(m,d,msg,gui));
  root.add(new WeddingMarriageSubcommand(m,d,msg,gui));
  root.add(new AcceptSubcommand(m,w,d));
  root.add(new DenySubcommand(m,w,d));
  root.add(new InfoSubcommand(m,d));
  root.add(new PartnerSubcommand(m,d));
  root.add(new CancelSubcommand(m,d));
  root.add(new DivorceSubcommand(m,d));
  root.add(new WithdrawSubcommand(m,d));
  root.add(new PlayerCommand("block","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(!exact(s,a,1))return;var actor=p(s);if(actor==null)return;UUID id=m.identityByName(a[0]);if(id==null){msg.send(s,"offline");return;}m.block(actor,id,true);}});
  root.add(new PlayerCommand("unblock","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(!exact(s,a,1))return;var actor=p(s);if(actor==null)return;UUID id=m.identityByName(a[0]);if(id==null){msg.send(s,"offline");return;}m.block(actor,id,false);}});
  root.add(new PlayerCommand("setweddingloc","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(!exact(s,a,0))return;var actor=p(s);if(actor!=null)w.setPoint(actor,"location");}});
  root.add(new PlayerCommand("hunliset","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(!exact(s,a,1))return;var actor=p(s);if(actor!=null)w.setPoint(actor,a[0]);}public List<String> complete(CommandSender s,String[] a){return a.length==1?List.of("nx","nl","ly","1","2","3"):List.of();}});
  root.add(new PlayerCommand("invite","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(!exact(s,a,1))return;var actor=p(s);if(actor==null)return;var t=d.name(a[0]);if(t==null){msg.send(s,"offline");return;}w.invite(actor,t);}});
  root.add(new PlayerCommand("startw","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(!exact(s,a,0))return;var actor=p(s);if(actor!=null)w.start(actor);}});
  root.add(new PlayerCommand("oath","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(!exact(s,a,0))return;var actor=p(s);if(actor!=null)w.oath(actor);}});
  root.add(new InvitationResponseSubcommand(true,w,d,msg));
  root.add(new InvitationResponseSubcommand(false,w,d,msg));
  root.add(new PlayerCommand("chat","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(a.length==0){msg.send(s,"invalid-argument");return;}var actor=p(s);if(actor!=null)m.partnerChat(actor,String.join(" ",a));}});
  root.add(new PlayerCommand("gift","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(!exact(s,a,0))return;var actor=p(s);if(actor!=null)gifts.send(actor);}});
  root.add(new PlayerCommand("weddinggift","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(!exact(s,a,1))return;var actor=p(s);if(actor==null)return;UUID id=m.identityByName(a[0]);if(id==null){msg.send(s,"offline");return;}gifts.weddingGift(actor,id);}public List<String> complete(CommandSender s,String[] a){return a.length==1?d.all().stream().map(PlayerSnapshot::name).filter(n->n.toLowerCase(Locale.ROOT).startsWith(a[0].toLowerCase(Locale.ROOT))).toList():List.of();}});
  root.add(new TaskSubcommand(gui,msg));
  root.add(new RankSubcommand(gui,msg));
  root.add(new PlayerCommand("ring","marry.use",m,d,msg){public void execute(CommandSender s,String l,String[] a){if(!exact(s,a,0))return;var actor=p(s);if(actor!=null)ring.toggle(actor);}});
 }
}

