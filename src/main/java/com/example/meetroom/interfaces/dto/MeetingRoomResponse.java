package com.example.meetroom.interfaces.dto;

/**
 * 会议室列表中的单条响应，仅暴露接口约定字段。
 * @param id 会议室 ID
 * @param name 会议室名称
 * @param capacity 可容纳人数
 */
public record MeetingRoomResponse(Long id, String name, int capacity) {
}
