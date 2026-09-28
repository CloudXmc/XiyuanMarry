package cn.mcxyd.xiyuanmarry.command;
import cn.mcxyd.xiyuanmarry.gui.GuiFactory;import cn.mcxyd.xiyuanmarry.message.MessageService;import cn.mcxyd.xiyuanmarry.service.RankingService;
import org.bukkit.command.CommandSender;import org.bukkit.entity.Player;import java.util.*;
public final class RankSubcommand implements Subcommand {
    private final GuiFactory gui;private final MessageService messages;
    public RankSubcommand(GuiFactory gui,MessageService messages){this.gui=gui;this.messages=messages;}
    public String name(){return "rank";}public List<String> aliases(){return List.of();}
    public String permission(){return "marry.use";}public boolean playerOnly(){return true;}
    public void execute(CommandSender sender,String label,String[] args){
        String board=args.length==0?"":args[0].toLowerCase(Locale.ROOT);
        if(args.length>1||!board.isEmpty()&&!RankingService.BOARDS.contains(board)){messages.send(sender,"invalid-argument");return;}
        gui.open((Player)sender,"rank",board,0);
    }
    public List<String> complete(CommandSender sender,String[] args){return args.length==1?RankingService.BOARDS.stream().filter(b->b.startsWith(args[0].toLowerCase(Locale.ROOT))).toList():List.of();}
}
