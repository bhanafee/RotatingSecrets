package com.maybeitssquid.rotatingsecrets;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Test configuration that provides an H2 in-memory database
 * instead of requiring Kubernetes-mounted secrets.
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public DataSource testDataSource() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:testdb;MODE=Oracle;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(3);
        config.setPoolName("TestPool");

        HikariDataSource dataSource = new HikariDataSource(config);

        // Initialize DUAL table for Oracle compatibility
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS DUAL (DUMMY VARCHAR(1))");
            stmt.execute("MERGE INTO DUAL KEY(DUMMY) VALUES ('X')");
        }

        return dataSource;
    }
}
