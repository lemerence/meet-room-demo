package com.example.meetroom.integration;

import java.sql.Connection;
import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证真实 MySQL 连接、MyBatis 数据源及测试账号的数据库隔离。 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DatabaseConnectionTest {

    /** 连接隔离测试库的数据源。 */
    @Autowired
    private DataSource dataSource;

    /** 用于核对 MyBatis 实际连接目标的会话工厂。 */
    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    /** 通过实际 SQL 验证连接目标、账号和数据库查询能力。 */
    @Test
    void connectsToIsolatedMysqlTestDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT DATABASE(), CURRENT_USER(), 1")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("meet_room_test");
            assertThat(result.getString(2)).startsWith("meet_room_test@");
            assertThat(result.getInt(3)).isEqualTo(1);
        }
    }

    /** 验证 MyBatis 会话连接到隔离测试库。 */
    @Test
    void mybatisUsesTheSameIsolatedDataSource() throws Exception {
        try (var session = sqlSessionFactory.openSession()) {
            assertThat(session.getConnection().getCatalog()).isEqualTo("meet_room_test");
        }
    }

    /** 验证测试账号无法通过数据库元数据看到业务库。 */
    @Test
    void testAccountCannotAccessBusinessDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'meet_room'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
        }
    }
}
