package cn.mcxyd.xiyuanmarry.command;
import cn.mcxyd.xiyuanmarry.message.MessageService;import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;import cn.mcxyd.xiyuanmarry.service.PlayerDirectory;import org.bukkit.command.*;import org.bukkit.entity.Player;import java.util.*;
public final class CommandContext {
 public final CommandSender sender;public final MessageService messages;public final PlayerDirectory directory;
 public CommandContext(CommandSender s,MessageService m,PlayerDirectory d){sender=s;messages=m;directory=d;}
 public Player player(){return (Player)sender;}public PlayerSnapshot snapshot(){return directory.capture(player());}public boolean permission(String node){return sender.hasPermission(node);}
 public UUID target(String name){var p=directory.name(name);return p==null?null:p.id();}
}

