package com.example.meetroom.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 提供统一采用北京时间且可在测试中替换的时钟配置。 */
@Configuration
public class TimeConfiguration {
    /** 提供 Asia/Shanghai 时钟，避免依赖运行机器的默认时区。 */
    @Bean
    public Clock businessClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
