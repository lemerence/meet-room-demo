package com.example.meetroom.domain.model;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import com.example.meetroom.domain.exception.DomainRuleViolation;

/** 预约聚合根，维护自身字段约束、取消状态与审计信息；新建对象尚未持久化。 */
public class Reservation {
    /** 主键；预约新建对象尚未持久化时为 null。 */
    private final Long id;
    /** 预约所属会议室的业务标识。 */
    private final Long meetingRoomId;
    /** 预约员工标识，仅作为业务字段保存。 */
    private final String employeeId;
    /** 已去除首尾空白的会议主题。 */
    private final String subject;
    /** 参会人数，单位为人。 */
    private final int attendeeCount;
    /** 分钟精度的预约时间段。 */
    private final ReservationPeriod period;
    /** 预约是否已取消，不表示会议进行阶段。 */
    private ReservationStatus status;
    /** 实际创建操作时间，精确到秒。 */
    private final LocalDateTime createdAt;
    /** 首次取消操作时间，精确到秒；未取消时为 null。 */
    private LocalDateTime cancelledAt;

    /** 构建尚未持久化的有效预约，并将实际创建时间保留到秒。 */
    private Reservation(Long meetingRoomId, String employeeId, String subject, int attendeeCount,
                        ReservationPeriod period, LocalDateTime createdAt) {
        // 当前对象尚未写入数据库，不能伪造已经分配的预约 ID。
        this.id = null;
        this.meetingRoomId = meetingRoomId;
        this.employeeId = employeeId;
        this.subject = subject;
        this.attendeeCount = attendeeCount;
        this.period = period;
        // 审计记录实际操作时间，与决定创建资格的请求到达时间分开。
        this.createdAt = createdAt.truncatedTo(ChronoUnit.SECONDS);
        this.status = ReservationStatus.ACTIVE;
    }

    /** 构建未持久化预约；容量、创建时点和跨预约冲突由 ReservationPolicy 协调。 */
    public static Reservation newActive(Long meetingRoomId, String employeeId, String subject,
                                        Integer attendeeCount, ReservationPeriod period, LocalDateTime createdAt) {
        if (meetingRoomId == null) {
            throw new DomainRuleViolation("ROOM_NOT_FOUND", "会议室不存在");
        }
        if (employeeId == null || employeeId.isBlank()) {
            throw new DomainRuleViolation("EMPLOYEE_REQUIRED", "员工ID不能为空");
        }
        if (subject == null || subject.strip().isEmpty()) {
            throw new DomainRuleViolation("INVALID_SUBJECT", "会议主题不能为空");
        }
        // 先去除首尾空白，再按 Unicode 码点计数，避免把一个补充字符算作两个字符。
        subject = subject.strip();
        if (subject.codePointCount(0, subject.length()) > 200) {
            throw new DomainRuleViolation("INVALID_SUBJECT", "会议主题不得超过200个字符");
        }
        if (attendeeCount == null || attendeeCount <= 0) {
            throw new DomainRuleViolation("INVALID_ATTENDEE_COUNT", "参会人数必须为正整数");
        }
        if (period == null) {
            throw new DomainRuleViolation("TIME_REQUIRED", "预约时间不能为空");
        }
        if (createdAt == null) {
            throw new DomainRuleViolation("AUDIT_TIME_REQUIRED", "创建操作时间不能为空");
        }
        return new Reservation(meetingRoomId, employeeId, subject, attendeeCount, period, createdAt);
    }

    /** 返回预约 ID；当前新建且未持久化的对象返回 null。 */
    public Long id() {
        return id;
    }

    /** 按请求到达时间判断取消资格；重复取消优先返回且不改写首次审计时间。 */
    public CancellationResult cancel(LocalDateTime requestArrivedAt, LocalDateTime cancelledAt) {
        // 已取消优先返回，原开始时间已经过去也不改变重复取消的结果。
        if (status == ReservationStatus.CANCELLED) {
            return CancellationResult.ALREADY_CANCELLED;
        }
        if (requestArrivedAt == null || cancelledAt == null) {
            throw new DomainRuleViolation("TIME_REQUIRED", "请求到达时间和取消操作时间不能为空");
        }
        // 判断的是服务端接收请求的时刻，不是等待锁结束后的处理时刻。
        if (!requestArrivedAt.isBefore(period.start())) {
            throw new DomainRuleViolation("RESERVATION_STARTED", "只有尚未开始的预约可以取消");
        }
        // 所有拒绝条件检查完成后再修改状态，失败不留下部分变更。
        this.cancelledAt = cancelledAt.truncatedTo(ChronoUnit.SECONDS);
        this.status = ReservationStatus.CANCELLED;
        return CancellationResult.CANCELLED;
    }

    /** 判断本预约是否仍占用指定会议室与候选时间段的交集。 */
    public boolean occupies(Long meetingRoomId, ReservationPeriod candidate) {
        return status == ReservationStatus.ACTIVE && this.meetingRoomId.equals(meetingRoomId)
                && period.overlaps(candidate);
    }

    /** 返回预约所属会议室 ID。 */
    public Long meetingRoomId() {
        return meetingRoomId;
    }

    /** 返回预约员工的业务标识，不代表已验证的登录身份。 */
    public String employeeId() {
        return employeeId;
    }

    /** 返回去除首尾空白后的会议主题。 */
    public String subject() {
        return subject;
    }

    /** 返回参会人数。 */
    public int attendeeCount() {
        return attendeeCount;
    }

    /** 返回已归一化到分钟的预约时间段。 */
    public ReservationPeriod period() {
        return period;
    }

    /** 返回当前预约状态。 */
    public ReservationStatus status() {
        return status;
    }

    /** 返回实际创建操作的秒级审计时间。 */
    public LocalDateTime createdAt() {
        return createdAt;
    }

    /** 返回首次取消的秒级审计时间；未取消时返回 null。 */
    public LocalDateTime cancelledAt() {
        return cancelledAt;
    }
}
