package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DailyTaskLedgerTest {
    @TempDir Path dir;
    private final UUID one = UUID.randomUUID(), two = UUID.randomUUID();
    private final TaskDefinition task = new TaskDefinition("build", "PLACE_BLOCK", "共筑爱巢", "STONE", 3, 20);
    private final DailyTaskLedger ledger = new DailyTaskLedger();
    private DailyTaskLedger.Outcome add(MarriageRepository r, String id, long now, long amount) {
        return ledger.record(r, one, id, now, task, new CoupleTaskService.Event("PLACE_BLOCK", "STONE", amount));
    }
    @Test void progressSurvivesRestartAndRewardsOnceForBothPartners() {
        Path file = dir.resolve("tasks.db"); String id;
        try (var r = new SqliteMarriageRepository(file)) {
            r.createMarriage(one, two, "NORMAL", 1000); id = r.findByPlayer(one).id();
            assertEquals(2, add(r, id, 2000, 2).task().progress());
            assertEquals(0, r.findByPlayer(one).bond());
        }
        try (var r = new SqliteMarriageRepository(file)) {
            assertTrue(add(r, id, 3000, 1).rewarded());
            assertFalse(ledger.record(r, two, id, 4000, task, new CoupleTaskService.Event("PLACE_BLOCK", "STONE", 100)).rewarded());
            assertEquals(20, r.findByPlayer(one).bond());
        }
    }
    @Test void newCycleGetsNewKeyAndRemarriageRejectsStaleEvents() {
        try (var r = new SqliteMarriageRepository(dir.resolve("cycles.db"))) {
            r.createMarriage(one, two, "NORMAL", 1000); String id = r.findByPlayer(one).id();
            var first = add(r, id, 2000, 3);
            var next = add(r, id, 1000 + 30L * 86400000, 3);
            assertTrue(first.rewarded()); assertTrue(next.rewarded());
            assertNotEquals(first.task().key(), next.task().key());
            assertEquals(1, next.task().cycleDay()); assertEquals(40, r.findByPlayer(one).bond());
            r.deleteMarriage(one); r.createMarriage(one, two, "NORMAL", 3000000000L);
            assertNull(add(r, id, 3000000001L, 3).task());
            assertEquals(0, r.findByPlayer(one).bond());
        }
    }
    @Test void wrongEventAndSelectorDoNotAdvance() {
        try (var r = new SqliteMarriageRepository(dir.resolve("filter.db"))) {
            r.createMarriage(one, two, "NORMAL", 1000); String id = r.findByPlayer(one).id();
            assertEquals(0, ledger.record(r, one, id, 2000, task, new CoupleTaskService.Event("BREAK_BLOCK", "STONE", 100)).task().progress());
            assertEquals(0, ledger.record(r, one, id, 2000, task, new CoupleTaskService.Event("PLACE_BLOCK", "DIRT", 100)).task().progress());
            assertEquals(3, add(r, id, 3000, Long.MAX_VALUE).task().progress());
        }
    }
    @Test void outerTransactionRollsBackProgressAndRewardTogether() {
        try (var r = new SqliteMarriageRepository(dir.resolve("rollback.db"))) {
            r.createMarriage(one, two, "NORMAL", 1000); String id = r.findByPlayer(one).id();
            assertThrows(IllegalStateException.class, () -> r.transaction(tx -> {add(tx, id, 2000, 3); throw new IllegalStateException("模拟提交失败");}));
            assertEquals(0, r.findByPlayer(one).bond());
            assertTrue(add(r, id, 2000, 3).rewarded());
            assertEquals(20, r.findByPlayer(one).bond());
        }
    }
    @Test void sameBiomeFromBothPartnersAndRestartCountsOnce(){
        var definition=new TaskDefinition("explore","BIOME","发现新群系","*",2,20);String id;
        try(var r=new SqliteMarriageRepository(dir.resolve("biomes.db"))){
            r.createMarriage(one,two,"NORMAL",1000);id=r.findByPlayer(one).id();
            ledger.record(r,one,id,2000,definition,new CoupleTaskService.Event("BIOME","minecraft:plains",1));
            assertEquals(1,ledger.record(r,two,id,3000,definition,new CoupleTaskService.Event("BIOME","MINECRAFT:PLAINS",1)).task().progress());
        }
        try(var r=new SqliteMarriageRepository(dir.resolve("biomes.db"))){
            assertEquals(1,ledger.record(r,one,id,4000,definition,new CoupleTaskService.Event("BIOME","minecraft:plains",1)).task().progress());
            assertTrue(ledger.record(r,two,id,5000,definition,new CoupleTaskService.Event("BIOME","minecraft:forest",1)).rewarded());
            assertEquals(20,r.findByPlayer(one).bond());
        }
    }
}
