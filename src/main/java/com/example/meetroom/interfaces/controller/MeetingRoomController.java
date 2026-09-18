package com.example.meetroom.interfaces.controller;

import java.util.List;
import com.example.meetroom.application.service.MeetingRoomQueryService;
import com.example.meetroom.interfaces.dto.MeetingRoomResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供会议室列表 HTTP 接口，负责将应用查询结果转换为响应 DTO。 */
@RestController
@RequestMapping("/api/meeting-rooms")
public class MeetingRoomController {

    /** 编排会议室列表查询的应用服务。 */
    private final MeetingRoomQueryService service;

    /** 注入会议室查询应用服务。 */
    public MeetingRoomController(MeetingRoomQueryService service) {
        this.service = service;
    }

    /** 返回按 ID 升序排列的会议室响应列表。 */
    @GetMapping
    public List<MeetingRoomResponse> list() {
        return service.findAll().stream()
                .map(room -> new MeetingRoomResponse(room.id(), room.name(), room.capacity()))
                .toList();
    }
}
