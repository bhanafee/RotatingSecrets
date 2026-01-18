package com.maybeitssquid.rotatingsecrets.hikari;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class HikariDataSourceConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.hikari.pool-name:HikariPool}")
    private String poolName;

    @Value("${spring.datasource.hikari.maximum-pool-size:10}")
    private int maximumPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:2}")
    private int minimumIdle;

    @Value("${spring.datasource.hikari.idle-timeout:30000}")
    private long idleTimeout;

    @Value("${spring.datasource.hikari.connection-timeout:20000}")
    private long connectionTimeout;

    @Value("${spring.datasource.hikari.max-lifetime:1800000}")
    private long maxLifetime;

    @Bean
    public HikariConfig hikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setDriverClassName(driverClassName);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName(poolName);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setIdleTimeout(idleTimeout);
        config.setConnectionTimeout(connectionTimeout);
        config.setMaxLifetime(maxLifetime);
        return config;
    }

    @Bean
    @Primary
    public HikariDataSource dataSource(HikariConfig hikariConfig, HikariCredentialsUpdater credentialsUpdater) {
        // Set the credentials provider BEFORE creating the datasource
        hikariConfig.setCredentialsProvider(credentialsUpdater);
        HikariDataSource dataSource = new HikariDataSource(hikariConfig);
        // Inject datasource back into updater for connection eviction
        credentialsUpdater.setDataSource(dataSource);
        return dataSource;
    }

    @Bean("hikariUpdater")
    public HikariCredentialsUpdater hikariCredentialsUpdater() {
        return new HikariCredentialsUpdater(username, password);
    }
}