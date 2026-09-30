package cn.mcxyd.xiyuanmarry.gui;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.*;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import cn.mcxyd.xiyuanmarry.service.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskMenuOverflowTest {
    private void render(boolean legacy){
        var config=mock(ConfigurationManager.class);var player=mock(Player.class);var messages=mock(MessageService.class);
        var yaml=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui/task.yml").toFile());
        if(legacy)yaml.set("layout",List.of("#########","#DDDDDDD#","#DDDDDDD#","#DDDDDDD#","#########"));
        var layout=GuiLayout.parse(yaml);
        when(config.snapshot()).thenReturn(new ConfigurationManager.Snapshot(UUID.randomUUID(),Map.of(),Map.of("task",layout),null,null));
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(messages.renderer()).thenReturn(new TextRenderer());when(messages.parse(anyString())).thenReturn(Component.text("任务"));
        var inventory=mock(Inventory.class);var holders=new ArrayList<XiyuanHolder>();
        try(var bukkit=mockStatic(Bukkit.class);var icons=mockConstruction(IconFactory.class)){
            bukkit.when(()->Bukkit.createInventory(any(InventoryHolder.class),eq(45),any(Component.class))).thenAnswer(call->{holders.add(call.getArgument(0));return inventory;});
            try(var gui=new GuiFactory(config,mock(MarriageService.class),mock(WeddingService.class),messages,mock(UnifiedScheduler.class),mock(DailyTaskService.class))){
                var entries=IntStream.rangeClosed(1,30).mapToObj(day->new MenuContentProvider.Entry(Integer.toString(day),Map.of("name","任务"+day))).toList();
                gui.render(player,"task","",0,entries);
                verify(player).openInventory(inventory);assertEquals(1,holders.size());
                var holder=holders.getFirst();int visible=legacy?21:30;
                for(int i=0;i<visible;i++)assertEquals(Integer.toString(i+1),holder.action(layout.dynamicSlots().get(i)).value());
                if(legacy)verify(messages).send(player,"task-menu-overflow","shown",21,"total",30);
                else verify(messages,never()).send(eq(player),eq("task-menu-overflow"),any(Object[].class));
                verify(messages,never()).send(eq(player),eq("menu-overflow"),any(Object[].class));
            }
        }
    }
    @Test void undersizedCustomTaskMenuUsesTaskSpecificAdvice(){render(true);}
    @Test void defaultTaskMenuDoesNotWarnForThirtyTasks(){render(false);}
    @Test void taskCapacityMessageDoesNotSuggestUnsupportedSearchOrRewardClaims(){
        var messages=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile());
        String text=messages.getString("task-menu-overflow","");
        assertTrue(text.contains("{shown}"));assertTrue(text.contains("{total}"));
        assertFalse(text.contains("/marry claim"));assertFalse(text.contains("/marry menu"));assertTrue(text.contains("管理员"));
    }
}
