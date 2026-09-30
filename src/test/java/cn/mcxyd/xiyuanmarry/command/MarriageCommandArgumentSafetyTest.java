package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.gui.GuiFactory;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.service.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MarriageCommandArgumentSafetyTest {
    final MessageService messages=mock(MessageService.class);
    final PlayerDirectory players=mock(PlayerDirectory.class);
    final MarriageService marriages=mock(MarriageService.class);
    final WeddingService weddings=mock(WeddingService.class);
    final GuiFactory gui=mock(GuiFactory.class);
    final Player sender=mock(Player.class);
    final MarryCommand root=new MarryCommand(messages,players,marriages,weddings,gui);
    final PlayerSnapshot actor=new PlayerSnapshot(UUID.randomUUID(),UUID.randomUUID(),"alice","Alice",60,null,System.currentTimeMillis());

    MarriageCommandArgumentSafetyTest(){
        MarriageSubcommands.register(root,marriages,weddings,players,messages,gui,null,null,null);
        when(sender.hasPermission(anyString())).thenReturn(true);
    }

    void invoke(String... args){assertTrue(root.onCommand(sender,null,"marry",args));}

    @ParameterizedTest
    @ValueSource(strings={"block","unblock","setweddingloc","hunliset","invite","startw","oath","gift","weddinggift","ring"})
    void extraArgumentsAreRejectedBeforeAnyLookup(String command){
        invoke(command,"first","extra");
        verify(messages).send(sender,"invalid-argument");
        verifyNoInteractions(players,marriages,weddings,gui);
    }

    @ParameterizedTest
    @ValueSource(strings={"block","unblock","hunliset","invite","chat","weddinggift"})
    void missingRequiredArgumentsAreRejected(String command){
        invoke(command);
        verify(messages).send(sender,"invalid-argument");
        verifyNoInteractions(players,marriages,weddings,gui);
    }

    @ParameterizedTest
    @ValueSource(strings={"setweddingloc","startw","oath","gift","ring"})
    void missingActorSnapshotStopsNoArgumentCommands(String command){
        invoke(command);
        verify(players).capture(sender);
        verify(messages).send(sender,"database-not-ready");
        verifyNoInteractions(marriages,weddings,gui);
    }

    @ParameterizedTest
    @ValueSource(strings={"block","unblock","invite","weddinggift"})
    void missingActorSnapshotStopsTargetCommandsBeforeLookup(String command){
        when(players.capture(sender)).thenReturn(null);
        invoke(command,"Bob");
        verify(players).capture(sender);
        verify(messages).send(sender,"database-not-ready");
        verifyNoMoreInteractions(players);
        verifyNoInteractions(marriages,weddings,gui);
    }

    @ParameterizedTest
    @ValueSource(strings={"hunliset","chat"})
    void missingActorSnapshotStopsParameterizedCommands(String command){
        when(players.capture(sender)).thenReturn(null);
        invoke(command,command.equals("hunliset")?"nx":"hello");
        verify(players).capture(sender);
        verify(messages).send(sender,"database-not-ready");
        verifyNoInteractions(marriages,weddings,gui);
    }
}
