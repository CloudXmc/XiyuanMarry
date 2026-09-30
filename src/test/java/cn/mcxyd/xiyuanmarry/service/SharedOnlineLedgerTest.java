package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.repository.SqliteMarriageRepository;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class SharedOnlineLedgerTest {
    @TempDir Path dir;
    @Test void duplicatesAndRestartCannotRepeatOnlineOrDailyAward() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();String id;var ledger=new SharedOnlineLedger();
        try(var r=new SqliteMarriageRepository(dir.resolve("online.db"))){
            r.createMarriage(a,b,"NORMAL",1000);id=r.findByPlayer(a).id();
            var first=ledger.record(r,a,id,1000,1001,"2026-09-26",10,8);
            assertEquals(1,first.seconds());assertEquals(8,first.bond());
            assertEquals(0,ledger.record(r,a,id,1000,1001,"2026-09-26",10,8).seconds());
        }
        try(var r=new SqliteMarriageRepository(dir.resolve("online.db"))){
            ledger.record(r,a,id,1000,1001,"2026-09-26",10,8);
            ledger.record(r,a,id,1001,1002,"2026-09-26",10,8);
            assertEquals(2,r.findByPlayer(a).sharedSeconds());assertEquals(8,r.findByPlayer(a).bond());
        }
    }
    @Test void hourBoundaryAndNextDateAreTransactional() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();var ledger=new SharedOnlineLedger();
        try(var r=new SqliteMarriageRepository(dir.resolve("hour.db"))){
            r.createMarriage(a,b,"NORMAL",1000);var id=r.findByPlayer(a).id();r.addOnline(a,3599);
            ledger.record(r,a,id,1000,1001,"2026-09-26",10,8);
            assertEquals(3600,r.findByPlayer(a).sharedSeconds());assertEquals(18,r.findByPlayer(a).bond());
            ledger.record(r,a,id,1001,1002,"2026-09-27",10,8);
            assertEquals(26,r.findByPlayer(a).bond());
            ledger.record(r,a,id,1002,1003,"2026-09-26",10,8);
            assertEquals(26,r.findByPlayer(a).bond());
        }
    }
    @Test void oldRelationshipOrLongGapCannotAward() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();var ledger=new SharedOnlineLedger();
        try(var r=new SqliteMarriageRepository(dir.resolve("invalid.db"))){
            r.createMarriage(a,b,"NORMAL",1000);var id=r.findByPlayer(a).id();
            ledger.record(r,a,"old",1000,1001,"2026-09-26",10,8);
            ledger.record(r,a,id,1000,1301,"2026-09-26",10,8);
            assertEquals(0,r.findByPlayer(a).sharedSeconds());assertEquals(0,r.findByPlayer(a).bond());
        }
    }
    @Test void malformedOnlineLedgerDoesNotAwardOrThrow(){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();var ledger=new SharedOnlineLedger();
        try(var r=new SqliteMarriageRepository(dir.resolve("malformed-online.db"))){
            r.createMarriage(a,b,"NORMAL",1000);var id=r.findByPlayer(a).id();
            r.put("shared-online",id,"not-json");
            var result=assertDoesNotThrow(() -> ledger.record(r,a,id,1000,1001,"2026-09-26",10,8));
            assertEquals(0,result.seconds());assertEquals(0,result.bond());
            assertEquals(0,r.findByPlayer(a).sharedSeconds());
        }
    }
}
