package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.gui.GuiFactory;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.service.*;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RelationshipCommandRoutingTest {
    final MessageService messages = mock(MessageService.class);
    final PlayerDirectory players = mock(PlayerDirectory.class);
    final MarriageService marriages = mock(MarriageService.class);
    final WeddingService weddings = mock(WeddingService.class);
    final PartnerTeleportService teleports = mock(PartnerTeleportService.class);
    final GuiFactory gui = mock(GuiFactory.class);
    final Player sender = mock(Player.class);
    final PlayerSnapshot actor = new PlayerSnapshot(UUID.randomUUID(), UUID.randomUUID(),
            "alice", "Alice", 60, null, System.currentTimeMillis());
    final MarryCommand root = new MarryCommand(messages, players, marriages, weddings, gui);

    RelationshipCommandRoutingTest() {
        MarriageSubcommands.register(root, marriages, weddings, players, messages, gui, null, null, null);
        root.add(new TeleportSubcommand(teleports, players, messages));
        when(sender.hasPermission(anyString())).thenReturn(true);
    }

    void invoke(String... args) {
        assertDoesNotThrow(() -> assertTrue(root.onCommand(sender, null, "marry", args)));
    }

    List<String> complete(String... args) {
        return new SubcommandCompleter(root.commands()).onTabComplete(sender, null, "marry", args);
    }

    @ParameterizedTest @ValueSource(strings={"info","partner","cancel","divorce","withdraw","tp"})
    void unexpectedArgumentCannotReadOrMutateRelationship(String command) {
        when(players.capture(sender)).thenReturn(actor);
        invoke(command, "extra");
        verify(messages).send(sender, "invalid-argument");
        verifyNoInteractions(players, marriages, weddings, teleports, gui);
        assertTrue(complete(command, "").isEmpty());
    }

    @ParameterizedTest @ValueSource(strings={"info","partner","cancel","divorce","withdraw","accept","deny","tp"})
    void missingSnapshotReportsUnavailableWithoutCallingService(String command) {
        invoke(command);
        verify(messages).send(sender, "database-not-ready");
        verify(players).capture(sender);
        verifyNoMoreInteractions(players);
        verifyNoInteractions(marriages, weddings, teleports, gui);
    }

    @ParameterizedTest @ValueSource(strings={"info","partner","cancel","divorce","withdraw","accept","deny","tp"})
    void validEmptyArgumentsPreserveExistingOperationAndPersistentIdentity(String command) {
        when(players.capture(sender)).thenReturn(actor);
        invoke(command);
        switch (command) {
            case "info", "partner" -> verify(marriages).info(sender, actor.id());
            case "cancel" -> verify(marriages).cancel(actor);
            case "divorce" -> verify(marriages).divorce(actor, false);
            case "withdraw" -> verify(marriages).divorce(actor, true);
            case "accept" -> verify(marriages).accept(actor);
            case "deny" -> verify(marriages).deny(actor);
            case "tp" -> verify(teleports).teleport(actor);
            default -> fail(command);
        }
        verifyNoMoreInteractions(marriages, teleports);
        verifyNoInteractions(weddings, gui, messages);
    }

    @ParameterizedTest @ValueSource(strings={"info","partner","cancel","divorce","withdraw","accept","deny","tp"})
    void permissionRejectionStopsExecutionAndCompletion(String command) {
        when(sender.hasPermission(anyString())).thenReturn(false);
        invoke(command);
        verify(messages).send(sender, "no-permission");
        assertTrue(complete(command, "").isEmpty());
        verifyNoInteractions(players, marriages, weddings, teleports, gui);
    }

    @ParameterizedTest @ValueSource(strings={"info","partner","cancel","divorce","withdraw","accept","deny","tp"})
    void consoleCannotExecuteOrCompletePlayerOnlyCommands(String command) {
        var console = mock(ConsoleCommandSender.class);
        when(console.hasPermission(anyString())).thenReturn(true);
        assertTrue(root.onCommand(console, null, "marry", new String[]{command}));
        verify(messages).send(console, "player-only");
        assertTrue(new SubcommandCompleter(root.commands())
                .onTabComplete(console, null, "marry", new String[]{command, ""}).isEmpty());
        verifyNoInteractions(players, marriages, weddings, teleports, gui);
    }

    @ParameterizedTest @CsvSource({"accept,PrOpOsAl","accept,InViTaTiOn","deny,PrOpOsAl","deny,InViTaTiOn"})
    void explicitRequestKindRoutesOnlyToSelectedService(String command, String kind) {
        when(players.capture(sender)).thenReturn(actor);
        invoke(command, kind);
        boolean accept = command.equals("accept");
        if (kind.equalsIgnoreCase("invitation")) {
            verify(weddings).respond(actor, accept);
        } else if (accept) {
            verify(marriages).accept(actor);
        } else {
            verify(marriages).deny(actor);
        }
        verifyNoMoreInteractions(marriages, weddings);
        verifyNoInteractions(teleports, gui, messages);
    }

    @ParameterizedTest @ValueSource(strings={"accept","deny"})
    void invalidRequestKindAndExtraArgumentsNeverCaptureOrSubmit(String command) {
        when(players.capture(sender)).thenReturn(actor);
        invoke(command, "invitaton");
        invoke(command, "proposal", "extra");
        invoke(command, "invitation", "extra");
        verify(messages, times(3)).send(sender, "invalid-argument");
        verifyNoInteractions(players, marriages, weddings, teleports, gui);
    }

    @ParameterizedTest @ValueSource(strings={"accept","deny"})
    void requestKindCompletionMatchesCaseAndStopsAfterOneArgument(String command) {
        assertEquals(List.of("proposal", "invitation"), complete(command, ""));
        assertEquals(List.of("invitation"), complete(command, "InV"));
        assertTrue(complete(command, "proposal", "").isEmpty());
        assertTrue(complete(command, "invitation", "").isEmpty());
        verifyNoInteractions(players, marriages, weddings, teleports, gui);
    }

    @ParameterizedTest @ValueSource(strings={"accept","deny"})
    void absentProposalNeverFallsBackToAnInvitation(String command) {
        when(players.capture(sender)).thenReturn(actor);
        if (command.equals("accept")) {
            doThrow(new RuleViolation("request-missing")).when(marriages).accept(actor);
        } else {
            doThrow(new RuleViolation("request-missing")).when(marriages).deny(actor);
        }
        invoke(command);
        verify(messages).send(sender, "request-missing");
        verifyNoInteractions(weddings, teleports, gui);
    }
}
