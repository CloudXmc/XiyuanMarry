package cn.mcxyd.xiyuanmarry.gui;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.service.*;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

class GuiMalformedValueTest {
    private final ConfigurationManager config=mock(ConfigurationManager.class);
    private final MessageService messages=mock(MessageService.class);
    private final MarriageService marriages=mock(MarriageService.class);
    private final Player player=mock(Player.class);
    private final UUID generation=UUID.randomUUID();
    private GuiFactory gui;

    @BeforeEach void setup() {
        when(config.snapshot()).thenReturn(new ConfigurationManager.Snapshot(generation,Map.of(),Map.of(),null,null));
        when(player.hasPermission("marry.use")).thenReturn(true);
        gui=new GuiFactory(config,marriages,mock(WeddingService.class),messages,mock(UnifiedScheduler.class),mock(DailyTaskService.class));
    }

    @Test void malformedPartnerValueShowsFriendlyError() {
        var holder=new XiyuanHolder(UUID.randomUUID(),generation,"partner_info","",0,Map.of());
        gui.activate(player,holder,new XiyuanHolder.Action("select","not-a-uuid"));
        verify(messages).send(player,"invalid-argument");
        verify(marriages,never()).info(any(),any());
    }

    @Test void malformedProposalValueShowsFriendlyError() {
        var holder=new XiyuanHolder(UUID.randomUUID(),generation,"propose","NORMAL",0,Map.of());
        gui.activate(player,holder,new XiyuanHolder.Action("select","not-a-uuid"));
        verify(messages).send(player,"invalid-argument");
        verify(marriages,never()).propose(any(),any(),anyString());
    }
}
