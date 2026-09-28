package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import java.util.UUID;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RingCallbackTest {
    final UnifiedScheduler scheduler=mock(UnifiedScheduler.class);final PlayerDirectory directory=mock(PlayerDirectory.class);
    final MessageService messages=mock(MessageService.class);final Player sender=mock(Player.class);final Player recipient=mock(Player.class);
    final PlayerInventory inventory=mock(PlayerInventory.class);final UUID senderId=UUID.randomUUID(),target=UUID.randomUUID();
    Consumer<Player> delivery;Runnable retired;RingService rings;
    @BeforeEach void setup(){
        var plugin=mock(JavaPlugin.class);when(plugin.namespace()).thenReturn("xiyuanmarry");when(sender.getUniqueId()).thenReturn(senderId);
        when(recipient.getInventory()).thenReturn(inventory);when(inventory.firstEmpty()).thenReturn(-1);
        when(directory.identity(target)).thenReturn(new PlayerSnapshot(target,target,"name:alice","Alice",60,null,1));
        doAnswer(call->{delivery=call.getArgument(1);retired=call.getArgument(2);return null;}).when(scheduler).player(eq(target),any(),any());
        rings=new RingService(plugin,mock(MarriageService.class),mock(ConfigurationManager.class),directory,scheduler,messages);
    }
    @AfterEach void close(){if(rings!=null)rings.close();}
    @Test void retiredRecipientSchedulesFeedbackOnTheSendersOwner(){
        rings.give(sender,target,"marriage");retired.run();
        verify(messages,never()).send(sender,"offline");verify(scheduler).player(eq(senderId),any());
    }
    @Test void recipientCallbackDoesNotReadTheOriginalSenderPlayer(){
        rings.give(sender,target,"marriage");clearInvocations(sender);delivery.accept(recipient);
        verify(sender,never()).getUniqueId();verify(scheduler).player(eq(senderId),any());
    }
    @Test void queuedDeliveryStopsBeforeInventoryAccessAfterClose(){
        rings.give(sender,target,"marriage");rings.close();delivery.accept(recipient);
        verifyNoInteractions(inventory);verify(recipient,never()).getInventory();
    }
}
