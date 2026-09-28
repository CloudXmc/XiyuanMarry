package cn.mcxyd.xiyuanmarry.command;
import org.bukkit.command.CommandSender;import java.util.*;
public interface Subcommand {String name();List<String> aliases();String permission();boolean playerOnly();void execute(CommandSender sender,String label,String[] args);List<String> complete(CommandSender sender,String[] args);default boolean visibleInHelp(){return true;}}

