package com.example.meetroom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 会议室预约后端的 Spring Boot 启动入口。 */
@SpringBootApplication
public class Application {

    /** 启动 Spring 容器和内嵌 Web 服务。 */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
