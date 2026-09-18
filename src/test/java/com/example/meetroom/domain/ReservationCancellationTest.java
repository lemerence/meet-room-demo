package com.example.meetroom.domain;

import java.time.LocalDateTime;
import java.util.List;

import com.example.meetroom.domain.exception.DomainRuleViolation;
import com.example.meetroom.domain.model.CancellationResult;
import com.example.meetroom.domain.model.MeetingRoom;
import com.example.meetroom.domain.model.Reservation;
import com.example.meetroom.domain.model.ReservationPeriod;
import com.example.meetroom.domain.model.ReservationStatus;
import com.example.meetroom.domain.service.ReservationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证取消资格、重复取消、审计保留和时段释放的领域单元测试。 */
class ReservationCancellationTest {
    /** 固定的合法预约时间段测试样本。 */
    private final ReservationPeriod period = ReservationPeriod.parse("2026-09-18 10:00", "2026-09-18 11:00");
    /** 固定的请求到达时间，保留业务资格判断所需精度。 */
    private final LocalDateTime arrival = LocalDateTime.of(2026, 9, 18, 9, 0);
    /** 固定会议室测试样本，容量为六人。 */
    private final MeetingRoom room = new MeetingRoom(1L, "会议室", 6);

    /** 创建取消测试所需的合法预约，所有时刻均由测试固定。 */
    private Reservation reservation() {
        return new ReservationPolicy((id, candidate) -> false)
                .create(room, "employee-1", "会议", 3, period, arrival, arrival.plusSeconds(1));
    }

    /** 验证开始前到达的取消请求，即使处理时已开始仍可取消，并使用实际操作时间审计。 */
    @Test
    void cancelsUsingArrivalBeforeStartEvenWhenProcessingAfterStart() {
        var reservation = reservation();
        var actualCancellation = period.start().plusSeconds(2).plusNanos(999);

        assertThat(reservation.cancel(period.start().minusNanos(1), actualCancellation)).isEqualTo(CancellationResult.CANCELLED);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.cancelledAt()).isEqualTo(period.start().plusSeconds(2));
        assertThat(reservation.subject()).isEqualTo("会议");
        assertThat(reservation.period()).isEqualTo(period);
        assertThat(reservation.createdAt()).isEqualTo(arrival.plusSeconds(1));
    }

    /** 验证恰好开始或之后到达的请求被拒绝，且不修改预约状态。 */
    @ParameterizedTest
    @ValueSource(longs = {0, 1, 1800, 3600, 7200})
    void rejectsArrivalAtOrAfterStartAndLeavesStateUnchanged(long seconds) {
        var reservation = reservation();
        assertThatThrownBy(() -> reservation.cancel(period.start().plusSeconds(seconds), period.end().plusHours(1)))
                .isInstanceOf(DomainRuleViolation.class)
                .satisfies(error -> assertThat(((DomainRuleViolation) error).code()).isEqualTo("RESERVATION_STARTED"));
        assertThat(reservation.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservation.cancelledAt()).isNull();
    }

    /** 验证重复取消优先于开始时间判断，并保留首次取消时间。 */
    @ParameterizedTest
    @ValueSource(longs = {-30, 0, 7200})
    void repeatedCancellationTakesPriorityAndPreservesFirstAuditTime(long seconds) {
        var reservation = reservation();
        reservation.cancel(arrival, arrival.plusSeconds(2));

        var result = reservation.cancel(period.start().plusSeconds(seconds), period.end().plusHours(2));

        assertThat(result).isEqualTo(CancellationResult.ALREADY_CANCELLED);
        assertThat(result.message()).isEqualTo("预约已经取消无需重复取消");
        assertThat(reservation.cancelledAt()).isEqualTo(arrival.plusSeconds(2));
    }

    /** 验证首次取消缺少必要时间时失败且状态不变。 */
    @Test
    void requiresExplicitCancellationTimesWithoutMutatingState() {
        var reservation = reservation();
        assertThatThrownBy(() -> reservation.cancel(null, arrival)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reservation.cancel(arrival, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservation.cancelledAt()).isNull();
    }

    /** 验证有效预约仅占用自身会议室的重叠区间，首尾衔接不占用。 */
    @Test
    void activeReservationOccupiesOnlyItsRoomAndOverlappingPeriod() {
        var reservation = reservation();
        assertThat(reservation.occupies(1L, period)).isTrue();
        assertThat(reservation.occupies(2L, period)).isFalse();
        assertThat(reservation.occupies(1L, ReservationPeriod.parse("2026-09-18 11:00", "2026-09-18 12:00"))).isFalse();
        assertThat(reservation.occupies(1L, ReservationPeriod.parse("2026-09-18 09:00", "2026-09-18 10:00"))).isFalse();
    }

    /** 验证取消前冲突被拒绝、取消后可重新预约，同时保留原记录。 */
    @Test
    void cancellationReleasesPeriodForAnotherReservation() {
        var existing = reservation();
        var records = List.of(existing);
        var policy = new ReservationPolicy((id, candidate) -> records.stream().anyMatch(r -> r.occupies(id, candidate)));
        assertThatThrownBy(() -> policy.create(room, "e2", "冲突会议", 1, period, arrival, arrival))
                .isInstanceOf(DomainRuleViolation.class);

        existing.cancel(arrival, arrival.plusSeconds(3));

        assertThat(existing.occupies(1L, period)).isFalse();
        assertThat(policy.create(room, "e2", "新会议", 1, period, arrival, arrival.plusSeconds(4)).status())
                .isEqualTo(ReservationStatus.ACTIVE);
        assertThat(records).containsExactly(existing);
    }

    /** 验证同一员工可以在相同时段预约不同会议室，不添加额外限制。 */
    @Test
    void sameEmployeeMayReserveAnotherRoomAtSameTime() {
        var existing = reservation();
        var policy = new ReservationPolicy(existing::occupies);
        var other = policy.create(new MeetingRoom(2L, "另一会议室", 6), "employee-1", "另一会议", 3,
                period, arrival, arrival);
        assertThat(other.meetingRoomId()).isEqualTo(2L);
    }
}
