package cn.mcxyd.xiyuanmarry.command;
import org.bukkit.command.*;import org.bukkit.entity.Player;import java.util.*;
public final class SubcommandCompleter implements TabCompleter {
    private final Map<String,Subcommand> commands;
    public SubcommandCompleter(Map<String,Subcommand> commands){this.commands=Map.copyOf(commands);}
    public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args){
        if(args.length==0)return List.of();
        if(args.length==1)return CommandAccess.visibleNames(commands.values(),sender instanceof Player,sender::hasPermission,args[0]);
        var sub=commands.get(args[0].toLowerCase(Locale.ROOT));
        if(sub==null||CommandAccess.rejection(sub,sender instanceof Player,sender::hasPermission)!=null)return List.of();
        return sub.complete(sender,Arrays.copyOfRange(args,1,args.length)).stream()
            .filter(s->s.toLowerCase(Locale.ROOT).startsWith(args[args.length-1].toLowerCase(Locale.ROOT))).toList();
    }
}
