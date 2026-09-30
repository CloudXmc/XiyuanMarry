package cn.mcxyd.xiyuanmarry.gui;

import cn.mcxyd.xiyuanmarry.config.*;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.DailyTask;
import cn.mcxyd.xiyuanmarry.service.MarriageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskIconRenderingTest {
    @Test void allThirtyTaskNamesReachItemMetaWithNoLiteralTokensOrItalics(){
        var config=mock(ConfigurationManager.class);
        var messages=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile());
        var taskYaml=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/tasks.yml").toFile());
        var catalogue=TaskCatalog.parse(taskYaml,20);
        when(config.messages()).thenReturn(messages);
        when(config.snapshot()).thenReturn(new ConfigurationManager.Snapshot(UUID.randomUUID(),Map.of(),Map.of(),null,catalogue));
        var service=new MessageService(config);
        var provider=new MenuContentProvider(config,mock(MarriageService.class),service);
        var entries=provider.tasks(new DailyTask("couple",0,catalogue.schedule().getFirst(),0,false));
        var layout=GuiLayout.parse(YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui/task.yml").toFile()));
        assertEquals(30,entries.size());assertTrue(layout.dynamicSlots().size()>=entries.size());
        var meta=mock(ItemMeta.class);var icons=new IconFactory(service.renderer());
        try(var construction=mockConstruction(ItemStack.class,(item,context)->when(item.getItemMeta()).thenReturn(meta))){
            entries.forEach(entry->icons.create(layout.icons().get('D'),entry.placeholders()));
            assertEquals(30,construction.constructed().size());
        }
        var names=ArgumentCaptor.forClass(Component.class);verify(meta,times(30)).displayName(names.capture());
        for(int day=0;day<30;day++){
            var name=names.getAllValues().get(day);
            assertEquals("第1轮 · 第"+(day+1)+"天 · "+catalogue.schedule().get(day).name(),PlainTextComponentSerializer.plainText().serialize(name));
            notItalic(name);
        }
    }
    private void notItalic(Component c){assertEquals(TextDecoration.State.FALSE,c.decoration(TextDecoration.ITALIC));c.children().forEach(this::notItalic);}
}
