package com.example.meetroom.e2e;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 HTTP 和隔离 MySQL 测试库验证会议室查询链路。 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MeetingRoomHttpTest {

    /** 通过随机端口访问真实应用的 HTTP 测试客户端。 */
    @Autowired
    private TestRestTemplate http;
    /** 仅操作隔离测试库的 JDBC 工具。 */
    @Autowired
    private JdbcTemplate jdbc;
    /** 连接隔离测试库的数据源。 */
    @Autowired
    private DataSource dataSource;
    /** 解析和比较 HTTP JSON 响应的序列化工具。 */
    @Autowired
    private ObjectMapper json;

    /** 确认实际测试库和账号后，准备本用例独占的会议室测试数据。 */
    @BeforeEach
    void prepareIsolatedTestDatabase() throws Exception {
        // 确认实际连接目标后，才允许清理本测试使用的表。
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("meet_room_test");
        assertThat(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)).startsWith("meet_room_test@");
        executeScript("schema.sql");
        jdbc.update("DELETE FROM meeting_room");
    }

    /** 通过真实 HTTP 验证预置会议室的字段、顺序和数量。 */
    @Test
    void listsSeededRoomsThroughRealHttp() throws Exception {
        executeScript("data.sql");

        var response = http.getForEntity("/api/meeting-rooms", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode rooms = json.readTree(response.getBody());
        assertThat(rooms).isEqualTo(json.readTree("""
                [{"id":1,"name":"第一会议室","capacity":6},
                 {"id":2,"name":"第二会议室","capacity":12},
                 {"id":3,"name":"第三会议室","capacity":20}]
                """));
    }

    /** 验证无会议室时返回 HTTP 200 和空数组。 */
    @Test
    void returnsEmptyArrayWhenNoRoomsExist() {
        var response = http.getForEntity("/api/meeting-rooms", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("[]");
    }

    /** 验证重复执行初始化不会重复插入或覆盖已有名称和容量。 */
    @Test
    void repeatedInitializationDoesNotOverwriteExistingRoom() throws Exception {
        executeScript("data.sql");
        jdbc.update("UPDATE meeting_room SET name = ?, capacity = ? WHERE id = ?", "保留的会议室", 8, 1L);

        executeScript("schema.sql");
        executeScript("data.sql");

        var response = http.getForEntity("/api/meeting-rooms", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode rooms = json.readTree(response.getBody());
        assertThat(rooms.size()).isEqualTo(3);
        assertThat(rooms.get(0).get("name").asText()).isEqualTo("保留的会议室");
        assertThat(rooms.get(0).get("capacity").asInt()).isEqualTo(8);
    }

    /** 在隔离测试库注入故障，验证返回安全的 503 错误响应。 */
    @Test
    void databaseFailureReturnsSafeErrorInsteadOfEmptySuccess() throws Exception {
        // 前置方法已确认测试库及账号；只在隔离库删表模拟访问失败。
        jdbc.execute("DROP TABLE meeting_room");
        try {
            var response = http.getForEntity("/api/meeting-rooms", String.class);

            assertThat(response.getStatusCode().value()).isEqualTo(503);
            assertThat(json.readTree(response.getBody())).isEqualTo(json.readTree("""
                    {"code":"DATABASE_UNAVAILABLE","message":"数据库暂时不可用，请稍后重试"}
                    """));
        } finally {
            // 即使断言失败也恢复测试表结构，避免故障注入污染后续用例。
            executeScript("schema.sql");
        }
    }

    /** 以 UTF-8 在隔离测试数据源上执行项目 SQL 脚本。 */
    private void executeScript(String name) throws Exception {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new FileSystemResource("sql/" + name), StandardCharsets.UTF_8));
        }
    }
}
