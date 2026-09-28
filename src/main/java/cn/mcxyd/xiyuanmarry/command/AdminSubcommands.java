package cn.mcxyd.xiyuanmarry.command;
import cn.mcxyd.xiyuanmarry.message.MessageService;import cn.mcxyd.xiyuanmarry.service.*;import org.bukkit.command.*;import java.util.*;
public final class AdminSubcommands {
 public static void register(MarryAdminCommand root,MarriageService m,PlayerDirectory d,MessageService msg,RingService ring){
  root.add(new Simple("reload","marry.admin.reload",false,(s,a)->m.reload(s instanceof org.bukkit.entity.Player p?p.getUniqueId():null),msg));
  root.add(new Simple("force","marry.admin.force",false,(s,a)->{if(a.length<2){msg.send(s,"invalid-argument");return;}var x=m.identityByName(a[0]);var y=m.identityByName(a[1]);if(x==null||y==null){msg.send(s,"offline");return;}m.admin(s instanceof org.bukkit.entity.Player p?p.getUniqueId():null,"force",x,y,0);},msg));
  root.add(new Simple("divorce","marry.admin.divorce",false,(s,a)->{if(a.length<1){msg.send(s,"invalid-argument");return;}var x=m.identityByName(a[0]);if(x==null){msg.send(s,"offline");return;}m.admin(null,"divorce",x,null,0);},msg));
  root.add(new Simple("setexp","marry.admin.set",false,(s,a)->set(m,s,a,"setexp"),msg));root.add(new Simple("setlevel","marry.admin.set",false,(s,a)->set(m,s,a,"setlevel"),msg));
  root.add(new Simple("list","marry.admin.list",false,(s,a)->m.view().couples().forEach(r->msg.send(s,"list-line","player1",m.name(r.playerOne()),"player2",m.name(r.playerTwo()),"state",r.state(),"bond",r.bond())),msg));
  root.add(new Simple("info","marry.admin.list",false,(s,a)->{if(a.length<1){msg.send(s,"invalid-argument");return;}var id=m.identityByName(a[0]);if(id==null)msg.send(s,"offline");else m.info(s,id);},msg));
  root.add(new Simple("clear","marry.admin.clear",false,(s,a)->{if(a.length<1){msg.send(s,"invalid-argument");return;}var id=m.identityByName(a[0]);if(id!=null)m.admin(null,"clear",id,null,0);},msg));
  root.add(new Simple("givering","marry.admin.give",false,(s,a)->{if(a.length<2){msg.send(s,"invalid-argument");return;}var id=m.identityByName(a[0]);if(id==null){msg.send(s,"offline");return;}ring.give(s,id,a[1]);},msg));
  root.add(new HelpSubcommand(msg,true,()->root.commands().values()));
 }
 private static void set(MarriageService m,CommandSender s,String[] a,String action){if(a.length<2){m.notifyLive(s instanceof org.bukkit.entity.Player p?p.getUniqueId():null,"invalid-argument");return;}UUID id=m.identityByName(a[0]);if(id==null){m.notifyLive(s instanceof org.bukkit.entity.Player p?p.getUniqueId():null,"offline");return;}try{long value=Long.parseLong(a[1]);if(value<0||action.equals("setlevel")&&(value<1||value>10))throw new IllegalArgumentException();m.admin(s instanceof org.bukkit.entity.Player p?p.getUniqueId():null,action,id,null,value);}catch(NumberFormatException e){m.notifyLive(s instanceof org.bukkit.entity.Player p?p.getUniqueId():null,"invalid-argument");}}
 private record Simple(String name,String permission,boolean playerOnly,java.util.function.BiConsumer<CommandSender,String[]> action,MessageService msg)implements Subcommand{public List<String> aliases(){return List.of();}public void execute(CommandSender s,String l,String[]a){action.accept(s,a);}public List<String> complete(CommandSender s,String[]a){return List.of();}}
}

