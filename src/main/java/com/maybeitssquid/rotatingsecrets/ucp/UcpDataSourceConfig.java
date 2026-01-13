package com.maybeitssquid.rotatingsecrets.ucp;

import com.maybeitssquid.rotatingsecrets.CredentialsProvider;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Configures the Oracle UCP connection pool with dynamic credential support.
 *
 * <p>Uses {@link RotatingCredentialsDataSource} to wrap the UCP pool and provide
 * fresh credentials for each new connection, enabling seamless password rotation.</p>
 */
@Configuration
public class UcpDataSourceConfig {

    /**
     * Creates an Oracle UCP DataSource configured with dynamic credentials.
     *
     * @param credentialsProvider provides credentials from Kubernetes secrets
     * @param jdbcUrl             JDBC connection URL
     * @param minPoolSize         minimum number of connections in the pool
     * @param maxPoolSize         maximum number of connections in the pool
     * @param connectionTimeoutMs maximum time to wait for a connection from the pool
     * @return configured DataSource with rotating credentials support
     * @throws SQLException if pool configuration fails
     */
    @Bean
    public DataSource dataSource(
            CredentialsProvider credentialsProvider,
            @Value("${db.jdbc-url}") String jdbcUrl,
            @Value("${pool.min-size}") int minPoolSize,
            @Value("${pool.max-size}") int maxPoolSize,
            @Value("${pool.connection-timeout-ms}") long connectionTimeoutMs) throws SQLException {

        PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
        pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        pds.setURL(jdbcUrl);
        pds.setConnectionPoolName("RotatingSecretsPool");
        pds.setMinPoolSize(minPoolSize);
        pds.setMaxPoolSize(maxPoolSize);
        pds.setConnectionWaitTimeout((int) (connectionTimeoutMs / 1000));

        return new RotatingCredentialsDataSource(pds, credentialsProvider);
    }
}
