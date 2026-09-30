package cn.mcxyd.xiyuanmarry.command;
import cn.mcxyd.xiyuanmarry.service.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;
public final class AcceptSubcommand implements Subcommand {
    private final MarriageService marriages;
    private final WeddingService weddings;
    private final PlayerDirectory directory;
    public AcceptSubcommand(MarriageService marriages, WeddingService weddings, PlayerDirectory directory) {
        this.marriages=marriages;this.weddings=weddings;this.directory=directory;
    }
    public String name(){return "accept";}
    public List<String> aliases(){return List.of();}
    public String permission(){return "marry.use";}
    public boolean playerOnly(){return true;}
    public void execute(CommandSender sender,String label,String[] args){
        var target=RequestTarget.parse(args);
        var player=directory.capture((Player)sender);
        RuleViolation.require(player!=null,"database-not-ready");
        if(target==RequestTarget.INVITATION)weddings.respond(player,true);else marriages.accept(player);
    }
    public List<String> complete(CommandSender sender,String[] args){return args.length==1?List.of("proposal","invitation"):List.of();}
}
