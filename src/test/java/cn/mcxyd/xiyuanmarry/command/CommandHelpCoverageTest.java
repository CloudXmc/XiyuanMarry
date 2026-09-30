package cn.mcxyd.xiyuanmarry.command;
import cn.mcxyd.xiyuanmarry.gui.GuiFactory;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.service.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CommandHelpCoverageTest {
    final MessageService messages=mock(MessageService.class);final PlayerDirectory players=mock(PlayerDirectory.class);
    final MarriageService marriages=mock(MarriageService.class);final GuiFactory gui=mock(GuiFactory.class);
    final MarryCommand root=new MarryCommand(messages,players,marriages,null,gui);
    CommandHelpCoverageTest(){
        MarriageSubcommands.register(root,marriages,null,players,messages,gui,null,null,null);
        root.add(new ClaimSubcommand(null,null,null,players,messages));root.add(new TeleportSubcommand(null,players,messages));
    }
    @Test void everyRegisteredCommandHasHelpText(){
        var yaml=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile());
        var admin=new MarryAdminCommand(messages);AdminSubcommands.register(admin,marriages,players,messages,null);
        for(var cmd:root.commands().values())assertFalse(yaml.getString("help-"+cmd.name(),"").isBlank(),cmd.name());
        for(var cmd:admin.commands().values())assertFalse(yaml.getString("admin-help-"+cmd.name(),"").isBlank(),cmd.name());
    }
    @Test void menuIsDiscoverableInHelpAndTabCompletion(){
        var player=mock(Player.class);when(player.hasPermission(anyString())).thenReturn(true);
        assertTrue(root.commands().containsKey("menu"));
        assertTrue(new SubcommandCompleter(root.commands()).onTabComplete(player,null,"marry",new String[]{"me"}).contains("menu"));
    }
    @Test void emptyCommandStillOpensTheMainMenu(){
        var player=mock(Player.class);when(player.hasPermission(anyString())).thenReturn(true);
        root.onCommand(player,null,"marry",new String[0]);verify(gui).open(player,"main_menu","",0);
    }
    @Test void menuSearchRoutesQueryAndCompletesOnlineNames(){
        var player=mock(Player.class);when(player.hasPermission(anyString())).thenReturn(true);
        root.onCommand(player,null,"marry",new String[]{"menu","propose","Alice"});
        verify(gui).openFiltered(player,"propose","WEDDING","Alice");
        when(gui.searchCandidates("propose")).thenReturn(java.util.List.of("Alice","Bob"));
        assertEquals(java.util.List.of("Alice"),new SubcommandCompleter(root.commands()).onTabComplete(player,null,"marry",new String[]{"menu","propose","ali"}));
    }
    @Test void unsupportedMenuAndExcessArgumentsDoNotOpenAnything(){
        var player=mock(Player.class);when(player.hasPermission(anyString())).thenReturn(true);
        root.onCommand(player,null,"marry",new String[]{"menu","not-a-menu"});
        root.onCommand(player,null,"marry",new String[]{"menu","gift","a","extra"});
        verifyNoInteractions(gui);verify(messages,times(2)).send(player,"invalid-argument");
    }
    @Test void normalMenuSearchPreservesMarriageMode(){
        var player=mock(Player.class);when(player.hasPermission(anyString())).thenReturn(true);
        root.onCommand(player,null,"marry",new String[]{"menu","normal","Alice"});
        verify(gui).openFiltered(player,"propose","NORMAL","Alice");
    }
    @Test void normalProposalRejectsSurplusArgumentsBeforeLookingUpPlayers(){
        var player=mock(Player.class);when(player.hasPermission(anyString())).thenReturn(true);
        root.onCommand(player,null,"marry",new String[]{"normal","Bob","extra"});
        verify(messages).send(player,"invalid-argument");
        verifyNoInteractions(players,marriages,gui);
    }
}
