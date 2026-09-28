package cn.mcxyd.xiyuanmarry.command;
import java.util.*;import java.util.function.Predicate;
public final class CommandAccess {
    private CommandAccess(){}
    public static String rejection(Subcommand command,boolean player,Predicate<String> permission){
        if(!permission.test(command.permission()))return "no-permission";
        if(command.playerOnly()&&!player)return "player-only";return null;
    }
    public static List<String> visibleNames(Collection<Subcommand> commands,boolean player,Predicate<String> permission,String prefix){
        return commands.stream().filter(Subcommand::visibleInHelp).filter(c->rejection(c,player,permission)==null)
            .map(Subcommand::name).distinct().filter(n->n.startsWith(prefix.toLowerCase(Locale.ROOT))).sorted().toList();
    }
}
