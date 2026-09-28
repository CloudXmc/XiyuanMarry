package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.gui.GuiFactory;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Locale;
import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;

/** 主菜单也参加权限过滤、帮助与补全，避免特殊分支漏掉入口。 */
public final class MenuSubcommand implements Subcommand {
    private final GuiFactory gui;
    private final MessageService messages;
    public MenuSubcommand(GuiFactory gui,MessageService messages){this.gui=gui;this.messages=messages;}
    public String name(){return "menu";}
    public List<String> aliases(){return List.of();}
    public String permission(){return "marry.use";}
    public boolean playerOnly(){return true;}
    public List<String> complete(CommandSender sender,String[] args){
        if(args.length==1)return java.util.stream.Stream.concat(ConfigurationManager.GUI_NAMES.stream().map(name->name.equals("main_menu")?"main":name),java.util.stream.Stream.of("normal")).toList();
        if(args.length==2)return gui.searchCandidates(args[0]);
        return List.of();
    }
    public void execute(CommandSender sender,String label,String[] args){
        if(args.length>2){messages.send(sender,"invalid-argument");return;}
        String page=args.length==0?"main_menu":args[0].toLowerCase(Locale.ROOT);
        boolean normal=page.equals("normal");if(normal)page="propose";
        if(page.equals("main"))page="main_menu";
        if(!ConfigurationManager.GUI_NAMES.contains(page)){messages.send(sender,"invalid-argument");return;}
        String mode=page.equals("propose")?(normal?"NORMAL":"WEDDING"):"";
        if(args.length==2)gui.openFiltered((Player)sender,page,mode,args[1]);
        else gui.open((Player)sender,page,mode,0);
    }
}
