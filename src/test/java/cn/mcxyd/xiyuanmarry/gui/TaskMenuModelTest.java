package cn.mcxyd.xiyuanmarry.gui;
import cn.mcxyd.xiyuanmarry.config.TaskCatalog;import cn.mcxyd.xiyuanmarry.model.*;
import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class TaskMenuModelTest {
    @Test void currentProgressSurvivesDefinitionReloadAndFutureDaysStayLocked() {
        var before=new TaskDefinition("build","PLACE_BLOCK","建造","*",10,20);
        var after=new TaskDefinition("kill","KILL_MONSTER","战斗","*",30,100);
        var rows=TaskMenuModel.rows(new DailyTask("c",31,before,8,false),new TaskCatalog(50,Collections.nCopies(30,after),List.of()));
        assertEquals(30,rows.size());assertEquals(2,rows.get(1).cycle());
        assertEquals(before,rows.get(1).definition());assertEquals(8,rows.get(1).progress());
        assertEquals("active",rows.get(1).state());assertEquals("past",rows.getFirst().state());
        assertEquals("locked",rows.getLast().state());assertEquals(after,rows.getLast().definition());
    }
}
