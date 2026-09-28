package cn.mcxyd.xiyuanmarry.command;
import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class CommandAccessTest {
    @Test void taskRequiresPlayerAndPermission(){
        var task=new TaskSubcommand(null,null);
        assertEquals("no-permission",CommandAccess.rejection(task,true,p->false));
        assertEquals("player-only",CommandAccess.rejection(task,false,p->true));
        assertNull(CommandAccess.rejection(task,true,p->true));
    }
    @Test void helpAndTopLevelCompletionRespectVisibilityAndPrefix(){
        var task=new TaskSubcommand(null,null);var rank=new RankSubcommand(null,null);
        assertEquals(List.of("rank"),CommandAccess.visibleNames(List.of(task,rank),true,p->true,"R"));
        assertTrue(CommandAccess.visibleNames(List.of(task,rank),true,p->false,"").isEmpty());
    }
    @Test void rankArgumentsHaveOnlySupportedCompletions(){
        var rank=new RankSubcommand(null,null);
        assertEquals(List.of("bond"),rank.complete(null,new String[]{"bo"}));
        assertEquals(4,rank.complete(null,new String[]{""}).size());
        assertTrue(rank.complete(null,new String[]{"bond",""}).isEmpty());
    }
}
