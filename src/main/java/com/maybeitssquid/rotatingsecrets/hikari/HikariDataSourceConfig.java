package com.maybeitssquid.rotatingsecrets.hikari;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Configures the HikariCP connection pool with dynamic credential support.
 *
 * <p>Uses {@link DynamicHikariCredentialsProvider} to supply fresh credentials
 * for each new physical connection, enabling seamless password rotation.</p>
 */
@Configuration
public class HikariDataSourceConfig {

    /**
     * Creates a HikariCP DataSource configured with dynamic credentials.
     *
     * @param credentialsProvider provides credentials from Kubernetes secrets
     * @param jdbcUrl             JDBC connection URL
     * @param minPoolSize         minimum number of idle connections in the pool
     * @param maxPoolSize         maximum number of connections in the pool
     * @return configured HikariCP DataSource
     */
    @Bean
    public DataSource dataSource(
            DynamicHikariCredentialsProvider credentialsProvider,
            @Value("${db.jdbc-url}") String jdbcUrl,
            @Value("${db.pool.min-size}") int minPoolSize,
            @Value("${db.pool.max-size}") int maxPoolSize) {

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setCredentialsProvider(credentialsProvider);
        config.setMinimumIdle(minPoolSize);
        config.setMaximumPoolSize(maxPoolSize);
        config.setPoolName("RotatingSecretsPool");

        return new HikariDataSource(config);
    }
}
