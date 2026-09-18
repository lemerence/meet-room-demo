package com.example.meetroom.domain.model;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import com.example.meetroom.domain.exception.DomainRuleViolation;

/**
 * 不可变的预约时间段，按北京时间解释并统一到分钟精度。
 * @param start 北京时间开始时间，构造后精确到分钟且包含该时刻
 * @param end 北京时间结束时间，构造后精确到分钟且不包含该时刻
 */
public record ReservationPeriod(LocalDateTime start, LocalDateTime end) {
    /** 严格日期解析器：支持分钟、可选秒及最多九位小数；不修正非法日期。 */
    private static final DateTimeFormatter INPUT = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd HH:mm")
            .optionalStart().appendPattern(":ss")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
            .optionalEnd().toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    /** 先归一化时间精度，再校验顺序、最长时长和跨天限制。 */
    public ReservationPeriod {
        if (start == null || end == null) {
            throw new DomainRuleViolation("TIME_REQUIRED", "开始时间和结束时间不能为空");
        }
        // 秒和小数直接截去；顺序、时长和跨天判断都使用相同的分钟精度。
        start = start.truncatedTo(ChronoUnit.MINUTES);
        end = end.truncatedTo(ChronoUnit.MINUTES);
        if (!start.isBefore(end)) {
            throw new DomainRuleViolation("INVALID_TIME_ORDER", "开始时间必须早于结束时间");
        }
        if (Duration.between(start, end).compareTo(Duration.ofHours(4)) > 0) {
            throw new DomainRuleViolation("DURATION_EXCEEDED", "单次预约不得超过4小时");
        }
        // 次日零点是允许的最大日期边界：恰好等于零点不算违规跨天。
        if (end.isAfter(start.toLocalDate().plusDays(1).atStartOfDay())) {
            throw new DomainRuleViolation("CROSS_DAY_NOT_ALLOWED", "预约不得跨天，结束于次日零点除外");
        }
    }

    /** 解析两个合法日期时间字符串，并构造通过业务校验的时间段。 */
    public static ReservationPeriod parse(String start, String end) {
        return new ReservationPeriod(parseTime(start), parseTime(end));
    }

    /** 以左闭右开区间判断重叠，首尾衔接不视为冲突。 */
    public boolean overlaps(ReservationPeriod other) {
        // 使用严格不等式排除只在端点相接的两个预约。
        return start.isBefore(other.end) && end.isAfter(other.start);
    }

    /** 严格解析日期和可选秒、小数部分，拒绝非法输入后再由构造器截取精度。 */
    private static LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolation("TIME_REQUIRED", "预约时间不能为空");
        }
        try {
            // 先解析完整输入，不能通过截掉尾部来接受非法秒数或无效日期。
            return LocalDateTime.parse(value, INPUT);
        } catch (DateTimeParseException exception) {
            throw new DomainRuleViolation("INVALID_TIME_FORMAT", "预约时间格式不合法");
        }
    }
}
