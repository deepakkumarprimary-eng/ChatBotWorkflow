package com.xpressbees.chatbot.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import redis.embedded.RedisServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;

/**
 * Base class for integration tests providing:
 * - A PostgreSQL TestContainer initialized with schema.sql
 * - An embedded Redis server to avoid external Redis dependency
 *
 * Subclasses automatically get a fully configured Spring context with
 * real PostgreSQL and Redis connections.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("chatbot_db_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("schema.sql");

    private static RedisServer redisServer;

    @BeforeAll
    static void startRedis() throws IOException {
        redisServer = new RedisServer(6370);
        redisServer.start();
    }

    @AfterAll
    static void stopRedis() throws IOException {
        if (redisServer != null && redisServer.isActive()) {
            redisServer.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL datasource properties
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");

        // Disable SQL init since TestContainers handles schema via withInitScript
        registry.add("spring.sql.init.mode", () -> "never");

        // Redis properties pointing to embedded server
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6370");
    }
}
