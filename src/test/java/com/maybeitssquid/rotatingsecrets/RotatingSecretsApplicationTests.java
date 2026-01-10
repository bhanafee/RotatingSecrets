package com.maybeitssquid.rotatingsecrets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests using H2 in-memory database.
 */
@SpringBootTest(
        classes = {TestConfig.class, DatabasePollingService.class},
        properties = {
                "spring.cloud.vault.enabled=false"
        }
)
@EnableAutoConfiguration
class RotatingSecretsApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DatabasePollingService pollingService;

    @Test
    void contextLoads() {
        assertNotNull(dataSource);
        assertNotNull(pollingService);
    }

    @Test
    void dataSource_isConnectable() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            assertNotNull(conn);
            assertFalse(conn.isClosed());
        }
    }

    @Test
    void dataSource_canExecuteQueries() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 FROM DUAL")) {

            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void pollingService_canExecuteWithoutError() {
        assertDoesNotThrow(() -> pollingService.pollEveryFiveSeconds());
        assertDoesNotThrow(() -> pollingService.pollEveryThreeSeconds());
    }
}
