package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.gui.GuiFactory;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.service.MarriageService;
import cn.mcxyd.xiyuanmarry.service.PlayerDirectory;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProposalCommandRoutingTest {
    final MessageService messages=mock(MessageService.class);
    final PlayerDirectory players=mock(PlayerDirectory.class);
    final MarriageService marriages=mock(MarriageService.class);
    final GuiFactory gui=mock(GuiFactory.class);
    final Player sender=mock(Player.class);
    final PlayerSnapshot actor=snapshot("Alice"), target=snapshot("Bob");
    final MarryCommand root=new MarryCommand(messages,players,marriages,null,gui);

    ProposalCommandRoutingTest(){
        MarriageSubcommands.register(root,marriages,null,players,messages,gui,null,null,null);
        when(sender.hasPermission(anyString())).thenReturn(true);
    }
    static PlayerSnapshot snapshot(String name){
        UUID live=UUID.randomUUID();return new PlayerSnapshot(live,live,name,name,60,null,System.currentTimeMillis());
    }
    void invoke(String... args){assertTrue(root.onCommand(sender,null,"marry",args));}

    @ParameterizedTest @ValueSource(strings={"normal","propose","wedding"})
    void surplusArgumentsNeverSubmitAProposalEvenWhenBothPlayersExist(String command){
        when(players.capture(sender)).thenReturn(actor);when(players.name("Bob")).thenReturn(target);
        invoke(command,"Bob","extra");
        verifyNoInteractions(marriages,gui);verify(messages).send(sender,"invalid-argument");
        verifyNoInteractions(players);
    }

    @ParameterizedTest @ValueSource(strings={"normal","propose","wedding"})
    void missingActorSnapshotGetsFeedbackWithoutSubmittingNull(String command){
        when(players.name("Bob")).thenReturn(target);
        invoke(command,"Bob");
        verify(messages).send(sender,"offline");verifyNoInteractions(marriages,gui);
        verify(players,never()).name(anyString());
    }

    @ParameterizedTest @ValueSource(strings={"normal","propose","wedding"})
    void missingTargetGetsFeedbackAndDoesNotSubmit(String command){
        when(players.capture(sender)).thenReturn(actor);
        invoke(command,"MissingPlayer");
        verify(messages).send(sender,"offline");verifyNoInteractions(marriages,gui);
    }

    @ParameterizedTest @CsvSource({"normal,NORMAL","propose,WEDDING","wedding,WEDDING"})
    void validRequestPreservesExistingMarriageMode(String command,String mode){
        when(players.capture(sender)).thenReturn(actor);when(players.name("Bob")).thenReturn(target);
        invoke(command,"Bob");verify(marriages).propose(actor,target,mode);
        verifyNoMoreInteractions(marriages);verifyNoInteractions(messages,gui);
    }

    @ParameterizedTest @CsvSource({"normal,NORMAL","propose,WEDDING","wedding,WEDDING"})
    void emptyArgumentsKeepOpeningTheCorrectMenu(String command,String mode){
        invoke(command);verify(gui).open(sender,"propose",mode,0);verifyNoInteractions(marriages,messages);
    }

    @ParameterizedTest @ValueSource(strings={"normal","propose","wedding"})
    void nameCompletionMatchesMixedCasePrefix(String command){
        when(players.all()).thenReturn(List.of(actor,target));
        assertEquals(List.of("Alice"),new SubcommandCompleter(root.commands()).onTabComplete(sender,null,"marry",new String[]{command,"aLi"}));
    }

    @ParameterizedTest @ValueSource(strings={"normal","propose","wedding"})
    void noPlayerSuggestionsAfterTheOnlyPlayerArgument(String command){
        when(players.all()).thenReturn(List.of(actor,target));
        assertTrue(new SubcommandCompleter(root.commands()).onTabComplete(sender,null,"marry",new String[]{command,"Bob",""}).isEmpty());
    }

    @ParameterizedTest @ValueSource(strings={"normal","propose","wedding"})
    void permissionRejectionStopsExecutionAndCompletion(String command){
        when(sender.hasPermission(anyString())).thenReturn(false);
        invoke(command,"Bob");verify(messages).send(sender,"no-permission");
        assertTrue(new SubcommandCompleter(root.commands()).onTabComplete(sender,null,"marry",new String[]{command,""}).isEmpty());
        verifyNoInteractions(players,marriages,gui);
    }

    @ParameterizedTest @ValueSource(strings={"normal","propose","wedding"})
    void consoleCannotExecuteOrCompletePlayerOnlyCommands(String command){
        var console=mock(ConsoleCommandSender.class);when(console.hasPermission(anyString())).thenReturn(true);
        assertTrue(root.onCommand(console,null,"marry",new String[]{command,"Bob"}));
        assertTrue(new SubcommandCompleter(root.commands()).onTabComplete(console,null,"marry",new String[]{command,""}).isEmpty());
        verifyNoInteractions(players,marriages,gui);verify(messages).send(console,"player-only");
    }
}
