package com.example.meetroom.domain.model;

import com.example.meetroom.domain.exception.DomainRuleViolation;

/**
 * 会议室聚合根，保存基础信息并维护参会人数与容量规则。
 * @param id 会议室 ID
 * @param name 会议室名称
 * @param capacity 可容纳人数
 */
public record MeetingRoom(Long id, String name, int capacity) {
    /** 校验人数必填且为正整数，并拒绝超过会议室容量的请求。 */
    public void validateAttendance(Integer count) {
        if (count == null || count <= 0) {
            throw new DomainRuleViolation("INVALID_ATTENDEE_COUNT", "参会人数必须为正整数");
        }
        if (count > capacity) {
            throw new DomainRuleViolation("CAPACITY_EXCEEDED", "参会人数不能超过会议室容量");
        }
    }
}
