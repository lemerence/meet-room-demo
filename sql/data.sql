-- 重复初始化只保留已有记录，不覆盖名称和容量。
INSERT INTO meeting_room (id, name, capacity)
VALUES (1, '第一会议室', 6), (2, '第二会议室', 12), (3, '第三会议室', 20)
ON DUPLICATE KEY UPDATE id = meeting_room.id;
