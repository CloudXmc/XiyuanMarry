package cn.mcxyd.xiyuanmarry.gui;
import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.repository.MarriageRepository;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import cn.mcxyd.xiyuanmarry.service.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuiPersistentFlowTest {
    final ConfigurationManager config=mock(ConfigurationManager.class);
    final MarriageService marriages=mock(MarriageService.class);
    final PlayerDirectory directory=mock(PlayerDirectory.class);
    final WeddingService weddings=mock(WeddingService.class);
    final ClaimInboxService inbox=mock(ClaimInboxService.class);
    final UnifiedScheduler scheduler=mock(UnifiedScheduler.class);
    final Player player=mock(Player.class);
    final UUID live=UUID.randomUUID(),generation=UUID.randomUUID(),database=UUID.randomUUID();
    final PlayerSnapshot actor=new PlayerSnapshot(live,live,"name:guest","Guest",60,null,1);
    final Queue<Runnable> queries=new ArrayDeque<>(),owners=new ArrayDeque<>();
    final List<BiConsumer<Player,List<InboxMessage>>> inboxReplies=new ArrayList<>();
    final List<List<MenuContentProvider.Entry>> rendered=new ArrayList<>();
    GuiFactory gui;
    @BeforeEach void setup(){
        when(config.snapshot()).thenReturn(new ConfigurationManager.Snapshot(generation,Map.of(),Map.of(),null,null));
        when(config.messages()).thenReturn(YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile()));
        when(marriages.directory()).thenReturn(directory);when(marriages.databaseGeneration()).thenReturn(database);
        when(directory.capture(player)).thenReturn(actor);when(directory.live(live)).thenReturn(actor);
        when(player.getUniqueId()).thenReturn(live);when(player.hasPermission("marry.use")).thenReturn(true);
        when(weddings.pendingInvitations(any(),eq(live))).thenReturn(List.of(new WeddingInvitationService.Invitation("selected-wedding",UUID.randomUUID(),UUID.randomUUID(),Long.MAX_VALUE)));
        when(marriages.name(any())).thenReturn("Alice");
        doAnswer(call->{Function<MarriageRepository,Object> work=call.getArgument(2);Consumer<Object> done=call.getArgument(3);queries.add(()->done.accept(work.apply(mock(MarriageRepository.class))));return null;})
            .when(marriages).submitAtGeneration(eq(live),eq(database),any(),any(),any());
        doAnswer(call->{Consumer<Player> callback=call.getArgument(1);owners.add(()->callback.accept(player));return null;}).when(scheduler).player(eq(live),any());
        doAnswer(call->{inboxReplies.add(call.getArgument(1));return null;}).when(inbox).load(eq(actor),any());
        gui=spy(new GuiFactory(config,marriages,weddings,new MessageService(config),scheduler,mock(DailyTaskService.class),inbox));
        doAnswer(call->{rendered.add(call.getArgument(4));return null;}).when(gui).render(any(),anyString(),anyString(),anyInt(),anyList());
    }
    void finishInvitation(){queries.remove().run();owners.remove().run();}
    @Test void invitationQueryReturnsThroughPlayerOwnerBeforeRendering(){
        gui.open(player,"invitation","",0);assertTrue(rendered.isEmpty());queries.remove().run();assertTrue(rendered.isEmpty());
        owners.remove().run();assertEquals("selected-wedding",rendered.getFirst().getFirst().value());
    }
    @Test void closingInventoryDropsPendingQuery(){gui.open(player,"invitation","",0);gui.cancel(live);finishInvitation();assertTrue(rendered.isEmpty());}
    @Test void reloadingDropsPendingQuery(){gui.open(player,"invitation","",0);gui.reload();finishInvitation();assertTrue(rendered.isEmpty());}
    @Test void closingServiceDropsPendingQuery(){gui.open(player,"invitation","",0);gui.close();finishInvitation();assertTrue(rendered.isEmpty());}
    @Test void switchingDatabaseDropsPendingQuery(){gui.open(player,"invitation","",0);when(marriages.databaseGeneration()).thenReturn(UUID.randomUUID());finishInvitation();assertTrue(rendered.isEmpty());}
    @Test void changingIdentityDropsPreviousPrivateData(){gui.open(player,"invitation","",0);when(directory.live(live)).thenReturn(new PlayerSnapshot(live,UUID.randomUUID(),"other","Other",60,null,1));finishInvitation();assertTrue(rendered.isEmpty());}
    @Test void permissionRevocationDropsPendingQuery(){gui.open(player,"invitation","",0);when(player.hasPermission("marry.use")).thenReturn(false);finishInvitation();assertTrue(rendered.isEmpty());}
    @Test void onlyLatestInboxRequestMayRender(){
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        gui.open(player,"gift","",0);gui.open(player,"gift","",0);
        inboxReplies.get(0).accept(player,List.of(InboxMessage.of("gift-id","id",first,"sender","Old")));
        assertTrue(rendered.isEmpty());inboxReplies.get(1).accept(player,List.of(InboxMessage.of("gift-id","id",second,"sender","New")));
        assertEquals(second.toString(),rendered.getFirst().getFirst().value());
    }
    @Test void giftClickUsesExistingClaimCommandWithExactTicketId(){
        var commands=new ArrayList<List<String>>();gui.commands((p,args)->commands.add(List.of(args)));UUID ticket=UUID.randomUUID();
        var holder=new XiyuanHolder(live,generation,"gift","",0,Map.of());
        gui.activate(player,holder,new XiyuanHolder.Action("select",ticket.toString()));
        verify(player).closeInventory();assertEquals(List.of(List.of("claim",ticket.toString())),commands);
    }
    @Test void rightClickDeclinesOnlySelectedInvitation(){
        var holder=new XiyuanHolder(live,generation,"invitation","",0,Map.of());
        gui.activate(player,holder,new XiyuanHolder.Action("select","chosen"),true);
        verify(weddings).respond(actor,"chosen",false);verify(player).closeInventory();
    }
}
