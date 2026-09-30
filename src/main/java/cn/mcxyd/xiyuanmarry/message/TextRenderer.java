package cn.mcxyd.xiyuanmarry.message;

import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

public final class TextRenderer {
    private static final MiniMessage MINI=MiniMessage.miniMessage();
    private static final Pattern TOKEN=Pattern.compile("\\{([^{}]+)}");

    public Component parse(String source){
        if(source==null)return Component.empty();
        return source.indexOf('<')>=0?MINI.deserialize(source):source.indexOf('§')>=0
                ?LegacyComponentSerializer.legacySection().deserialize(source)
                :LegacyComponentSerializer.legacyAmpersand().deserialize(source);
    }

    public Component format(String template,Object...values){
        if(template==null)return Component.empty();
        var replacements=new LinkedHashMap<String,String>();
        for(int i=0;i+1<values.length;i+=2)replacements.putIfAbsent(String.valueOf(values[i]),String.valueOf(values[i+1]));
        if(replacements.isEmpty())return parse(template);
        if(template.indexOf('<')>=0){
            var resolver=TagResolver.builder();var matcher=TOKEN.matcher(template);var source=new StringBuilder();int end=0,index=0;
            while(matcher.find()){
                String value=replacements.get(matcher.group(1));if(value==null)continue;
                String tag="xiyuan_arg_"+index++;
                source.append(template,end,matcher.start()).append('<').append(tag).append('>');end=matcher.end();
                // 渐变会逐字拆分 Component；先插入纯文本，再由 MiniMessage 着色，不能解析玩家输入。
                resolver.resolver(Placeholder.unparsed(tag,value));
            }
            return MINI.deserialize(source.append(template,end,template.length()).toString(),resolver.build());
        }
        // 传统颜色仍由 Adventure 解析；一次替换避免玩家文字中的占位符被再次展开。
        return parse(template).replaceText(TextReplacementConfig.builder().match(TOKEN)
                .replacement((match,builder)->Component.text(replacements.getOrDefault(match.group(1),match.group()))).build());
    }

    public Component gui(Component component){
        return component.decoration(TextDecoration.ITALIC,false).children(component.children().stream().map(this::gui).toList());
    }
}

