package com.example.meetroom.e2e;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
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

/** 通过真实 HTTP 与 MySQL 验证创建预约的提交结果和拒绝规则。 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationCreateHttpTest {
    /** 真实 HTTP 客户端。 */
    @Autowired private TestRestTemplate http;
    /** 隔离测试库查询工具。 */
    @Autowired private JdbcTemplate jdbc;
    /** 隔离数据源。 */
    @Autowired private DataSource dataSource;
    /** 响应 JSON 解析器。 */
    @Autowired private ObjectMapper json;

    /** 在任何建表或清理之前确认测试账号和数据库。 */
    @BeforeEach
    void prepare() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("meet_room_test");
        assertThat(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)).startsWith("meet_room_test@");
        script("schema.sql");
        // 红灯阶段预约表尚未实现；存在时才执行清理，不掩盖接口缺失的断言。
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'reservation'", Integer.class) > 0) {
            jdbc.update("DELETE FROM reservation");
        }
        jdbc.update("DELETE FROM meeting_room");
        script("data.sql");
    }

    /** 验证创建返回数据库 ID，所有业务字段及分钟精度正确落库。 */
    @Test
    void createsAndPersistsNormalizedReservation() throws Exception {
        var body = request();
        body.put("startTime", "2090-01-01 10:00:59.999");
        body.put("endTime", "2090-01-01 11:00:01");
        var response = http.postForEntity("/api/reservations", body, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        long id = json.readTree(response.getBody()).get("id").asLong();
        assertThat(id).isPositive();
        var row = jdbc.queryForMap("SELECT * FROM reservation WHERE id = ?", id);
        assertThat(row.get("meeting_room_id")).isEqualTo(1L);
        assertThat(row.get("employee_id")).isEqualTo("employee-01");
        assertThat(row.get("subject")).isEqualTo("项目讨论");
        assertThat(row.get("attendee_count")).isEqualTo(6);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("cancelled_at")).isNull();
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(start_time, '%Y-%m-%d %H:%i:%s') FROM reservation WHERE id = ?", String.class, id))
                .isEqualTo("2090-01-01 10:00:00");
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(end_time, '%Y-%m-%d %H:%i:%s') FROM reservation WHERE id = ?", String.class, id))
                .isEqualTo("2090-01-01 11:00:00");
        assertThat(row.get("created_at")).isNotNull();
    }

    /** 构造合法创建请求，测试可单独改变待验证字段。 */
    private Map<String, Object> request() {
        var body = new LinkedHashMap<String, Object>();
        body.put("meetingRoomId", 1L);
        body.put("employeeId", "employee-01");
        body.put("subject", "  项目讨论  ");
        body.put("attendeeCount", 6);
        body.put("startTime", "2090-01-01 10:00");
        body.put("endTime", "2090-01-01 11:00");
        return body;
    }

    /** 在已确认的测试库执行 UTF-8 SQL。 */
    private void script(String name) throws Exception {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new FileSystemResource("sql/" + name), StandardCharsets.UTF_8));
        }
    }
}
