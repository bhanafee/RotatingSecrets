package com.maybeitssquid.rotatingsecrets;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Test configuration that provides an H2 in-memory database
 * instead of requiring Kubernetes-mounted secrets.
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public DataSource testDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(3);
        config.setPoolName("TestPool");

        return new HikariDataSource(config);
    }
}
