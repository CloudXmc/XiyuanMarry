package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.service.PlayerDirectory;
import cn.mcxyd.xiyuanmarry.service.WeddingService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class InvitationResponseSubcommandTest {
    @Test void malformedInvitationIdIsRejectedBeforePlayerLookup() {
        var weddings=mock(WeddingService.class);
        var players=mock(PlayerDirectory.class);
        var messages=mock(MessageService.class);
        var sender=mock(Player.class);
        new InvitationResponseSubcommand(true,weddings,players,messages)
                .execute(sender,"acceptinvitation",new String[]{"not-a-uuid"});
        verify(messages).send(sender,"invalid-argument");
        verifyNoInteractions(players,weddings);
    }
}
