package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.model.RewardTicket;
import cn.mcxyd.xiyuanmarry.repository.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RewardClaimLedgerTest {
    @TempDir Path root;
    final Gson gson = new Gson();
    RewardTicket ticket() { return new RewardTicket(UUID.randomUUID(), UUID.randomUUID(), "couple", 1, 1, "COMMITTED", 7, 0, 50, 10, List.of()); }
    void save(DatabaseManager db, RewardTicket t) { db.use(r -> { r.put(RewardClaimLedger.INBOX, t.id().toString(), gson.toJson(t)); return null; }); }
    RewardTicket read(DatabaseManager db, UUID id) { return db.use(r -> gson.fromJson(r.get(RewardClaimLedger.INBOX, id.toString()), RewardTicket.class)); }
    @Test void completionCannotDeleteSameIdInReplacementDatabase() {
        try (var db = new DatabaseManager(DatabaseSettings.sqlite(root.resolve("old.db")))) {
            var t = ticket(); save(db, t); var ledger = new RewardClaimLedger(db);
            var old = ledger.reserve(t.recipient(), t.id(), 2);
            db.switchTo(DatabaseSettings.sqlite(root.resolve("new.db")));
            save(db, old.ticket());
            assertFalse(ledger.complete(old, 3));
            assertEquals("CLAIMING", read(db, t.id()).state());
        }
    }
    @Test void oldCompletionCannotConsumeNewAttemptAfterSafeRestore() {
        try (var db = new DatabaseManager(DatabaseSettings.sqlite(root.resolve("one.db")))) {
            var t = ticket(); save(db, t); var ledger = new RewardClaimLedger(db);
            var old = ledger.reserve(t.recipient(), t.id(), 2);
            assertTrue(ledger.restore(old, 3));
            var retry = ledger.reserve(t.recipient(), t.id(), 4);
            assertFalse(ledger.complete(old, 5));
            assertTrue(ledger.complete(retry, 6));
        }
    }
    @Test void duplicateReviewCannotUnfreezeCompletedOrReviewedTicket() {
        try (var db = new DatabaseManager(DatabaseSettings.sqlite(root.resolve("one.db")))) {
            var t = ticket(); save(db, t); var ledger = new RewardClaimLedger(db);
            var claim = ledger.reserve(t.recipient(), t.id(), 2);
            assertTrue(ledger.review(claim, 3));
            assertFalse(ledger.restore(claim, 4));
            assertEquals("REVIEW", read(db, t.id()).state());
        }
    }
    @Test void switchingAwayAndBackDoesNotReviveOldCallback() {
        var original = DatabaseSettings.sqlite(root.resolve("old.db"));
        try (var db = new DatabaseManager(original)) {
            var t = ticket(); save(db, t); var ledger = new RewardClaimLedger(db);
            var claim = ledger.reserve(t.recipient(), t.id(), 2);
            db.switchTo(DatabaseSettings.sqlite(root.resolve("new.db"))); db.switchTo(original);
            assertFalse(ledger.restore(claim, 3)); assertFalse(ledger.complete(claim, 3));
            assertEquals("CLAIMING", read(db, t.id()).state());
            assertThrows(RuleViolation.class, () -> ledger.reserve(t.recipient(), t.id(), 4));
        }
    }
    @Test void staleQueuedReservationCannotTouchReplacementDatabase() {
        try (var db = new DatabaseManager(DatabaseSettings.sqlite(root.resolve("old.db")))) {
            UUID generation = db.generation(); var t = ticket();
            db.switchTo(DatabaseSettings.sqlite(root.resolve("new.db"))); save(db, t);
            var ledger = new RewardClaimLedger(db);
            assertThrows(DatabaseManager.StaleGenerationException.class, () -> ledger.reserve(generation, t.recipient(), t.id(), 2));
            assertEquals("COMMITTED", read(db, t.id()).state());
        }
    }
    @Test void wrongRecipientAndDuplicateClaimsDoNotChangeTicket() {
        try (var db = new DatabaseManager(DatabaseSettings.sqlite(root.resolve("one.db")))) {
            var t = ticket(); save(db, t); var ledger = new RewardClaimLedger(db);
            assertNull(ledger.reserve(UUID.randomUUID(), t.id(), 2));
            assertEquals("COMMITTED", read(db, t.id()).state());
            var claim = ledger.reserve(t.recipient(), t.id(), 3);
            assertThrows(RuleViolation.class, () -> ledger.reserve(t.recipient(), t.id(), 4));
            assertTrue(ledger.complete(claim, 5)); assertNull(read(db, t.id()));
            assertTrue(db.<Boolean>use(r -> r.entries(RewardClaimLedger.TOKENS).isEmpty()));
        }
    }
    @Test void legacyClaimingWithoutTokenIsNotAutomaticallyRetried() {
        try (var db = new DatabaseManager(DatabaseSettings.sqlite(root.resolve("one.db")))) {
            var t = ticket().withState("CLAIMING", 2); save(db, t);
            assertThrows(RuleViolation.class, () -> new RewardClaimLedger(db).reserve(t.recipient(), t.id(), 3));
            assertEquals("CLAIMING", read(db, t.id()).state());
        }
    }
    @Test void transactionRollbackRemovesTicketReservationAndTokenTogether() {
        try (var db = new DatabaseManager(DatabaseSettings.sqlite(root.resolve("one.db")))) {
            var t = ticket(); save(db, t); var ledger = new RewardClaimLedger(db);
            assertThrows(IllegalStateException.class, () -> db.use(r -> r.transaction(tx -> {
                ledger.reserve(t.recipient(), t.id(), 2); throw new IllegalStateException("injected failure");
            })));
            assertEquals("COMMITTED", read(db, t.id()).state());
            assertTrue(db.<Boolean>use(r -> r.entries(RewardClaimLedger.TOKENS).isEmpty()));
        }
    }
    @Test void restartedProcessKeepsUncertainTicketFrozen() {
        var settings = DatabaseSettings.sqlite(root.resolve("restart.db")); var t = ticket();
        try (var db = new DatabaseManager(settings)) { save(db, t); new RewardClaimLedger(db).reserve(t.recipient(), t.id(), 2); }
        try (var db = new DatabaseManager(settings)) {
            assertEquals("CLAIMING", read(db, t.id()).state());
            assertThrows(RuleViolation.class, () -> new RewardClaimLedger(db).reserve(t.recipient(), t.id(), 3));
        }
    }
}
