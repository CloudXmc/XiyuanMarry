package cn.mcxyd.xiyuanmarry.service;
import java.time.*;
import java.util.Objects;

/** 仅处理日期和时区，不接触 Bukkit 实时对象。 */
public record WeeklyRewardSchedule(ZoneId zone, DayOfWeek day, int hour) {
    public WeeklyRewardSchedule {
        Objects.requireNonNull(zone);
        Objects.requireNonNull(day);
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("周榜小时范围为 0-23");
    }
    public ZonedDateTime previousOrSame(Instant now) {
        ZonedDateTime local = now.atZone(zone);
        LocalDate date = local.toLocalDate().minusDays(Math.floorMod(local.getDayOfWeek().getValue() - day.getValue(), 7));
        ZonedDateTime boundary = date.atTime(hour, 0).atZone(zone);
        return boundary.toInstant().isAfter(now) ? date.minusWeeks(1).atTime(hour, 0).atZone(zone) : boundary;
    }
    public ZonedDateTime nextOrSame(Instant now) {
        ZonedDateTime previous = previousOrSame(now);
        return previous.toInstant().equals(now) ? previous : nextAfter(previous);
    }
    public ZonedDateTime nextAfter(ZonedDateTime boundary) {
        // 以本地日期加一周，跨夏令时仍保持配置的当地小时。
        return boundary.toLocalDate().plusWeeks(1).atTime(hour, 0).atZone(zone);
    }
    public String signature() { return zone.getId() + ":" + day.name() + ":" + hour; }
}
