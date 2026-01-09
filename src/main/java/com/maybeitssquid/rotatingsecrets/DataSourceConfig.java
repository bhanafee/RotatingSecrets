package com.maybeitssquid.rotatingsecrets;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(
            KubernetesCredentialsProvider credentialsProvider,
            @Value("${pool.min-size:1}") int minPoolSize,
            @Value("${pool.max-size:3}") int maxPoolSize,
            @Value("${pool.connection-timeout-ms:5000}") long connectionTimeoutMs) {

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(credentialsProvider.getJdbcUrl());
        config.setCredentialsProvider(credentialsProvider);
        config.setMinimumIdle(minPoolSize);
        config.setMaximumPoolSize(maxPoolSize);
        config.setConnectionTimeout(connectionTimeoutMs);
        config.setPoolName("RotatingSecretsPool");
        config.setConnectionTestQuery("SELECT 1 FROM DUAL");

        return new HikariDataSource(config);
    }
}
