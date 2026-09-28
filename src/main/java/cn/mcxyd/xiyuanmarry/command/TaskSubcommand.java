package cn.mcxyd.xiyuanmarry.command;
import cn.mcxyd.xiyuanmarry.gui.GuiFactory;import cn.mcxyd.xiyuanmarry.message.MessageService;
import org.bukkit.command.CommandSender;import org.bukkit.entity.Player;import java.util.*;
public final class TaskSubcommand implements Subcommand {
    private final GuiFactory gui;private final MessageService messages;
    public TaskSubcommand(GuiFactory gui,MessageService messages){this.gui=gui;this.messages=messages;}
    public String name(){return "task";}public List<String> aliases(){return List.of();}
    public String permission(){return "marry.use";}public boolean playerOnly(){return true;}
    public void execute(CommandSender sender,String label,String[] args){if(args.length>0){messages.send(sender,"invalid-argument");return;}gui.open((Player)sender,"task","",0);}
    public List<String> complete(CommandSender sender,String[] args){return List.of();}
}
