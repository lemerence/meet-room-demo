package com.example.meetroom.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 映射 meeting_room 表的持久化实体，不承担预约业务规则。 */
@TableName("meeting_room")
public class MeetingRoomEntity {

    /** 会议室数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 会议室名称。 */
    private String name;
    /** 会议室可容纳人数。 */
    private Integer capacity;

    /** 读取会议室数据库主键。 */
    public Long getId() {
        return id;
    }

    /** 设置会议室数据库主键，供持久化映射使用。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 读取会议室名称。 */
    public String getName() {
        return name;
    }

    /** 设置会议室名称，供持久化映射使用。 */
    public void setName(String name) {
        this.name = name;
    }

    /** 读取会议室容量，单位为人。 */
    public Integer getCapacity() {
        return capacity;
    }

    /** 设置会议室容量，供持久化映射使用。 */
    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }
}
