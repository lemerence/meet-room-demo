package com.example.meetroom.domain.repository;

import java.util.List;
import com.example.meetroom.domain.model.MeetingRoom;

/** 会议室领域仓储契约，屏蔽具体数据库访问方式。 */
public interface MeetingRoomRepository {
    /** 查询全部会议室；没有记录时返回空列表。 */
    List<MeetingRoom> findAll();
}
