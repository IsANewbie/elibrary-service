package com.elibrary;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 集成测试基座：设了 IT_DB_URL 走外部 MySQL（无 Docker 环境用），否则 Testcontainers 起临时容器。
 * 两条路径跑同一套 Flyway 迁移，schema 断言不分叉。
 *
 * @author link
 * @date 2026/10/19
 */
@SpringBootTest
public abstract class AbstractIT {

    private static final String EXTERNAL_URL = System.getenv("IT_DB_URL");

    private static final MySQLContainer<?> MYSQL =
            EXTERNAL_URL == null ? new MySQLContainer<>("mysql:8.4") : null;

    static {
        if (EXTERNAL_URL == null) {
            MYSQL.start(); // 手动启动：外部库路径不触发容器生命周期
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (EXTERNAL_URL == null) {
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> System.getenv("IT_DB_USER"));
            registry.add("spring.datasource.password", () -> System.getenv("IT_DB_PASSWORD"));
        }
    }
}
