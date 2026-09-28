package cn.mcxyd.xiyuanmarry.command;
import cn.mcxyd.xiyuanmarry.message.MessageService;import org.bukkit.command.*;import org.bukkit.entity.Player;
import java.util.*;import java.util.function.Supplier;
public final class HelpSubcommand implements Subcommand {
    private final MessageService messages;private final boolean admin;private final Supplier<Collection<Subcommand>> commands;
    public HelpSubcommand(MessageService m,boolean admin,Supplier<Collection<Subcommand>> commands){messages=m;this.admin=admin;this.commands=commands;}
    public String name(){return "help";}public List<String> aliases(){return List.of();}public String permission(){return admin?"marry.admin":"marry.use";}public boolean playerOnly(){return false;}
    public void execute(CommandSender sender,String label,String[] args){
        messages.send(sender,admin?"admin-help-title":"help-title");
        for(String name:CommandAccess.visibleNames(commands.get(),sender instanceof Player,sender::hasPermission,"")){
            String key=(admin?"admin-help-":"help-")+name;
            if(!messages.raw(key).isBlank())messages.send(sender,key);
        }
    }
    public List<String> complete(CommandSender sender,String[] args){return List.of();}
}
