package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.repository.SqliteMarriageRepository;
import java.nio.file.Path;import java.util.UUID;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class TeleportCooldownLedgerTest {
    @TempDir Path dir;
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
