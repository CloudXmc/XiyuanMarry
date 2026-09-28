package cn.mcxyd.xiyuanmarry.gui;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import cn.mcxyd.xiyuanmarry.service.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;

class GuiRoutingTest {
    final ConfigurationManager config=mock(ConfigurationManager.class);
    final Player player=mock(Player.class);
    final UUID generation=UUID.randomUUID();
    GuiFactory gui;
    XiyuanHolder holder;
    @BeforeEach void setup(){
        when(config.snapshot()).thenReturn(new ConfigurationManager.Snapshot(generation,Map.of(),Map.of(),null,null));
        when(player.hasPermission("marry.use")).thenReturn(true);
        gui=spy(new GuiFactory(config,mock(MarriageService.class),mock(WeddingService.class),
                mock(MessageService.class),mock(UnifiedScheduler.class),mock(DailyTaskService.class)));
        doNothing().when(gui).open(any(),anyString(),anyString(),anyInt());
        holder=new XiyuanHolder(UUID.randomUUID(),generation,"main_menu","",0,Map.of());
    }
    @Test void giftButtonOpensDedicatedInbox(){
        gui.activate(player,holder,new XiyuanHolder.Action("gifts",""));
        verify(gui).open(player,"gift","",0);
    }
    @Test void receivedInvitationButtonOpensDedicatedMenu(){
        gui.activate(player,holder,new XiyuanHolder.Action("received-invitations",""));
        verify(gui).open(player,"invitation","",0);
    }
    @Test void existingSendInvitationEntryKeepsItsPurpose(){
        gui.activate(player,holder,new XiyuanHolder.Action("invitations",""));
        verify(gui).open(player,"send_invite","",0);
    }
}
