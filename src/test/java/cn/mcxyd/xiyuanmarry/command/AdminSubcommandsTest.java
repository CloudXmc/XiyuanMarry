package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.service.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.mockito.Mockito.*;

class AdminSubcommandsTest {
    final MarriageService marriages = mock(MarriageService.class);
    final MessageService messages = mock(MessageService.class);
    final MarryAdminCommand root = new MarryAdminCommand(messages);
    AdminSubcommandsTest() {AdminSubcommands.register(root, marriages, mock(PlayerDirectory.class), messages, mock(RingService.class));}
    CommandSender console() {var sender = mock(CommandSender.class); when(sender.hasPermission(anyString())).thenReturn(true); return sender;}
    void invoke(CommandSender sender, String... args) {root.onCommand(sender, null, "marryadmin", args);}
    @Test void missingNumericArgumentReportsToConsole() {var sender=console(); invoke(sender, "setexp", "Alice"); verify(messages).send(sender, "invalid-argument");}
    @Test void unknownPlayerReportsToConsole() {var sender=console(); invoke(sender, "setlevel", "Missing", "2"); verify(messages).send(sender, "offline");}
    @Test void malformedNumberReportsToConsole() {var sender=console(); when(marriages.identityByName("Alice")).thenReturn(UUID.randomUUID()); invoke(sender, "setexp", "Alice", "abc"); verify(messages).send(sender, "invalid-argument");}
    @Test void clearMissingPlayerDoesNotSilentlyReturn() {var sender=console(); invoke(sender, "clear", "Missing", "confirm"); verify(messages).send(sender, "offline");}
    @Test void destructiveCommandsKeepThePlayerActorForFeedback() {
        var sender=mock(Player.class); var actor=UUID.randomUUID(); var target=UUID.randomUUID();
        when(sender.getUniqueId()).thenReturn(actor); when(sender.hasPermission(anyString())).thenReturn(true); when(marriages.identityByName("Alice")).thenReturn(target);
        invoke(sender, "divorce", "Alice", "confirm"); invoke(sender, "clear", "Alice", "confirm");
        verify(marriages).admin(actor, "divorce", target, null, 0); verify(marriages).admin(actor, "clear", target, null, 0);
    }
}
