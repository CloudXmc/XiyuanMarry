package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.model.RewardDefinition;
import cn.mcxyd.xiyuanmarry.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyRewardLedgerTest {
    @TempDir Path dir;
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");
    private final Map<Integer, RewardDefinition> rewards = Map.of(
            1, new RewardDefinition(1, 5000, 1000, List.of()),
            2, new RewardDefinition(2, 3000, 600, List.of()));
    private long at(String time) { return LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli(); }
    private WeeklyRewardLedger.Result scan(MarriageRepository r, String time) {
        return new WeeklyRewardLedger().settle(r, "bond", DayOfWeek.SUNDAY, 20, zone, rewards, exp -> 1, at(time));
    }
    private UUID couple(MarriageRepository r, int bond) {
        UUID one = UUID.randomUUID();
        assertTrue(r.createMarriage(one, UUID.randomUUID(), "NORMAL", at("2026-09-20T10:00:00")));
        r.addBond(one, bond);
        return one;
    }
    @Test void firstScanBeforeDeadlineDoesNotPayPreviousWeek() {
        try (var r = new SqliteMarriageRepository(dir.resolve("before.db"))) {
            couple(r, 100);
            assertFalse(scan(r, "2026-09-27T19:59:59").settled());
            assertTrue(r.entries("reward-inbox").isEmpty());
            assertTrue(scan(r, "2026-09-27T20:00:00").settled());
            assertEquals(2, r.entries("reward-inbox").size());
        }
    }
    @Test void rankChangesAndNewCouplesCannotReceiveAnotherRewardInSameWeek() {
        try (var r = new SqliteMarriageRepository(dir.resolve("changes.db"))) {
            couple(r, 100); UUID runnerUp = couple(r, 50);
            assertTrue(scan(r, "2026-09-27T20:00:00").settled());
            assertEquals(4, r.entries("reward-inbox").size());
            r.addBond(runnerUp, 1000);
            couple(r, 2000);
            assertFalse(scan(r, "2026-09-28T12:00:00").settled());
            assertEquals(4, r.entries("reward-inbox").size());
        }
    }
    @Test void reopeningDatabaseCannotPayAgainAndNextWeekCanSettle() {
        Path file = dir.resolve("restart.db");
        try (var r = new SqliteMarriageRepository(file)) {
            couple(r, 100);
            assertTrue(scan(r, "2026-09-27T20:00:00").settled());
        }
        try (var r = new SqliteMarriageRepository(file)) {
            assertFalse(scan(r, "2026-09-30T12:00:00").settled());
            assertEquals(2, r.entries("reward-inbox").size());
            assertTrue(scan(r, "2026-10-04T20:00:00").settled());
            assertEquals(4, r.entries("reward-inbox").size());
        }
    }
    @Test void rollbackRemovesBothTicketsAndCursorThenRetrySucceeds() {
        try (var r = new SqliteMarriageRepository(dir.resolve("rollback.db"))) {
            couple(r, 100);
            scan(r, "2026-09-27T19:59:00");
            String before = r.get(WeeklyRewardLedger.CURSORS, "bond");
            assertThrows(IllegalStateException.class, () -> r.transaction(tx -> {
                scan(tx, "2026-09-27T20:00:00");
                throw new IllegalStateException("模拟提交失败");
            }));
            assertTrue(r.entries("reward-inbox").isEmpty());
            assertTrue(r.entries(WeeklyRewardLedger.SETTLEMENTS).isEmpty());
            assertEquals(before, r.get(WeeklyRewardLedger.CURSORS, "bond"));
            assertTrue(scan(r, "2026-09-27T20:00:30").settled());
            assertEquals(2, r.entries("reward-inbox").size());
        }
    }
    @Test void sparseRankThreeConfigurationPaysActualThirdCouple() {
        try (var r = new SqliteMarriageRepository(dir.resolve("sparse.db"))) {
            couple(r, 300); couple(r, 200); UUID third = couple(r, 100);
            var sparse = Map.of(3, new RewardDefinition(3, 1500, 300, List.of("say {player}")));
            var result = new WeeklyRewardLedger().settle(r, "bond", DayOfWeek.SUNDAY, 20, zone, sparse, x -> 1, at("2026-09-27T20:00:00"));
            assertEquals(1, result.winners().size());
            assertEquals(3, result.winners().getFirst().rank());
            assertEquals(r.findByPlayer(third).id(), result.winners().getFirst().relationship());
            assertEquals(2, result.tickets().size());
            assertTrue(result.tickets().stream().allMatch(t -> t.rank() == 3 && t.money() == 1500 && t.experience() == 300));
        }
    }
    @Test void emptySettlementRemainsClosedWhenNewPlayersJoin() {
        try (var r = new SqliteMarriageRepository(dir.resolve("empty.db"))) {
            assertTrue(scan(r, "2026-09-27T20:00:00").settled());
            couple(r, 100);
            assertFalse(scan(r, "2026-09-27T20:00:30").settled());
            assertTrue(r.entries("reward-inbox").isEmpty());
        }
    }
    @Test void delayedRestartSettlesOnlyLatestPeriodUsingEligibleCurrentData() {
        try (var r = new SqliteMarriageRepository(dir.resolve("catchup.db"))) {
            couple(r, 100);
            assertFalse(scan(r, "2026-09-27T19:59:00").settled());
            var result = scan(r, "2026-10-13T12:00:00");
            assertTrue(result.settled());
            assertEquals("2026-10-11", result.week());
            assertEquals(2, r.entries("reward-inbox").size());
            assertEquals(1, r.entries(WeeklyRewardLedger.SETTLEMENTS).size());
        }
    }
    @Test void legacyIssuedMarkerClosesPeriodWithoutChangingOldTicket() {
        try (var r = new SqliteMarriageRepository(dir.resolve("legacy.db"))) {
            UUID one = couple(r, 100);
            String oldKey = "weekly:2026-09-27:1:" + r.findByPlayer(one).id() + ":" + one;
            r.put("reward-issued", oldKey, "old-ticket");
            r.put("reward-inbox", "old-ticket", "preserved");
            var result = scan(r, "2026-09-27T20:00:00");
            assertTrue(result.legacyDetected());
            assertTrue(result.tickets().isEmpty());
            assertEquals(Map.of("old-ticket", "preserved"), r.entries("reward-inbox"));
            assertEquals("old-ticket", r.get("reward-issued", oldKey));
            assertFalse(scan(r, "2026-09-27T20:00:30").settled());
        }
    }
    @Test void concurrentScansHaveOnlyOneCommittedSettlement() throws Exception {
        try (var r = new SqliteMarriageRepository(dir.resolve("concurrent.db"));
             var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            couple(r, 100);
            var jobs = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 8; i++) jobs.add(executor.submit(() -> scan(r, "2026-09-27T20:00:00").settled()));
            int settled = 0;
            for (var job : jobs) if (job.get(10, java.util.concurrent.TimeUnit.SECONDS)) settled++;
            assertEquals(1, settled);
            assertEquals(2, r.entries("reward-inbox").size());
        }
    }
    @Test void scheduleChangeDoesNotReplaySettledPeriodAndClockRollbackDoesNotPay() {
        try (var r = new SqliteMarriageRepository(dir.resolve("clock.db"))) {
            couple(r, 100);
            assertTrue(scan(r, "2026-09-27T20:00:00").settled());
            var result = new WeeklyRewardLedger().settle(r, "bond", DayOfWeek.SUNDAY, 21, zone, rewards, x -> 1, at("2026-09-27T21:00:00"));
            assertFalse(result.settled());
            assertFalse(scan(r, "2026-09-20T20:00:00").settled());
            assertEquals(2, r.entries("reward-inbox").size());
        }
    }
    @Test void prunedSnapshotsDoNotEraseMonotonicCursor() {
        try (var r = new SqliteMarriageRepository(dir.resolve("prune.db"))) {
            couple(r, 100);
            scan(r, "2026-09-27T20:00:00");
            scan(r, "2027-03-07T20:00:00");
            assertEquals(1, r.entries(WeeklyRewardLedger.SETTLEMENTS).size());
            assertFalse(scan(r, "2026-09-27T20:00:00").settled());
            assertEquals(4, r.entries("reward-inbox").size());
        }
    }
    @Test void claimedTicketIsNotRecreatedByRepeatedScan() {
        try (var r = new SqliteMarriageRepository(dir.resolve("claimed.db"))) {
            couple(r, 100);
            var result = scan(r, "2026-09-27T20:00:00");
            for (var ticket : result.tickets()) r.remove("reward-inbox", ticket.id().toString());
            assertFalse(scan(r, "2026-09-28T12:00:00").settled());
            assertTrue(r.entries("reward-inbox").isEmpty());
        }
    }
    @Test void activationBeforeDeadlineStillPaysWhenFirstAsyncScanRunsLate() {
        try (var r = new SqliteMarriageRepository(dir.resolve("activation.db"))) {
            couple(r, 100);
            var result = new WeeklyRewardLedger().settle(r, "bond", DayOfWeek.SUNDAY, 20, zone, rewards,
                    x -> 1, at("2026-09-27T20:00:01"), at("2026-09-27T19:59:59"));
            assertTrue(result.settled());
            assertEquals(2, r.entries("reward-inbox").size());
        }
    }
}
