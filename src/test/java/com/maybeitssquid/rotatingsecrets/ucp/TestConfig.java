package com.maybeitssquid.rotatingsecrets.ucp;

import org.h2.jdbcx.JdbcDataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Test configuration that provides an H2 in-memory database
 * instead of requiring Kubernetes-mounted secrets or Oracle UCP.
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public DataSource testDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
