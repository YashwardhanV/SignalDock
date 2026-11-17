package dev.signaldock.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared setup for tests that need a real PostgreSQL (the queue relies on FOR UPDATE SKIP LOCKED,
 * window functions and jsonb, so an in-memory database would test something else).
 *
 * By default one Testcontainers PostgreSQL is started for the whole test run. Without Docker, point
 * the tests at any empty PostgreSQL 16 database instead:
 * SIGNALDOCK_TEST_DB_URL=jdbc:postgresql://localhost:5432/signaldock_test (plus _USER / _PASSWORD).
 */
@AutoConfigureMockMvc
@SpringBootTest
abstract class PostgresIntegrationTest {
    static final String API_KEY = "integration-api-key";
    static final int MAX_IN_FLIGHT_PER_ENDPOINT = 2;
    static final int AUTO_DISABLE_AFTER_DEAD = 3;

    private static final String EXTERNAL_DB_URL = System.getenv("SIGNALDOCK_TEST_DB_URL");
    private static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        if (EXTERNAL_DB_URL != null && !EXTERNAL_DB_URL.isBlank()) {
            registry.add("spring.datasource.url", () -> EXTERNAL_DB_URL);
            registry.add("spring.datasource.username", () -> env("SIGNALDOCK_TEST_DB_USER", "signaldock"));
            registry.add("spring.datasource.password", () -> env("SIGNALDOCK_TEST_DB_PASSWORD", "signaldock"));
        } else {
            PostgreSQLContainer<?> container = container();
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        }
        registry.add("app.demo.enabled", () -> "true");
        registry.add("app.api-key", () -> API_KEY);
        registry.add("app.demo.receiver-url", () -> "http://127.0.0.1:65530/success");
        registry.add("app.demo.failure-url", () -> "http://127.0.0.1:65530/failure");
        // Tests drive the worker by hand; the scheduler only runs once at startup.
        registry.add("app.delivery.poll-delay", () -> "3600000");
        registry.add("app.delivery.batch-size", () -> "20");
        registry.add("app.delivery.max-in-flight-per-endpoint", () -> String.valueOf(MAX_IN_FLIGHT_PER_ENDPOINT));
        registry.add("app.delivery.auto-disable-after-dead-deliveries", () -> String.valueOf(AUTO_DISABLE_AFTER_DEAD));
    }

    private static synchronized PostgreSQLContainer<?> container() {
        if (postgres == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            postgres.start(); // stopped by Testcontainers when the JVM exits
        }
        return postgres;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
