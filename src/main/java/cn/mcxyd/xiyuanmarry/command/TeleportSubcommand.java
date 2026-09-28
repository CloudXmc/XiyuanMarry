package cn.mcxyd.xiyuanmarry.command;
import cn.mcxyd.xiyuanmarry.service.*;import cn.mcxyd.xiyuanmarry.message.MessageService;
import org.bukkit.command.CommandSender;import org.bukkit.entity.Player;import java.util.*;
public final class TeleportSubcommand implements Subcommand {
    private final PartnerTeleportService service;private final PlayerDirectory players;private final MessageService messages;
    public TeleportSubcommand(PartnerTeleportService s,PlayerDirectory p,MessageService m){service=s;players=p;messages=m;}
    public String name(){return "tp";}public List<String> aliases(){return List.of();}public String permission(){return "marry.use";}public boolean playerOnly(){return true;}
    public void execute(CommandSender sender,String label,String[] args){if(args.length!=0){messages.send(sender,"invalid-argument");return;}service.teleport(players.capture((Player)sender));}
    public List<String> complete(CommandSender sender,String[] args){return List.of();}
}
