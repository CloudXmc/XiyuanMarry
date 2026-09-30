package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class DailyTaskCycleCoverageTest {
    @TempDir Path root;
    final UUID one=UUID.randomUUID(),two=UUID.randomUUID();
    final DailyTaskLedger ledger=new DailyTaskLedger();
    final TaskDefinition build=new TaskDefinition("build","PLACE_BLOCK","共同建造","*",2,20);
    static Stream<String> taskTypes() { return CoupleTaskService.TYPES.stream(); }
    @ParameterizedTest @MethodSource("taskTypes")
    void allEighteenTypesAcceptBothPartnersAndRewardOnce(String type) {
        try(var r=new SqliteMarriageRepository(root.resolve("task.db"))) {
            r.createMarriage(one,two,"NORMAL",1000);String id=r.findByPlayer(one).id();
            var definition=new TaskDefinition(type,type,"任务","*",2,20);
            String first=type.equals("BIOME")?"minecraft:plains":"STONE";
            String second=type.equals("BIOME")?"minecraft:forest":"STONE";
            assertEquals(1,ledger.record(r,one,id,2000,definition,new CoupleTaskService.Event(type,first,1)).task().progress());
            assertTrue(ledger.record(r,two,id,3000,definition,new CoupleTaskService.Event(type,second,1)).rewarded());
            assertFalse(ledger.record(r,one,id,4000,definition,new CoupleTaskService.Event(type,second,100)).rewarded());
            assertEquals(20,r.findByPlayer(two).bond());
        }
    }
    @Test void dayBoundaryAndThirtiethDayUseMarriageLocalCalendarDate() {
        ZoneId zone=ZoneId.of("Asia/Shanghai");
        long married=ZonedDateTime.of(2026,9,29,23,58,0,0,zone).toInstant().toEpochMilli();
        assertEquals(0,DailyTaskLedger.daySerial(married,ZonedDateTime.of(2026,9,29,23,59,59,0,zone).toInstant().toEpochMilli(),zone));
        assertEquals(1,DailyTaskLedger.daySerial(married,ZonedDateTime.of(2026,9,30,0,0,0,0,zone).toInstant().toEpochMilli(),zone));
        assertEquals(1,DailyTaskLedger.daySerial(married,ZonedDateTime.of(2026,9,30,23,59,59,0,zone).toInstant().toEpochMilli(),zone));
        assertEquals(30,DailyTaskLedger.daySerial(married,ZonedDateTime.of(2026,10,29,0,0,0,0,zone).toInstant().toEpochMilli(),zone));
        assertEquals(0,DailyTaskLedger.daySerial(married,married-1));
    }
    @Test void assignedDefinitionAndRewardStayStableAfterConfigChanges() {
        try(var r=new SqliteMarriageRepository(root.resolve("reload.db"))) {
            r.createMarriage(one,two,"NORMAL",1000);var marriage=r.findByPlayer(one);
            ledger.record(r,one,marriage.id(),2000,build,new CoupleTaskService.Event("PLACE_BLOCK","STONE",1));
            var replacement=new TaskDefinition("kill","KILL_MONSTER","战斗","*",1,999);
            var finished=ledger.record(r,two,marriage.id(),3000,replacement,new CoupleTaskService.Event("PLACE_BLOCK","STONE",1));
            assertEquals(build,finished.task().definition());assertTrue(finished.rewarded());assertEquals(20,r.findByPlayer(one).bond());
        }
    }
    @Test void expiredDivorceRejectsQueuedEventAndPendingCoolingStillAllowsTasks() {
        try(var r=new SqliteMarriageRepository(root.resolve("divorce.db"))) {
            r.createMarriage(one,two,"NORMAL",1000);String id=r.findByPlayer(one).id();r.requestDivorce(one,3000);
            assertEquals(1,ledger.record(r,one,id,2999,build,new CoupleTaskService.Event("PLACE_BLOCK","STONE",1)).task().progress());
            assertNull(ledger.record(r,two,id,3000,build,new CoupleTaskService.Event("PLACE_BLOCK","STONE",1)).task());
            assertEquals(0,r.findByPlayer(one).bond());
        }
    }
    @Test void zeroNegativeAndClockRollbackCannotAwardAgain() {
        try(var r=new SqliteMarriageRepository(root.resolve("rollback.db"))) {
            r.createMarriage(one,two,"NORMAL",1000);String id=r.findByPlayer(one).id();
            for(long amount:new long[]{0,-1,Long.MIN_VALUE})
                assertEquals(0,ledger.record(r,one,id,2000,build,new CoupleTaskService.Event("PLACE_BLOCK","STONE",amount)).task().progress());
            assertTrue(ledger.record(r,one,id,86_401_000,build,new CoupleTaskService.Event("PLACE_BLOCK","STONE",2)).rewarded());
            assertFalse(ledger.record(r,one,id,2000,build,new CoupleTaskService.Event("PLACE_BLOCK","STONE",2)).rewarded());
            assertEquals(20,r.findByPlayer(one).bond());
        }
    }
}
