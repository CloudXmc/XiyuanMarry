package cn.mcxyd.xiyuanmarry.config;

import cn.mcxyd.xiyuanmarry.gui.GuiLayout;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GuiAlignmentTest {
    @Test void originalTwentyOneSlotMenuAndItsLegacyNavigationUpgradeToCurrentDefaults(){
        var legacy=List.of("#########","#DDDDDDD#","#DDDDDDD#","#DDDDDDD#","#########");
        for(String name:List.of("propose","send_invite","invitation","gift","task","rank","partner_info")){
            for(boolean navigation:List.of(false,true)){
                var actual=read(name);var rows=new ArrayList<>(legacy);
                if(navigation){rows.set(4,"#AAAAAAA#");rows.add("P###F###N");}
                actual.set("layout",rows);actual.set("icons.D.name","自定义动态标题");
                var defaults=read(name);
                ConfigurationDefaults.merge(actual,defaults,"gui/"+name+".yml");
                assertEquals(defaults.getStringList("layout"),actual.getStringList("layout"),name);
                assertEquals("自定义动态标题",actual.getString("icons.D.name"));
                var serialized=actual.saveToString();
                ConfigurationDefaults.merge(actual,defaults,"gui/"+name+".yml");
                assertEquals(serialized,actual.saveToString(),"再次 reload 不应改变已升级的布局");
            }
        }
    }
    @Test void distinctCustomTwentyOneSlotLayoutIsPreserved(){
        var custom=List.of("#########","DDDDDDDDD","DDDDDDAAA","AAADDDDDD","#########");
        var actual=read("task");actual.set("layout",custom);
        ConfigurationDefaults.merge(actual,read("task"),"gui/task.yml");
        assertEquals(custom,actual.getStringList("layout"));
    }
    private YamlConfiguration read(String name){return YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui/"+name+".yml").toFile());}
    @Test void mainMenuKeepsAllNineActionsInThreeAlignedColumns(){
        var layout=GuiLayout.parse(read("main_menu"));
        assertEquals(List.of("#########","#ACAIAWA#","#ATAUARA#","#AQABAGA#","#########"),layout.rows());
        var actions=new HashSet<String>();for(var row:layout.rows())for(char c:row.toCharArray())if(c!='A'&&c!='#')actions.add(layout.icons().get(c).action());
        assertEquals(Set.of("info","normal-marriage","wedding-marriage","wedding-plan","task","rank","gifts","tp","received-invitations"),actions);
    }
    @Test void weddingPreparationUsesCentredGroupsAndPartnerCardUsesCentre(){
        assertEquals(List.of("#########","#LUVYIAA#","#AAAAAAA#","#GASACAA#","#########"),read("wedding_plan").getStringList("layout"));
        assertEquals(List.of(22),GuiLayout.parse(read("partner_info")).dynamicSlots());
    }
    @Test void everyListUsesFourEqualRowsWithCapacityForThirtyDays(){
        for(String name:List.of("propose","send_invite","invitation","gift","task","rank")){
            var layout=GuiLayout.parse(read(name));
            if(name.equals("task")){
                assertEquals(List.of("DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","DDDDDDDDH","#########"),layout.rows(),name);
                assertEquals(35,layout.dynamicSlots().size());
            }else if(name.equals("rank")){
                assertEquals(List.of("DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","DDDDDDDDR","#########"),layout.rows(),name);
                assertEquals(35,layout.dynamicSlots().size());
            }else{
                assertEquals(List.of("DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","#########"),layout.rows(),name);
                assertEquals(36,layout.dynamicSlots().size());
            }
            assertEquals(45,layout.size());
        }
    }
    @Test void taskPeriodFirstDefaultUpgradesToLastSlot(){
        var actual=read("task");
        actual.set("layout",List.of("DDDHDDDDD","DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","#########"));
        ConfigurationDefaults.merge(actual,read("task"),"gui/task.yml");
        assertEquals(List.of("DDDDDDDDD","DDDDDDDDD","DDDDDDDDD","DDDDDDDDH","#########"),actual.getStringList("layout"));
    }
    @Test void knownDefaultLayoutsUpgradeWhileCustomLayoutsAndIconsSurvive(){
        var oldList=List.of("DDDDDDDDD","#DDDDDDD#","#DDDDDDD#","#DDDDDDD#","#########");
        for(String name:ConfigurationManager.GUI_NAMES){
            var defaults=read(name);var actual=new YamlConfiguration();
            actual.set("layout",switch(name){case "main_menu"->List.of("#########","#IATARAB#","#ACQAAWA#","#AAGAUAA#","#########");case "wedding_plan"->List.of("#########","#LAIAGAA#","#AAASACA#","#AAAAAAA#","#########");case "task"->oldList;default->oldList;});
            actual.set("icons.#.name","自定义分隔板");
            ConfigurationDefaults.merge(actual,defaults,"gui/"+name+".yml");
            assertEquals(defaults.getStringList("layout"),actual.getStringList("layout"),name);
            assertEquals("自定义分隔板",actual.getString("icons.#.name"));
            var custom=List.of("#########","#AAAAAAA#","#DAAAAAD#","#AAAAAAA#","#########");actual.set("layout",custom);
            ConfigurationDefaults.merge(actual,defaults,"gui/"+name+".yml");assertEquals(custom,actual.getStringList("layout"));
        }
    }
}
