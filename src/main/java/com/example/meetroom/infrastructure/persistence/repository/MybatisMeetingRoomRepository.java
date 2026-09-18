package com.example.meetroom.infrastructure.persistence.repository;

import java.util.List;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.meetroom.domain.model.MeetingRoom;
import com.example.meetroom.domain.repository.MeetingRoomRepository;
import com.example.meetroom.infrastructure.persistence.entity.MeetingRoomEntity;
import com.example.meetroom.infrastructure.persistence.mapper.MeetingRoomMapper;
import org.springframework.stereotype.Repository;

/** 使用 MyBatis-Plus 实现会议室仓储，并转换持久化实体为领域对象。 */
@Repository
public class MybatisMeetingRoomRepository implements MeetingRoomRepository {

    /** 负责会议室表访问的 MyBatis-Plus Mapper。 */
    private final MeetingRoomMapper mapper;

    /** 注入会议室 Mapper，隔离领域契约与框架细节。 */
    public MybatisMeetingRoomRepository(MeetingRoomMapper mapper) {
        this.mapper = mapper;
    }

    /** 查询全部会议室；没有记录时返回空列表。 */
    @Override
    public List<MeetingRoom> findAll() {
        // 明确按主键排序，避免数据库默认顺序不稳定；转换后不向领域层泄露表实体。
        return mapper.selectList(Wrappers.<MeetingRoomEntity>lambdaQuery()
                        .orderByAsc(MeetingRoomEntity::getId))
                .stream()
                .map(entity -> new MeetingRoom(entity.getId(), entity.getName(), entity.getCapacity()))
                .toList();
    }
}
