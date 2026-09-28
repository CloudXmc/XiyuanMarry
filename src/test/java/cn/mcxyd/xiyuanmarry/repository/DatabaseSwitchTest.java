package cn.mcxyd.xiyuanmarry.repository;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;import java.util.UUID;import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class DatabaseSwitchTest {
    @TempDir Path root;
    @Test void rejectedCandidateClosesOnlyItsOwnPool() {
        try(var db=new DatabaseManager(DatabaseSettings.sqlite(root.resolve("old.db")))){
            var generation=db.generation();var old=db.use(r->(JdbcMarriageRepository)r);
            var candidate=new java.util.concurrent.atomic.AtomicReference<JdbcMarriageRepository>();
            assertThrows(IllegalArgumentException.class,()->db.switchTo(DatabaseSettings.sqlite(root.resolve("candidate.db")),r->{
                candidate.set((JdbcMarriageRepository)r);assertEquals(generation,db.generation());assertFalse(old.closed());
                throw new IllegalArgumentException("候选业务数据无效");
            }));
            assertNotNull(candidate.get());assertTrue(candidate.get().closed());assertFalse(old.closed());assertEquals(generation,db.generation());
        }
    }
    @Test void unchangedDatabaseStillValidatesItsSnapshot() {
        var settings=DatabaseSettings.sqlite(root.resolve("same.db"));
        try(var db=new DatabaseManager(settings)){
            var generation=db.generation();var pool=db.use(r->(JdbcMarriageRepository)r);
            assertThrows(IllegalArgumentException.class,()->db.switchTo(settings,r->{throw new IllegalArgumentException("bad snapshot");}));
            assertEquals(generation,db.generation());assertFalse(pool.closed());
            var result=db.switchTo(settings,r->r.findAll().size());assertFalse(result.switched());assertEquals(0,result.value());
        }
    }
    @Test void shutdownDuringValidationRejectsAndClosesCandidate() {
        try(var db=new DatabaseManager(DatabaseSettings.sqlite(root.resolve("old.db")))){
            var candidate=new java.util.concurrent.atomic.AtomicReference<JdbcMarriageRepository>();
            assertThrows(IllegalStateException.class,()->db.switchTo(DatabaseSettings.sqlite(root.resolve("new.db")),r->{candidate.set((JdbcMarriageRepository)r);db.close();return null;}));
            assertTrue(candidate.get().closed());assertThrows(IllegalStateException.class,db::generation);
        }
    }
    @Test void switchCreatesEmptyDatabaseAndPreservesOldData() {
        var first=DatabaseSettings.sqlite(root.resolve("first.db"));var second=DatabaseSettings.sqlite(root.resolve("second.db"));
        UUID one=UUID.randomUUID(),two=UUID.randomUUID();
        try(var db=new DatabaseManager(first)){
            db.use(r->r.createMarriage(one,two,"NORMAL",1));assertTrue(db.switchTo(second));
            assertTrue(db.<Boolean>use(r->r.findAll().isEmpty()));assertTrue(db.switchTo(first));
            assertNotNull(db.use(r->r.findByPlayer(one)));assertFalse(db.switchTo(first));
        }
    }
    @Test void failedSwitchKeepsOriginalDatabase()throws Exception {
        try(var db=new DatabaseManager(DatabaseSettings.sqlite(root.resolve("good.db")))){
            Path blocked=root.resolve("file");Files.writeString(blocked,"not a directory");
            assertThrows(RuntimeException.class,()->db.switchTo(DatabaseSettings.sqlite(blocked.resolve("bad.db"))));
            assertTrue(db.<Boolean>use(r->r.createMarriage(UUID.randomUUID(),UUID.randomUUID(),"NORMAL",1)));
        }
    }
    @Test void oldGenerationStaysAliveUntilActiveLeaseFinishes()throws Exception {
        try(var db=new DatabaseManager(DatabaseSettings.sqlite(root.resolve("old.db")))){
            var started=new CountDownLatch(1);var release=new CountDownLatch(1);
            var executor=Executors.newSingleThreadExecutor();
            try{
                var work=executor.submit(()->db.use(r->{started.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("timeout");}catch(InterruptedException e){throw new RuntimeException(e);}return r.createMarriage(UUID.randomUUID(),UUID.randomUUID(),"NORMAL",1);}));
                assertTrue(started.await(5,TimeUnit.SECONDS));db.switchTo(DatabaseSettings.sqlite(root.resolve("new.db")));
                release.countDown();assertTrue(work.get(5,TimeUnit.SECONDS));assertTrue(db.<Boolean>use(r->r.findAll().isEmpty()));
            }finally{release.countDown();executor.shutdownNow();}
        }
    }
}
