package com.barofit.report;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** 실제 PostgreSQL 동작을 검증하되 개발자의 로컬 DB에는 쓰지 않는다. */
public abstract class ReportPostgresSupport {
    private static final PostgreSQLContainer<?> POSTGRES = start();

    private static PostgreSQLContainer<?> start() {
        var container = new PostgreSQLContainer<>("postgres:18.6");
        container.start();
        org.flywaydb.core.Flyway.configure()
            .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
            .schemas("report").locations("classpath:db/migration/report").load().migrate();
        Runtime.getRuntime().addShutdownHook(new Thread(container::stop));
        return container;
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
            + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=report");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.schemas", () -> "report");
        registry.add("spring.flyway.default-schema", () -> "report");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/report");
    }
}
