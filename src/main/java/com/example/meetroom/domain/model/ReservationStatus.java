package com.example.meetroom.domain.model;

/** 预约持久化状态，不用于表达会议未开始、进行中或已结束。 */
public enum ReservationStatus {
    /** 创建成功且未取消，包括尚未开始、进行中及已结束的预约。 */
    ACTIVE,
    /** 已取消，不再占用时间段，但记录仍保留。 */
    CANCELLED
}
