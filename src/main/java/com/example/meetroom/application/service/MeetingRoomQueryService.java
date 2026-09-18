package com.example.meetroom.application.service;

import java.util.List;
import com.example.meetroom.domain.model.MeetingRoom;
import com.example.meetroom.domain.repository.MeetingRoomRepository;
import org.springframework.stereotype.Service;

/** 编排会议室列表查询，将数据访问委托给领域仓储。 */
@Service
public class MeetingRoomQueryService {

    /** 会议室领域仓储，隐藏数据库访问细节。 */
    private final MeetingRoomRepository repository;

    /** 注入会议室领域仓储。 */
    public MeetingRoomQueryService(MeetingRoomRepository repository) {
        this.repository = repository;
    }

    /** 查询全部会议室；没有记录时返回空列表。 */
    public List<MeetingRoom> findAll() {
        return repository.findAll();
    }
}
