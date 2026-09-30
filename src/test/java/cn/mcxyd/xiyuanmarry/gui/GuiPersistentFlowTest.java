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
    final List<Consumer<Throwable>> inboxFailures=new ArrayList<>();
    final List<Consumer<Throwable>> invitationFailures=new ArrayList<>();
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
        doAnswer(call->{Function<MarriageRepository,Object> work=call.getArgument(2);Consumer<Object> done=call.getArgument(3);invitationFailures.add(call.getArgument(4));queries.add(()->done.accept(work.apply(mock(MarriageRepository.class))));return null;})
            .when(marriages).submitAtGeneration(eq(live),eq(database),any(),any(),any());
        doAnswer(call->{Consumer<Player> callback=call.getArgument(1);owners.add(()->callback.accept(player));return null;}).when(scheduler).player(eq(live),any());
        doAnswer(call->{Consumer<Player> callback=call.getArgument(1);owners.add(()->callback.accept(player));return null;}).when(scheduler).player(eq(live),any(),any());
        doAnswer(call->{inboxReplies.add(call.getArgument(1));inboxFailures.add(call.getArgument(2));return null;}).when(inbox).load(eq(actor),any(),any());
        gui=spy(new GuiFactory(config,marriages,weddings,new MessageService(config),scheduler,mock(DailyTaskService.class),inbox));
        doAnswer(call->{rendered.add(call.getArgument(4));return null;}).when(gui).render(any(),anyString(),anyString(),anyInt(),anyList());
    }
    void finishInvitation(){queries.remove().run();owners.remove().run();}
    @Test void invitationQueryReturnsThroughPlayerOwnerBeforeRendering(){
        gui.open(player,"invitation","",0);assertTrue(rendered.isEmpty());queries.remove().run();assertTrue(rendered.isEmpty());
        owners.remove().run();assertEquals("selected-wedding",rendered.getFirst().getFirst().value());
    }
    @Test void failedInvitationQueryGivesFeedbackOnPlayerOwnerAndCannotRenderLater(){
        gui.open(player,"invitation","",0);
        clearInvocations(player);
        invitationFailures.getFirst().accept(new IllegalStateException("test database failure"));
        assertFalse(owners.isEmpty(),"失败也必须回到玩家实体上下文处理");
        verifyNoInteractions(player);
        owners.remove().run();
        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
        finishInvitation();
        assertTrue(rendered.isEmpty(),"失败令牌必须被消费，不能随后打开旧菜单");
        gui.open(player,"invitation","",0);finishInvitation();
        assertEquals(1,rendered.size(),"失败后可以立即重试");
    }
    @Test void oldFailureDoesNotCancelNewerInvitationRequest(){
        gui.open(player,"invitation","",0);
        gui.open(player,"invitation","",0);
        invitationFailures.getFirst().accept(new IllegalStateException("old failure"));
        while(!owners.isEmpty())owners.remove().run();
        finishInvitation();finishInvitation();
        assertEquals(1,rendered.size());
    }
    @Test void busyInvitationFailureDoesNotSendDuplicateNotification(){
        gui.open(player,"invitation","",0);clearInvocations(player);
        invitationFailures.getFirst().accept(new IllegalStateException("操作繁忙"));
        while(!owners.isEmpty())owners.remove().run();
        verify(player,never()).sendMessage(any(net.kyori.adventure.text.Component.class));
        finishInvitation();assertTrue(rendered.isEmpty(),"繁忙拒绝也必须清理令牌");
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
    @Test void failedGiftReadClearsRequestAndRetryCanRender(){
        gui.open(player,"gift","",0);clearInvocations(player);
        inboxFailures.getFirst().accept(new IllegalStateException("test inbox error"));
        verifyNoInteractions(player);owners.remove().run();
        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
        inboxReplies.getFirst().accept(player,List.of());assertTrue(rendered.isEmpty());
        gui.open(player,"gift","",0);inboxReplies.get(1).accept(player,List.of());assertEquals(1,rendered.size());
    }
    @Test void delayedGiftFailureCannotCancelReplacementRequest(){
        gui.open(player,"gift","",0);inboxFailures.getFirst().accept(new IllegalStateException("old error"));
        gui.open(player,"gift","",0);clearInvocations(player);owners.remove().run();
        verify(player,never()).sendMessage(any(net.kyori.adventure.text.Component.class));
        inboxReplies.get(1).accept(player,List.of());assertEquals(1,rendered.size());
    }
    @Test void failedCallbackAfterReloadOrCloseStaysSilent(){
        gui.open(player,"gift","",0);inboxFailures.getFirst().accept(new IllegalStateException("late error"));
        gui.reload();clearInvocations(player);owners.remove().run();verifyNoInteractions(player);
        gui.open(player,"gift","",0);gui.close();clearInvocations(player);
        inboxFailures.get(1).accept(new IllegalStateException("closed error"));
        while(!owners.isEmpty())owners.remove().run();verifyNoInteractions(player);
    }
    @Test void failureDoesNotNotifyChangedIdentityOrDeadPlayer(){
        gui.open(player,"gift","",0);inboxFailures.getFirst().accept(new IllegalStateException("late error"));
        when(directory.live(live)).thenReturn(new PlayerSnapshot(live,UUID.randomUUID(),"other","Other",60,null,1));
        clearInvocations(player);owners.remove().run();verify(player,never()).sendMessage(any(net.kyori.adventure.text.Component.class));
        when(directory.live(live)).thenReturn(actor);gui.open(player,"gift","",0);
        inboxFailures.get(1).accept(new IllegalStateException("dead player"));when(player.isDead()).thenReturn(true);
        clearInvocations(player);owners.remove().run();verify(player,never()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }
    @Test void discardedSuccessConsumesTokenBeforeIdentityOrDatabaseCanChangeBack(){
        gui.open(player,"gift","",0);
        when(marriages.databaseGeneration()).thenReturn(UUID.randomUUID());
        inboxReplies.getFirst().accept(player,List.of());
        when(marriages.databaseGeneration()).thenReturn(database);
        inboxReplies.getFirst().accept(player,List.of());assertTrue(rendered.isEmpty());
    }
    @Test void missingActorCannotSendProposalOrWeddingInvitation(){
        when(directory.capture(player)).thenReturn(null);
        for(String page:List.of("propose","send_invite")){
            var holder=new XiyuanHolder(live,generation,page,"NORMAL",0,Map.of());
            assertDoesNotThrow(()->gui.activate(player,holder,new XiyuanHolder.Action("select",live.toString())));
        }
        verify(marriages,never()).propose(any(),any(),anyString());
        verify(weddings,never()).invite(any(),any());
    }
    @Test void missingActorCannotAcceptWeddingInvitation(){
        when(directory.capture(player)).thenReturn(null);
        var holder=new XiyuanHolder(live,generation,"invitation","",0,Map.of());
        assertDoesNotThrow(()->gui.activate(player,holder,new XiyuanHolder.Action("select","chosen")));
        verify(weddings,never()).respond(any(),anyString(),anyBoolean());
        verify(player,never()).closeInventory();
    }
    @Test void rightClickDeclinesOnlySelectedInvitation(){
        var holder=new XiyuanHolder(live,generation,"invitation","",0,Map.of());
        gui.activate(player,holder,new XiyuanHolder.Action("select","chosen"),true);
        verify(weddings).respond(actor,"chosen",false);verify(player).closeInventory();
    }
}
