package com.example.meetroom.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import com.example.meetroom.domain.exception.DomainRuleViolation;
import com.example.meetroom.domain.model.MeetingRoom;
import com.example.meetroom.domain.model.ReservationPeriod;
import com.example.meetroom.domain.model.ReservationStatus;
import com.example.meetroom.domain.service.ReservationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证创建预约规则及冲突查询契约，不依赖真实数据库。 */
class ReservationPolicyTest {
    /** 固定会议室测试样本，容量为六人。 */
    private final MeetingRoom room = new MeetingRoom(1L, "会议室", 6);
    /** 固定的合法预约时间段测试样本。 */
    private final ReservationPeriod period = ReservationPeriod.parse("2026-09-18 15:31", "2026-09-18 16:31");
    /** 固定北京时间测试时钟，不受实际运行时间影响。 */
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-18T07:30:20.123456789Z"), ZoneId.of("Asia/Shanghai"));
    /** 固定的请求到达时间，保留业务资格判断所需精度。 */
    private final LocalDateTime arrival = LocalDateTime.now(clock);
    /** 默认无冲突的创建规则服务，用于隔离验证其他领域规则。 */
    private final ReservationPolicy policy = new ReservationPolicy((roomId, candidate) -> false);

    /** 验证容量等值可预约、主题去空白，以及创建审计不复用请求到达时间。 */
    @Test
    void createsAtCapacityAndNormalizesSubjectWithSeparateAuditTime() {
        var createdAt = LocalDateTime.now(Clock.offset(clock, java.time.Duration.ofSeconds(2)));
        var reservation = policy.create(room, "employee-001", "  项目评审  ", 6, period, arrival, createdAt);
        assertThat(reservation.meetingRoomId()).isEqualTo(1L);
        assertThat(reservation.employeeId()).isEqualTo("employee-001");
        assertThat(reservation.subject()).isEqualTo("项目评审");
        assertThat(reservation.attendeeCount()).isEqualTo(6);
        assertThat(reservation.period()).isEqualTo(period);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservation.createdAt()).isEqualTo(LocalDateTime.of(2026, 9, 18, 15, 30, 22));
        assertThat(reservation.cancelledAt()).isNull();
    }

    /** 模拟处理延迟，验证创建资格仍以原请求到达时间判断。 */
    @Test
    void usesArrivalRatherThanProcessingTimeAfterLockWait() {
        var reservation = policy.create(room, "e1", "会议", 1, period, arrival, period.start().plusSeconds(5));
        assertThat(reservation.status()).isEqualTo(ReservationStatus.ACTIVE);
    }

    /** 验证会议室不存在时拒绝创建。 */
    @Test
    void rejectsMissingRoom() {
        assertCode("ROOM_NOT_FOUND", () -> policy.create(null, "e1", "会议", 1, period, arrival, arrival));
    }

    /** 验证空值、空串及纯空白主题均被拒绝。 */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "　"})
    void rejectsBlankSubject(String subject) {
        assertCode("INVALID_SUBJECT", () -> policy.create(room, "e1", subject, 1, period, arrival, arrival));
    }

    /** 验证去除首尾空白后，主题二百字符允许、二百零一字符拒绝。 */
    @Test
    void limitsSubjectAfterTrimmingToTwoHundredCharacters() {
        assertThat(policy.create(room, "e1", " " + "会".repeat(200) + " ", 1, period, arrival, arrival).subject())
                .hasSize(200);
        assertCode("INVALID_SUBJECT", () -> policy.create(room, "e1", "会".repeat(201), 1, period, arrival, arrival));
    }

    /** 验证员工标识必填，不依赖员工查询。 */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void requiresEmployeeIdWithoutLookingUpEmployees(String employeeId) {
        assertCode("EMPLOYEE_REQUIRED", () -> policy.create(room, employeeId, "会议", 1, period, arrival, arrival));
    }

    /** 验证创建流程拒绝非正数或超出容量的人数。 */
    @ParameterizedTest
    @ValueSource(ints = {0, -1, 7})
    void rejectsInvalidAttendance(int count) {
        assertThatThrownBy(() -> policy.create(room, "e1", "会议", count, period, arrival, arrival))
                .isInstanceOf(DomainRuleViolation.class);
    }

    /** 验证创建流程要求人数与预约时间段。 */
    @Test
    void rejectsMissingAttendanceAndPeriod() {
        assertCode("INVALID_ATTENDEE_COUNT", () -> policy.create(room, "e1", "会议", null, period, arrival, arrival));
        assertCode("TIME_REQUIRED", () -> policy.create(room, "e1", "会议", 1, null, arrival, arrival));
    }

    /** 验证请求到达时间和实际创建时间必须分别显式传入。 */
    @Test
    void requiresArrivalAndAuditTime() {
        assertThatThrownBy(() -> policy.create(room, "e1", "会议", 1, period, null, arrival))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.create(room, "e1", "会议", 1, period, arrival, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 验证开始时间必须严格晚于未截断的请求到达时间。 */
    @Test
    void rejectsStartEqualToOrBeforeExactArrival() {
        assertCode("START_NOT_FUTURE", () -> policy.create(room, "e1", "会议", 1, period, period.start(), arrival));
        assertCode("START_NOT_FUTURE", () -> policy.create(room, "e1", "会议", 1, period, period.start().plusNanos(1), arrival));
        var past = ReservationPeriod.parse("2026-09-18 15:30:59", "2026-09-18 16:00");
        assertCode("START_NOT_FUTURE", () -> policy.create(room, "e1", "会议", 1, past, arrival, arrival));
    }

    /** 验证冲突检查接收到正确的会议室及归一化时段，并拒绝冲突。 */
    @Test
    void checksConflictUsingRoomIdAndNormalizedPeriod() {
        var seenRoom = new AtomicReference<Long>();
        var seenPeriod = new AtomicReference<ReservationPeriod>();
        var conflictPolicy = new ReservationPolicy((id, candidate) -> {
            seenRoom.set(id);
            seenPeriod.set(candidate);
            return true;
        });
        assertCode("RESERVATION_CONFLICT", () -> conflictPolicy.create(room, "e1", "会议", 1, period, arrival, arrival));
        assertThat(seenRoom.get()).isEqualTo(1L);
        assertThat(seenPeriod.get()).isEqualTo(period);
    }

    /** 断言操作抛出领域异常且携带预期业务错误码。 */
    private static void assertCode(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(DomainRuleViolation.class)
                .satisfies(error -> assertThat(((DomainRuleViolation) error).code()).isEqualTo(code));
    }
}
