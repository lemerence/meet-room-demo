package com.example.meetroom.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.meetroom.infrastructure.persistence.entity.MeetingRoomEntity;
import org.apache.ibatis.annotations.Mapper;

/** 通过 MyBatis-Plus 提供会议室表的数据访问能力，仅供仓储实现使用。 */
@Mapper
public interface MeetingRoomMapper extends BaseMapper<MeetingRoomEntity> {
}
