package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.service.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.*;

/** 保留无参数行为；指定婚礼 UUID 时只处理选中的请帖。 */
public final class InvitationResponseSubcommand implements Subcommand {
    private final boolean accept;
    private final WeddingService weddings;
    private final PlayerDirectory players;
    private final MessageService messages;
    public InvitationResponseSubcommand(boolean accept,WeddingService weddings,PlayerDirectory players,MessageService messages){
        this.accept=accept;this.weddings=weddings;this.players=players;this.messages=messages;
    }
    public String name(){return accept?"acceptinvitation":"denyinvitation";}
    public List<String> aliases(){return List.of();}
    public String permission(){return "marry.use";}
    public boolean playerOnly(){return true;}
    public List<String> complete(CommandSender sender,String[] args){return List.of();}
    public void execute(CommandSender sender,String label,String[] args){
        if(args.length>1){messages.send(sender,"invalid-argument");return;}
        String selected=null;
        if(args.length==1){
            try { selected=UUID.fromString(args[0]).toString(); }
            catch(IllegalArgumentException invalid){messages.send(sender,"invalid-argument");return;}
        }
        var actor=players.capture((Player)sender);
        if(selected==null)weddings.respond(actor,accept);else weddings.respond(actor,selected,accept);
    }
}
