package cn.mcxyd.xiyuanmarry.command;
import cn.mcxyd.xiyuanmarry.gui.GuiFactory;import cn.mcxyd.xiyuanmarry.message.MessageService;import cn.mcxyd.xiyuanmarry.service.*;
import org.bukkit.command.*;import org.bukkit.entity.Player;import java.util.*;
public final class MarryCommand implements CommandExecutor {
    private final Map<String,Subcommand> commands=new LinkedHashMap<>();private final MessageService messages;
    public MarryCommand(MessageService m,PlayerDirectory d,MarriageService ms,WeddingService ws,GuiFactory g){messages=m;add(new MenuSubcommand(g,m));add(new HelpSubcommand(m,false,()->commands.values()));}
    public void add(Subcommand c){commands.put(c.name(),c);c.aliases().forEach(a->commands.put(a,c));}
    public Map<String,Subcommand> commands(){return Map.copyOf(commands);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        String key=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
        var sub=commands.get(key);if(sub==null){messages.send(sender,"unknown");return true;}
        String rejection=CommandAccess.rejection(sub,sender instanceof Player,sender::hasPermission);
        if(rejection!=null){messages.send(sender,rejection);return true;}
        try{sub.execute(sender,label,args.length==0?new String[0]:Arrays.copyOfRange(args,1,args.length));}
        catch(RuleViolation rule){messages.send(sender,rule.key());}
        catch(IllegalArgumentException invalid){messages.send(sender,"invalid-argument");}
        return true;
    }
}
