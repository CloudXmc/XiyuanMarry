package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.repository.SqliteMarriageRepository;
import java.nio.file.Path;import java.util.UUID;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class TeleportCooldownLedgerTest {
    @TempDir Path dir;
    @Test void cooldownCountsCeilingSecondsFromPersistedDeadline(){
        try(var r=new SqliteMarriageRepository(dir.resolve("remaining.db"))){
            var ledger=new TeleportCooldownLedger();var actor=UUID.randomUUID();
            var ticket=ledger.reserve(r,actor,1000,86400000);
            for(long millis:new long[]{1,999,1000,1001,59000,59999,60000,60001,125001,86400000}){
                var rule=assertThrows(RuleViolation.class,()->ledger.reserve(r,actor,ticket.until()-millis,1));
                long seconds=millis/1000+(millis%1000==0?0:1);
                assertEquals("teleport-cooldown",rule.key());
                assertArrayEquals(new Object[]{"minutes",seconds/60,"seconds",seconds%60,"remaining-seconds",seconds},rule.values());
                assertEquals(ticket.token(),r.get("teleport-tokens",actor.toString()));
                assertEquals(Long.toString(ticket.until()),r.get("teleport-cooldowns",actor.toString()));
            }
            assertEquals(ticket.until()+1000,ledger.reserve(r,actor,ticket.until(),1000).until());
        }
    }
    @Test void zeroCooldownAllowsImmediateNextRequestWithoutStaleRelease(){
        try(var r=new SqliteMarriageRepository(dir.resolve("zero.db"))){
            var ledger=new TeleportCooldownLedger();var actor=UUID.randomUUID();
            var old=ledger.reserve(r,actor,1000,0);var next=ledger.reserve(r,actor,1000,0);
            assertFalse(ledger.release(r,old));assertTrue(ledger.release(r,next));
        }
    }
    @Test void staleFailureCannotReleaseNewReservation() {
        try(var r=new SqliteMarriageRepository(dir.resolve("cooldown.db"))) {
            var ledger=new TeleportCooldownLedger();var actor=UUID.randomUUID();
            var old=ledger.reserve(r,actor,10,10);var current=ledger.reserve(r,actor,21,100);
            assertFalse(ledger.release(r,old));
            assertThrows(RuleViolation.class,()->ledger.reserve(r,actor,22,100));
            assertTrue(ledger.release(r,current));assertFalse(ledger.release(r,current));
        }
    }
    @Test void legacyTimestampSurvivesRestartWithoutMigration() {
        var actor=UUID.randomUUID();var file=dir.resolve("legacy.db");
        try(var r=new SqliteMarriageRepository(file)) {r.put("teleport-cooldowns",actor.toString(),"100");}
        try(var r=new SqliteMarriageRepository(file)) {
            var ledger=new TeleportCooldownLedger();
            assertThrows(RuleViolation.class,()->ledger.reserve(r,actor,99,10));
            assertEquals(110,ledger.reserve(r,actor,100,10).until());
        }
    }
}
