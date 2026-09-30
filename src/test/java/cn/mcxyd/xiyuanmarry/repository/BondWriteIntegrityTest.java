package cn.mcxyd.xiyuanmarry.repository;

import cn.mcxyd.xiyuanmarry.model.TaskDefinition;
import cn.mcxyd.xiyuanmarry.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 使用真实 SQLite 检查整数边界与任务奖励事务，不用模拟仓库掩盖数值转换。 */
class BondWriteIntegrityTest {
    @TempDir Path root;
    final UUID a=UUID.randomUUID(),b=UUID.randomUUID();
    @Test void exactMaximumIsValidButCurrentBondOverflowChangesNeitherCounter(){
        try(var r=new SqliteMarriageRepository(root.resolve("current.db"))){
            r.createMarriage(a,b,"NORMAL",1000);r.setBond(a,Long.MAX_VALUE-10);
            assertTrue(r.addBond(a,10));var before=r.findByPlayer(a);
            assertEquals(Long.MAX_VALUE,before.bond());assertEquals(10,before.totalBond());
            assertFalse(r.addBond(a,1));assertEquals(before,r.findByPlayer(a));
        }
    }
    @Test void totalBondOverflowCannotSlipThroughAfterAdministratorReducesCurrentBond(){
        try(var r=new SqliteMarriageRepository(root.resolve("total.db"))){
            r.createMarriage(a,b,"NORMAL",1000);assertTrue(r.addBond(a,Long.MAX_VALUE));r.setBond(a,0);
            var before=r.findByPlayer(a);assertFalse(r.addBond(a,1));assertEquals(before,r.findByPlayer(a));
        }
    }
    @Test void rejectedTaskRewardRollsBackProgressAndCanLaterCompleteOnce(){
        try(var r=new SqliteMarriageRepository(root.resolve("task.db"))){
            r.createMarriage(a,b,"NORMAL",1000);r.setBond(a,Long.MAX_VALUE-5);
            var ledger=new DailyTaskLedger();var task=new TaskDefinition("build","PLACE_BLOCK","共筑爱巢","STONE",3,20);
            String relation=r.findByPlayer(a).id();
            ledger.record(r,a,relation,2000,task,new CoupleTaskService.Event("PLACE_BLOCK","STONE",2));
            String before=r.get(DailyTaskLedger.BUCKET,relation);var marriage=r.findByPlayer(a);
            assertThrows(IllegalStateException.class,()->ledger.record(r,a,relation,3000,task,new CoupleTaskService.Event("PLACE_BLOCK","STONE",1)));
            assertEquals(before,r.get(DailyTaskLedger.BUCKET,relation));assertEquals(marriage,r.findByPlayer(a));
            r.setBond(a,0);assertTrue(ledger.record(r,b,relation,4000,task,new CoupleTaskService.Event("PLACE_BLOCK","STONE",1)).rewarded());
            assertFalse(ledger.record(r,a,relation,5000,task,new CoupleTaskService.Event("PLACE_BLOCK","STONE",1)).rewarded());
            assertEquals(20,r.findByPlayer(a).bond());assertEquals(20,r.findByPlayer(a).totalBond());
        }
    }
}
