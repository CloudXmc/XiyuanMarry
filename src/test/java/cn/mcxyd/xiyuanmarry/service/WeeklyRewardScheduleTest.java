package cn.mcxyd.xiyuanmarry.service;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeeklyRewardScheduleTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void sundayBeforeSettlementUsesPreviousSunday() {
        var local = ZonedDateTime.of(2026, 9, 27, 19, 59, 0, 0, ZONE);
        assertEquals("2026-09-20", RewardService.weeklyWeekKey(local, DayOfWeek.SUNDAY, 20));
    }

    @Test
    void sundayAtSettlementStartsCurrentWeek() {
        var local = ZonedDateTime.of(2026, 9, 27, 20, 0, 0, 0, ZONE);
        assertEquals("2026-09-27", RewardService.weeklyWeekKey(local, DayOfWeek.SUNDAY, 20));
    }

    @Test
    void weekdaysUseMostRecentSettlementDay() {
        var local = ZonedDateTime.of(2026, 9, 30, 12, 0, 0, 0, ZONE);
        assertEquals("2026-09-27", RewardService.weeklyWeekKey(local, DayOfWeek.SUNDAY, 20));
    }

    @Test
    void announcementMarkerIsStablePerBoardAndWeek() {
        assertEquals("bond:2026-09-27", RewardService.weeklyAnnouncementMarker("bond", "2026-09-27"));
    }
    @Test void daylightSavingTransitionKeepsLocalSettlementHour() {
        var schedule = new WeeklyRewardSchedule(ZoneId.of("America/New_York"), DayOfWeek.SUNDAY, 20);
        var previous = ZonedDateTime.parse("2026-03-01T20:00:00-05:00[America/New_York]");
        var next = schedule.nextAfter(previous);
        assertEquals(20, next.getHour());
        assertEquals(167, java.time.Duration.between(previous, next).toHours());
    }
    @Test void firstActivationBetweenDeadlinesWaitsForNextDeadline() {
        var schedule = new WeeklyRewardSchedule(ZONE, DayOfWeek.SUNDAY, 20);
        var first = ZonedDateTime.of(2026, 9, 30, 12, 0, 0, 0, ZONE);
        assertEquals("2026-10-04T20:00+08:00[Asia/Shanghai]", schedule.nextOrSame(first.toInstant()).toString());
    }
}
