package com.example.meetroom.domain;

import java.time.LocalDateTime;

import com.example.meetroom.domain.model.ReservationPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证时间格式、精度归一化、时长、跨天与重叠语义的单元测试。 */
class ReservationPeriodTest {

    /** 验证秒和小数被直接截取，而非四舍五入。 */
    @Test
    void truncatesSecondsAndFractionsBeforeValidation() {
        var period = ReservationPeriod.parse("2026-09-18 15:30:59.999999999", "2026-09-18 19:30:59");
        assertThat(period.start()).isEqualTo(LocalDateTime.of(2026, 9, 18, 15, 30));
        assertThat(period.end()).isEqualTo(LocalDateTime.of(2026, 9, 18, 19, 30));
    }

    /** 验证合法时段、恰好四小时及结束于次日零点的例外。 */
    @ParameterizedTest
    @CsvSource({
            "2026-09-18 15:30,2026-09-18 15:31",
            "2026-09-18 15:30,2026-09-18 19:30",
            "2026-09-18 20:00,2026-09-19 00:00",
            "2026-09-18 23:00,2026-09-19 00:00:59",
            "2026-09-19 00:00,2026-09-19 01:00"
    })
    void acceptsValidPeriodsIncludingMidnightException(String start, String end) {
        assertThat(ReservationPeriod.parse(start, end).start()).isBefore(ReservationPeriod.parse(start, end).end());
    }

    /** 验证归一化后的相等、倒序、超长和跨天时段均被拒绝。 */
    @ParameterizedTest
    @CsvSource({
            "2026-09-18 15:30,2026-09-18 15:30",
            "2026-09-18 15:30:01,2026-09-18 15:30:59",
            "2026-09-18 16:00,2026-09-18 15:30",
            "2026-09-18 15:30,2026-09-18 19:31",
            "2026-09-18 23:00,2026-09-19 00:01",
            "2026-09-18 23:00,2026-09-20 00:00"
    })
    void rejectsInvalidNormalizedPeriods(String start, String end) {
        assertThatThrownBy(() -> ReservationPeriod.parse(start, end)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 验证非法日期、秒数和尾随内容不会被精度截取掩盖。 */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "2026-02-30 15:30", "2026-09-18 24:00", "2026-09-18 15:60",
            "2026-09-18 15:30:60", "2026-09-18 15:30:xx", "2026-09-18 15:30garbage", "2026-09-18 15:30:00.1234567890"})
    void rejectsMalformedTimeBeforeTruncating(String invalid) {
        assertThatThrownBy(() -> ReservationPeriod.parse(invalid, "2026-09-18 16:00"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReservationPeriod.parse("2026-09-18 15:00", invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 验证直接构造时间段时两个端点都必须提供。 */
    @Test
    void rejectsMissingTypedEndpoints() {
        var time = LocalDateTime.of(2026, 9, 18, 15, 30);
        assertThatThrownBy(() -> new ReservationPeriod(null, time)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservationPeriod(time, null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 验证各类重叠在双向比较时一致，结束时刻不占用。 */
    @ParameterizedTest
    @CsvSource({"09:00,10:00,false", "11:00,12:00,false", "08:00,09:00,false",
            "10:00,11:00,true", "09:30,10:30,true", "10:30,11:30,true",
            "10:15,10:45,true", "09:30,11:30,true"})
    void checksOverlapSymmetricallyWithExclusiveEnd(String start, String end, boolean expected) {
        var existing = ReservationPeriod.parse("2026-09-18 10:00", "2026-09-18 11:00");
        var candidate = ReservationPeriod.parse("2026-09-18 " + start, "2026-09-18 " + end);
        assertThat(existing.overlaps(candidate)).isEqualTo(expected);
        assertThat(candidate.overlaps(existing)).isEqualTo(expected);
    }
}
