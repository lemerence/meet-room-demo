package com.example.meetroom.domain.service;

import java.time.LocalDateTime;
import java.util.Objects;
import com.example.meetroom.domain.exception.DomainRuleViolation;
import com.example.meetroom.domain.model.MeetingRoom;
import com.example.meetroom.domain.model.Reservation;
import com.example.meetroom.domain.model.ReservationPeriod;
import com.example.meetroom.domain.repository.ReservationConflictRepository;

/** 协调创建预约所需的自身约束、容量、请求时点和跨预约冲突规则。 */
public class ReservationPolicy {
    /** 有效预约冲突查询契约，由持久化层提供具体实现。 */
    private final ReservationConflictRepository conflicts;

    /** 注入冲突查询契约，领域层不负责数据库连接或加锁。 */
    public ReservationPolicy(ReservationConflictRepository conflicts) {
        this.conflicts = Objects.requireNonNull(conflicts);
    }

    /** 使用固定的请求到达时间及独立审计时间校验创建规则，返回未持久化预约。 */
    public Reservation create(MeetingRoom room, String employeeId, String subject, Integer attendeeCount,
                              ReservationPeriod period, LocalDateTime requestArrivedAt, LocalDateTime createdAt) {
        if (room == null) {
            throw new DomainRuleViolation("ROOM_NOT_FOUND", "会议室不存在");
        }
        if (requestArrivedAt == null) {
            throw new DomainRuleViolation("REQUEST_TIME_REQUIRED", "请求到达时间不能为空");
        }
        // 聚合先维护自身字段约束，会议室对象再维护容量约束。
        var reservation = Reservation.newActive(room.id(), employeeId, subject, attendeeCount, period, createdAt);
        room.validateAttendance(attendeeCount);
        // 即使实际创建操作已延迟到开始后，资格仍以原请求到达时间判断。
        if (!period.start().isAfter(requestArrivedAt)) {
            throw new DomainRuleViolation("START_NOT_FUTURE", "开始时间必须晚于请求到达时间");
        }
        // 此处只协调冲突规则；调用方在持久化阶段必须用会议室锁和事务保证检查与写入一致。
        if (conflicts.existsActiveOverlap(room.id(), period)) {
            throw new DomainRuleViolation("RESERVATION_CONFLICT", "会议室在该时间段已有有效预约");
        }
        return reservation;
    }
}
