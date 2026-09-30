package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.service.*;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class TeleportSubcommandTest {
    @Test void missingSnapshotReportsUnavailableWithoutCallingService(){
        var service=mock(PartnerTeleportService.class);var directory=mock(PlayerDirectory.class);
        var messages=mock(MessageService.class);var player=mock(Player.class);
        new TeleportSubcommand(service,directory,messages).execute(player,"marry",new String[0]);
        verify(messages).send(player,"database-not-ready");verifyNoInteractions(service);
    }
}
