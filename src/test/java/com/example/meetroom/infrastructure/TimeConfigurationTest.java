package com.example.meetroom.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import com.example.meetroom.infrastructure.config.TimeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** 验证注入时钟使用北京时间，避免日期边界受机器默认时区影响。 */
class TimeConfigurationTest {
    /** 使用固定瞬间验证注入时钟按北京时间转换跨日日期，并保留时间精度。 */
    @Test
    void injectableClockConvertsUtcInstantToBeijingDateAndTime() {
        try (var context = new AnnotationConfigApplicationContext(TimeConfiguration.class)) {
            var clock = context.getBean(Clock.class);
            var fixed = Clock.fixed(Instant.parse("2026-09-18T23:30:20.123456789Z"), clock.getZone());
            assertThat(LocalDateTime.now(fixed)).isEqualTo(LocalDateTime.of(2026, 9, 19, 7, 30, 20, 123456789));
        }
    }
}
