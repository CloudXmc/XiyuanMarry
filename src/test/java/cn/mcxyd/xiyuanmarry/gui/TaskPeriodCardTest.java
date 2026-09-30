package cn.mcxyd.xiyuanmarry.gui;

import cn.mcxyd.xiyuanmarry.config.*;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.service.MarriageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskPeriodCardTest {
    @Test void taskMenuReservesOneFixedPeriodCardAndKeepsThirtyTaskEntries(){
        var y=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui/task.yml").toFile());
        var layout=GuiLayout.parse(y);
        assertTrue(layout.icons().containsKey('H'));
        assertEquals(35,layout.dynamicSlots().size());
        assertFalse(layout.dynamicSlots().contains(35));
        String slots=String.join("",layout.rows());
        assertEquals(35,slots.indexOf('H'));
        assertEquals(35,slots.lastIndexOf('H'));
        assertTrue(layout.dynamicSlots().size()>=30);
        assertEquals("task-period",layout.icons().get('H').action());
    }

    @Test void periodCardUsesGradientConfiguredNameAndLoreTokens(){
        var config=mock(ConfigurationManager.class);
        var messages=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile());
        when(config.messages()).thenReturn(messages);
        when(config.config()).thenReturn(YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile()));
        var provider=new MenuContentProvider(config,mock(MarriageService.class),new MessageService(config));
        var task=new DailyTask("couple",20,new TaskDefinition("x","KILL_MONSTER","共同击杀怪物","*",10,20),0,false);
        var tokens=provider.taskPeriodTokens(task,java.time.LocalDate.of(2026,9,29));
        var icon=GuiLayout.parse(YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui/task.yml").toFile())).icons().get('H');
        var meta=mock(ItemMeta.class);
        try(var construction=mockConstruction(ItemStack.class,(item,context)->when(item.getItemMeta()).thenReturn(meta))){
            new IconFactory(new MessageService(config).renderer()).create(icon,tokens);
        }
        var display=mockingDetails(meta).getInvocations().stream().filter(i->i.getMethod().getName().equals("displayName")&&i.getArguments().length==1).findFirst();
        assertTrue(display.isPresent());
        var displayName=(Component)display.get().getArguments()[0];
        assertEquals("2026年9月（第一轮）",PlainTextComponentSerializer.plainText().serialize(displayName));
        assertFalse(PlainTextComponentSerializer.plainText().serialize(displayName).contains("{"));
    }
}
