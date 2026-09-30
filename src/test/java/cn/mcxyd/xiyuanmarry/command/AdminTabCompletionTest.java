package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.service.*;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminTabCompletionTest {
    final MarriageService marriages=mock(MarriageService.class);
    final PlayerDirectory players=mock(PlayerDirectory.class);
    final MessageService messages=mock(MessageService.class);
    final RingService ring=mock(RingService.class);
    final CommandSender console=mock(CommandSender.class);
    final MarryAdminCommand root=new MarryAdminCommand(messages);
    AdminTabCompletionTest(){
        when(console.hasPermission(anyString())).thenReturn(true);
        when(players.all()).thenReturn(List.of(
            new PlayerSnapshot(UUID.randomUUID(),UUID.randomUUID(),"one","Alice",60,null,1),
            new PlayerSnapshot(UUID.randomUUID(),UUID.randomUUID(),"two","Bob",60,null,1)));
        AdminSubcommands.register(root,marriages,players,messages,ring);
    }
    List<String> complete(String... args){return new SubcommandCompleter(root.commands()).onTabComplete(console,null,"marryadmin",args);}
    @Test void playerArgumentsSuggestOnlineNames(){assertEquals(List.of("Alice","Bob"),complete("force",""));assertEquals(List.of("Bob"),complete("divorce","b"));}
    @Test void secondForceArgumentAlsoSuggestsNames(){assertEquals(List.of("Alice","Bob"),complete("force","Alice",""));}
    @Test void numericAndRingArgumentsHaveChoices(){assertEquals(List.of("1","2","3","4","5","6","7","8","9","10"),complete("setlevel","Alice",""));assertEquals(List.of("engagement","marriage"),complete("givering","Alice",""));}
    @Test void extraArgumentsReturnNothing(){assertTrue(complete("info","Alice","").isEmpty());}
}
