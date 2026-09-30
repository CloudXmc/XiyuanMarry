package cn.mcxyd.xiyuanmarry.gui;

import cn.mcxyd.xiyuanmarry.config.*;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import cn.mcxyd.xiyuanmarry.service.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskGuiReadFlowTest {
    final ConfigurationManager config=mock(ConfigurationManager.class);
    final MarriageService marriages=mock(MarriageService.class);
    final PlayerDirectory directory=mock(PlayerDirectory.class);
    final DailyTaskService tasks=mock(DailyTaskService.class);
    final UnifiedScheduler scheduler=mock(UnifiedScheduler.class);
    final Player player=mock(Player.class);final Inventory inventory=mock(Inventory.class);
    final UUID live=UUID.randomUUID(),generation=UUID.randomUUID(),database=UUID.randomUUID();
    final PlayerSnapshot actor=new PlayerSnapshot(live,live,"name:alice","Alice",60,null,1);
    final Queue<Runnable> owners=new ArrayDeque<>();
    final List<Consumer<DailyTask>> replies=new ArrayList<>();
    final List<Consumer<Throwable>> failures=new ArrayList<>();
    final DailyTask task=new DailyTask("couple",0,new TaskDefinition("kill","KILL_MONSTER","共同击杀怪物","*",10,20),3,false);
    MessageService messages;GuiFactory gui;MockedStatic<Bukkit> bukkit;MockedConstruction<IconFactory> icons;
    @BeforeEach void setup(){
        var main=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
        var yaml=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui/task.yml").toFile());
        when(config.snapshot()).thenReturn(new ConfigurationManager.Snapshot(generation,Map.of("config.yml",main),Map.of("task",GuiLayout.parse(yaml)),null,new TaskCatalog(50,Collections.nCopies(30,task.definition()),List.of())));
        when(config.config()).thenReturn(main);when(config.messages()).thenReturn(YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile()));
        when(marriages.directory()).thenReturn(directory);when(marriages.databaseGeneration()).thenReturn(database);
        when(directory.capture(player)).thenReturn(actor);when(directory.live(live)).thenReturn(actor);
        when(player.getUniqueId()).thenReturn(live);when(player.hasPermission("marry.use")).thenReturn(true);
        var marriage=new MarriageRecord(live,UUID.randomUUID(),MarriageState.MARRIED,"NORMAL",1,0,0,"couple",1,0,0);
        when(marriages.view()).thenReturn(new MarriageService.View(Map.of(live,marriage),Map.of(),List.of(marriage),Map.of()));
        doAnswer(call->{Consumer<Player> done=call.getArgument(1);owners.add(()->done.accept(player));return null;}).when(scheduler).player(eq(live),any());
        doAnswer(call->{Consumer<Player> done=call.getArgument(1);owners.add(()->done.accept(player));return null;}).when(scheduler).player(eq(live),any(),any());
        doAnswer(call->{replies.add(call.getArgument(1));failures.add(call.getArgument(2));return null;}).when(tasks).load(eq(actor),any(),any());
        bukkit=mockStatic(Bukkit.class);icons=mockConstruction(IconFactory.class);
        bukkit.when(()->Bukkit.createInventory(any(InventoryHolder.class),eq(45),any(Component.class))).thenReturn(inventory);
        messages=spy(new MessageService(config));gui=new GuiFactory(config,marriages,mock(WeddingService.class),messages,scheduler,tasks);
    }
    @AfterEach void cleanup(){gui.close();icons.close();bukkit.close();}
    void open(){gui.open(player,"task","",0);}
    void finish(){replies.getFirst().accept(task);owners.remove().run();}
    void assertNoResult(){verify(player,never()).openInventory(any(Inventory.class));verify(messages,never()).send(eq(player),eq("task-loaded"),any(Object[].class));}
    @Test void currentRequestShowsProgressAndOpensTaskMenu(){
        open();replies.getFirst().accept(task);verify(player,never()).openInventory(any(Inventory.class));owners.remove().run();
        verify(player).openInventory(inventory);verify(messages).send(player,"task-loaded","task",task.definition().name(),"progress",3L,"target",10L);
    }
    @Test void permissionRevocationDropsQueuedTaskMenu(){open();when(player.hasPermission("marry.use")).thenReturn(false);finish();assertNoResult();}
    @Test void identityChangeDropsQueuedTaskMenu(){open();when(directory.live(live)).thenReturn(new PlayerSnapshot(live,UUID.randomUUID(),"other","Other",60,null,1));finish();assertNoResult();}
    @Test void missingSnapshotDropsQueuedTaskMenu(){open();when(directory.live(live)).thenReturn(null);finish();assertNoResult();}
    @Test void deadPlayerDoesNotReceiveQueuedTaskMenu(){open();when(player.isDead()).thenReturn(true);finish();assertNoResult();}
    @Test void switchedDatabaseDropsQueuedTaskMenu(){open();when(marriages.databaseGeneration()).thenReturn(UUID.randomUUID());finish();assertNoResult();}
    @Test void permissionRevocationSuppressesQueuedFailure(){
        open();failures.getFirst().accept(new IllegalStateException("test database failure"));when(player.hasPermission("marry.use")).thenReturn(false);owners.remove().run();
        verify(messages,never()).send(eq(player),eq("task-load-failed"),any(Object[].class));
    }
    @Test void repeatedClickKeepsOriginalTaskRequest(){
        open();open();assertEquals(1,replies.size());
        verify(messages).send(player,"task-refresh-busy");finish();verify(player).openInventory(inventory);
    }
    @Test void cancelledReadCannotReopenMenu(){open();gui.cancel(live);finish();assertNoResult();}
    @Test void reloadedGuiDropsTaskReply(){open();gui.reload();finish();assertNoResult();}
    @Test void closedGuiDropsTaskReply(){open();gui.close();finish();assertNoResult();}
    @Test void retiredPlayerReleasesTaskReadForRetry(){
        doAnswer(call->{Runnable retired=call.getArgument(2);retired.run();return null;}).when(scheduler).player(eq(live),any(),any());
        open();replies.getFirst().accept(task);open();
        assertEquals(2,replies.size(),"实体退休必须结束请求，重试不能继续繁忙");assertNoResult();
    }
    @Test void rejectedFailureNotificationReleasesTaskReadForRetry(){
        doThrow(new IllegalStateException("test scheduler rejection")).when(scheduler).player(eq(live),any(),any());
        open();assertDoesNotThrow(()->failures.getFirst().accept(new IllegalStateException("test read failure")));
        open();assertEquals(2,replies.size());assertNoResult();
    }
    @Test void configurationChangeConsumesStaleRequestBeforeRetry(){
        open();var old=config.snapshot();
        when(config.snapshot()).thenReturn(new ConfigurationManager.Snapshot(UUID.randomUUID(),old.files(),old.menus(),old.database(),old.tasks()));
        finish();assertNoResult();open();assertEquals(2,replies.size());
    }
    @Test void oldTaskFailureDoesNotCancelRequestAfterReload(){
        open();gui.reload();open();failures.getFirst().accept(new IllegalStateException("old query failure"));owners.remove().run();
        verify(messages,never()).send(eq(player),eq("task-load-failed"),any(Object[].class));
        replies.get(1).accept(task);owners.remove().run();verify(player).openInventory(inventory);
    }
}
