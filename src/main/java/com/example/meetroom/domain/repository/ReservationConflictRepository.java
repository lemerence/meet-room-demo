package com.example.meetroom.domain.repository;

import com.example.meetroom.domain.model.ReservationPeriod;

/** 只检查同一会议室 ACTIVE 记录的区间重叠；T03 在持有会议室锁的事务内实现。 */
@FunctionalInterface
public interface ReservationConflictRepository {
    /** 查询同一会议室是否存在与候选时间段重叠的 ACTIVE 预约；实现必须受创建事务的会议室锁保护。 */
    boolean existsActiveOverlap(Long meetingRoomId, ReservationPeriod period);
}
