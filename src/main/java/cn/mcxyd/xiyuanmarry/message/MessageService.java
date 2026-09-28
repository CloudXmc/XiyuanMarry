package cn.mcxyd.xiyuanmarry.message;
import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;import net.kyori.adventure.text.Component;import org.bukkit.command.CommandSender;import java.util.*;
public final class MessageService {
 private final ConfigurationManager config;private final TextRenderer text=new TextRenderer();public MessageService(ConfigurationManager config){this.config=config;}
 public List<String> list(String key){return config.messages().getStringList(key);}
 public TextRenderer renderer(){return text;}public String raw(String key){return config.messages().getString(key,"");}public Component parse(String value){return text.parse(value);}
 public Component format(String key,Object...values){return text.parse(raw("prefix")).append(text.format(raw(key),values));}
 public List<Component> lines(String key){return config.messages().getStringList(key).stream().map(l->text.parse(raw("prefix")).append(text.parse(l))).toList();}
 public void send(CommandSender sender,String key,Object...values){sender.sendMessage(format(key,values));}public void send(CommandSender sender,Component c){sender.sendMessage(c);}
}



