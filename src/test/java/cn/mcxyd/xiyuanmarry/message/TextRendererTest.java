package cn.mcxyd.xiyuanmarry.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TextRendererTest {
    private final TextRenderer text=new TextRenderer();
    private String plain(Component c){return PlainTextComponentSerializer.plainText().serialize(c);}
    @Test void gradientInterpolatesFullTaskNameBeforeColouring(){
        var c=text.gui(text.format("<gradient:#F5ADCD:#B5A4F0>{entry.name}</gradient>","entry.name","第1轮 · 第1天 · 共同击杀怪物"));
        assertEquals("第1轮 · 第1天 · 共同击杀怪物",plain(c));
        var colours=new HashSet<TextColor>();collect(c,null,colours);assertTrue(colours.size()>2);
        notItalic(c);
    }
    @Test void replacementNeverParsesPlayerTextOrExpandsAnotherPlaceholder(){
        String hostile="<click:run_command:'/op nobody'>§c&l{second}</click>";
        for(String template:List.of("<gradient:#F5ADCD:#B5A4F0>{first}</gradient> {second}","&a{first} {second}","§e{first} {second}")){
            var c=text.format(template,"first",hostile,"second","正常");
            assertEquals(hostile+" 正常",plain(c));noEvents(c);
        }
    }
    @Test void repeatedUnknownAndLiteralTokensRemainPredictable(){
        assertEquals("任务/任务/{missing}",plain(text.format("<gradient:#F5ADCD:#B5A4F0>{entry.name}/{entry.name}/{missing}</gradient>","entry.name","任务")));
    }
    private void collect(Component c,TextColor inherited,Set<TextColor> out){
        var colour=c.color()==null?inherited:c.color();
        if(c instanceof TextComponent t&&!t.content().isEmpty()&&colour!=null)out.add(colour);
        c.children().forEach(child->collect(child,colour,out));
    }
    private void noEvents(Component c){assertNull(c.clickEvent());assertNull(c.hoverEvent());c.children().forEach(this::noEvents);}
    private void notItalic(Component c){assertEquals(TextDecoration.State.FALSE,c.decoration(TextDecoration.ITALIC));c.children().forEach(this::notItalic);}
}
