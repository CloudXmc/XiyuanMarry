package cn.mcxyd.xiyuanmarry.gui;
import cn.mcxyd.xiyuanmarry.config.TaskCatalog;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.service.MonsterTaskRandomizer;
import java.util.*;
/** 今天使用数据库中的冻结定义，未来使用新目录；过去只标已结束，不虚构完成记录。 */
public final class TaskMenuModel {
    public record Row(int day,long cycle,TaskDefinition definition,long progress,String state) {}
    public static List<Row> rows(DailyTask today,TaskCatalog catalog) {
        var rows=new ArrayList<Row>();
        for(int day=1;day<=30;day++) {
            boolean current=day==today.cycleDay();
            String state=current?(today.completed()?"completed":"active"):(day<today.cycleDay()?"past":"locked");
            long serial=today.daySerial()-today.daySerial()%30+day-1;
            TaskDefinition definition=current?today.definition():MonsterTaskRandomizer.resolve(catalog.forDay(day-1),today.coupleId(),serial);
            rows.add(new Row(day,today.daySerial()/30+1,definition,
                current?today.progress():0,state));
        }
        return List.copyOf(rows);
    }
}
