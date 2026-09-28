package cn.mcxyd.xiyuanmarry.message;
import net.kyori.adventure.text.*;import net.kyori.adventure.text.format.TextDecoration;import net.kyori.adventure.text.minimessage.MiniMessage;import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
public final class TextRenderer {
 private static final MiniMessage MINI=MiniMessage.miniMessage();
 public Component parse(String source){if(source==null)return Component.empty();return source.indexOf('<')>=0?MINI.deserialize(source):source.indexOf('§')>=0?LegacyComponentSerializer.legacySection().deserialize(source):LegacyComponentSerializer.legacyAmpersand().deserialize(source);}
 public Component format(String template,Object...values){Component out=parse(template);for(int i=0;i+1<values.length;i+=2)out=out.replaceText(TextReplacementConfig.builder().matchLiteral("{"+values[i]+"}").replacement(Component.text(String.valueOf(values[i+1]))).build());return out;}
 public Component gui(Component component){return component.decoration(TextDecoration.ITALIC,false).children(component.children().stream().map(this::gui).toList());}
}

