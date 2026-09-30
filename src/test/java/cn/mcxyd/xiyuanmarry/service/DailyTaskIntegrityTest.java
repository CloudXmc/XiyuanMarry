package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.SqliteMarriageRepository;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DailyTaskIntegrityTest {
    @TempDir Path root;
    final UUID one=UUID.randomUUID(),two=UUID.randomUUID();
    final Gson json=new Gson();
    final DailyTaskLedger ledger=new DailyTaskLedger();
    final TaskDefinition build=new TaskDefinition("build","PLACE_BLOCK","一起建造","*",3,20);
    final TaskDefinition biome=new TaskDefinition("explore","BIOME","一起探索","*",3,20);
    SqliteMarriageRepository repository(){
        var r=new SqliteMarriageRepository(root.resolve(UUID.randomUUID()+".db"));
        assertTrue(r.createMarriage(one,two,"NORMAL",1000));return r;
    }
    @Test void malformedTaskIsPreservedInsteadOfRecreatedOrRewarded(){
        try(var r=repository()){var marriage=r.findByPlayer(one);
            for(String raw:List.of("not-json","null","{}","[]")){
                r.put(DailyTaskLedger.BUCKET,marriage.id(),raw);
                assertNull(ledger.current(r,marriage,2000,build),raw);
                assertFalse(ledger.record(r,one,marriage.id(),2000,build,new CoupleTaskService.Event("PLACE_BLOCK","STONE",3)).rewarded());
                assertNull(ledger.current(r,marriage,86402000,build),"跨天也不能覆盖未知完成状态");
                assertEquals(raw,r.get(DailyTaskLedger.BUCKET,marriage.id()));assertEquals(0,r.findByPlayer(one).bond());
            }
        }
    }
    @Test void wrongRelationshipAndInconsistentCompletionRemainUntouched(){
        try(var r=repository()){var m=r.findByPlayer(one);
            for(var task:List.of(new DailyTask("other",0,build,1,false),new DailyTask(m.id(),-1,build,0,false),
                    new DailyTask(m.id(),0,build,4,true),new DailyTask(m.id(),0,build,3,false),new DailyTask(m.id(),0,build,2,true))){
                String raw=json.toJson(task);r.put(DailyTaskLedger.BUCKET,m.id(),raw);
                assertNull(ledger.current(r,m,2000,build));
                assertFalse(ledger.record(r,one,m.id(),2000,build,new CoupleTaskService.Event("PLACE_BLOCK","STONE",3)).rewarded());
                assertEquals(raw,r.get(DailyTaskLedger.BUCKET,m.id()));assertEquals(0,r.findByPlayer(one).bond());
            }
        }
    }
    @Test void missingPrimitiveFieldsDoNotDefaultToAnUncompletedTask(){
        try(var r=repository()){var m=r.findByPlayer(one);
            for(String field:List.of("daySerial","progress","completed")){
                var raw=JsonParser.parseString(json.toJson(new DailyTask(m.id(),0,build,0,false))).getAsJsonObject();raw.remove(field);
                r.put(DailyTaskLedger.BUCKET,m.id(),raw.toString());
                assertNull(ledger.current(r,m,2000,build),field);assertEquals(raw.toString(),r.get(DailyTaskLedger.BUCKET,m.id()));
            }
        }
    }
    @Test void corruptedCompletedTaskCannotEarnASecondRewardAfterRestart(){
        Path file=root.resolve("restart.db");String id,damaged;
        try(var r=new SqliteMarriageRepository(file)){
            r.createMarriage(one,two,"NORMAL",1000);id=r.findByPlayer(one).id();
            assertTrue(ledger.record(r,one,id,2000,build,new CoupleTaskService.Event("PLACE_BLOCK","STONE",3)).rewarded());
            var raw=JsonParser.parseString(r.get(DailyTaskLedger.BUCKET,id)).getAsJsonObject();raw.remove("definition");damaged=raw.toString();
            r.put(DailyTaskLedger.BUCKET,id,damaged);
        }
        try(var r=new SqliteMarriageRepository(file)){
            assertFalse(new DailyTaskLedger().record(r,two,id,3000,build,new CoupleTaskService.Event("PLACE_BLOCK","STONE",3)).rewarded());
            assertEquals(20,r.findByPlayer(one).bond());assertEquals(damaged,r.get(DailyTaskLedger.BUCKET,id));
        }
    }
    @Test void malformedBiomeWatermarksDoNotResetDeduplicationOrProgress(){
        try(var r=repository()){var m=r.findByPlayer(one);
            ledger.record(r,one,m.id(),2000,biome,new CoupleTaskService.Event("BIOME","minecraft:plains",1));
            String taskRaw=r.get(DailyTaskLedger.BUCKET,m.id());
            for(String raw:List.of("null","{}","{\"daySerial\":0}","{\"daySerial\":0,\"names\":null}",
                    "{\"daySerial\":0,\"names\":[null]}","{\"daySerial\":0,\"names\":[]}",
                    "{\"daySerial\":1,\"names\":[\"minecraft:plains\"]}")){
                r.put(DailyTaskLedger.BIOME_BUCKET,m.id(),raw);
                assertNull(ledger.current(r,m,3000,biome),raw);
                assertFalse(ledger.record(r,two,m.id(),3000,biome,new CoupleTaskService.Event("BIOME","minecraft:plains",1)).rewarded());
                assertEquals(taskRaw,r.get(DailyTaskLedger.BUCKET,m.id()));assertEquals(raw,r.get(DailyTaskLedger.BIOME_BUCKET,m.id()));
                assertEquals(0,r.findByPlayer(one).bond());
            }
        }
    }
    @Test void missingBiomeWatermarkWithRecordedProgressCannotRecount(){
        try(var r=repository()){var m=r.findByPlayer(one);
            ledger.record(r,one,m.id(),2000,biome,new CoupleTaskService.Event("BIOME","minecraft:plains",1));
            String raw=r.get(DailyTaskLedger.BUCKET,m.id());r.remove(DailyTaskLedger.BIOME_BUCKET,m.id());
            assertNull(ledger.current(r,m,3000,biome));
            assertFalse(ledger.record(r,one,m.id(),3000,biome,new CoupleTaskService.Event("BIOME","minecraft:plains",1)).rewarded());
            assertEquals(raw,r.get(DailyTaskLedger.BUCKET,m.id()));assertNull(r.get(DailyTaskLedger.BIOME_BUCKET,m.id()));
        }
    }
    @Test void validNewDayResetsBiomeSetButClockRollbackKeepsSnapshot(){
        try(var r=repository()){var m=r.findByPlayer(one);
            ledger.record(r,one,m.id(),2000,biome,new CoupleTaskService.Event("BIOME","minecraft:plains",1));
            var next=ledger.record(r,one,m.id(),86402000,biome,new CoupleTaskService.Event("BIOME","minecraft:plains",1));
            assertEquals(1,next.task().daySerial());assertEquals(1,next.task().progress());
            assertEquals(next.task(),ledger.current(r,m,3000,build));
        }
    }
    @Test void restoredValidTaskCanContinueAndAwardsOnce(){
        try(var r=repository()){var m=r.findByPlayer(one);
            r.put(DailyTaskLedger.BUCKET,m.id(),"null");assertNull(ledger.current(r,m,2000,build));
            r.put(DailyTaskLedger.BUCKET,m.id(),json.toJson(new DailyTask(m.id(),0,build,2,false)));
            assertTrue(ledger.record(r,one,m.id(),3000,build,new CoupleTaskService.Event("PLACE_BLOCK","STONE",1)).rewarded());
            assertFalse(ledger.record(r,two,m.id(),4000,build,new CoupleTaskService.Event("PLACE_BLOCK","STONE",1)).rewarded());
            assertEquals(20,r.findByPlayer(one).bond());
        }
    }
    @Test void damagedCoupleDoesNotAbortHealthyCoupleInSameBatch(){
        try(var r=repository()){var m=r.findByPlayer(one);UUID other=UUID.randomUUID();
            assertTrue(r.createMarriage(other,UUID.randomUUID(),"NORMAL",1000));var healthy=r.findByPlayer(other);
            r.put(DailyTaskLedger.BUCKET,m.id(),"not-json");
            r.transaction(tx->{
                assertFalse(ledger.record(tx,one,m.id(),2000,build,new CoupleTaskService.Event("PLACE_BLOCK","STONE",3)).rewarded());
                assertTrue(ledger.record(tx,other,healthy.id(),2000,build,new CoupleTaskService.Event("PLACE_BLOCK","STONE",3)).rewarded());return null;
            });
            assertEquals(0,r.findByPlayer(one).bond());assertEquals(20,r.findByPlayer(other).bond());
            assertEquals("not-json",r.get(DailyTaskLedger.BUCKET,m.id()));
        }
    }
    @Test void invalidEmbeddedDefinitionIsRejectedWithoutOverwriting(){
        try(var r=repository()){var m=r.findByPlayer(one);
            String raw="{\"coupleId\":\""+m.id()+"\",\"daySerial\":0,\"progress\":0,\"completed\":false,\"definition\":{"+
                    "\"id\":\"bad\",\"type\":\"PLACE_BLOCK\",\"name\":\"\",\"selector\":null,\"target\":0,\"bondReward\":-1}}";
            r.put(DailyTaskLedger.BUCKET,m.id(),raw);
            assertNull(ledger.current(r,m,2000,build));
            assertFalse(ledger.record(r,one,m.id(),2000,build,new CoupleTaskService.Event("PLACE_BLOCK","STONE",3)).rewarded());
            assertEquals(raw,r.get(DailyTaskLedger.BUCKET,m.id()));
        }
    }
    @Test void nullEventValueCannotCrashOrAdvanceSpecificSelector(){
        var selected=new TaskDefinition("specific","PLACE_BLOCK","指定方块","STONE",2,20);
        try(var r=new SqliteMarriageRepository(root.resolve("null-event.db"))){
            r.createMarriage(one,two,"NORMAL",1000);String id=r.findByPlayer(one).id();
            assertDoesNotThrow(() -> ledger.record(r,one,id,2000,selected,new CoupleTaskService.Event("PLACE_BLOCK",null,1)));
            assertEquals(0,ledger.current(r,r.findByPlayer(one),2000,selected).progress());
        }
    }
    @Test void nullEventOrDefinitionIsIgnoredWithoutCreatingTaskState(){
        try(var r=new SqliteMarriageRepository(root.resolve("null-input.db"))){
            r.createMarriage(one,two,"NORMAL",1000);String id=r.findByPlayer(one).id();
            assertDoesNotThrow(() -> ledger.record(r,one,id,2000,null,null));
            assertNull(r.get(DailyTaskLedger.BUCKET,id));
            assertFalse(ledger.record(r,one,id,2000,null,new CoupleTaskService.Event("PLACE_BLOCK","STONE",1)).rewarded());
            assertNull(r.get(DailyTaskLedger.BUCKET,id));
        }
    }
}
