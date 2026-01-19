package com.maybeitssquid.rotatingsecrets.ucp;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.SQLException;

/**
 * Spring configuration for Oracle Universal Connection Pool (UCP) DataSource.
 *
 * <p>This configuration creates an Oracle UCP {@link PoolDataSource} bean with configurable
 * connection pool settings. Oracle UCP is designed specifically for Oracle databases and
 * provides advanced features not available in generic connection pools.</p>
 *
 * <h2>Oracle UCP Advantages</h2>
 * <ul>
 *   <li><b>Fast Application Notification (FAN)</b> - Automatic failover on RAC/Data Guard events</li>
 *   <li><b>Oracle Wallet Integration</b> - Enterprise credential management</li>
 *   <li><b>Transparent Application Continuity</b> - Automatic request replay on failover</li>
 *   <li><b>Service-Aware Connections</b> - Different pools for different database services</li>
 * </ul>
 *
 * <h2>Configuration Properties</h2>
 * <p>All properties are prefixed with {@code spring.datasource.ucp.}:</p>
 * <ul>
 *   <li>{@code url} - JDBC URL for the Oracle database (required)</li>
 *   <li>{@code connection-factory-class-name} - JDBC driver class (required)</li>
 *   <li>{@code user} - Initial database username (required)</li>
 *   <li>{@code password} - Initial database password (required)</li>
 *   <li>{@code pool-name} - Connection pool name (default: UCPPool)</li>
 *   <li>{@code initial-pool-size} - Initial connections to create (default: 2)</li>
 *   <li>{@code min-pool-size} - Minimum pool size (default: 2)</li>
 *   <li>{@code max-pool-size} - Maximum pool size (default: 10)</li>
 *   <li>{@code connection-wait-timeout} - Seconds to wait for a connection (default: 20)</li>
 *   <li>{@code inactive-connection-timeout} - Seconds before idle connection is closed (default: 30)</li>
 *   <li>{@code max-connection-reuse-time} - Maximum seconds to reuse a connection (default: 1800)</li>
 * </ul>
 *
 * @see PoolDataSource
 * @see UcpCredentialsUpdater
 */
@Configuration
public class UcpDataSourceConfig {

    /** JDBC URL for the Oracle database connection. */
    @Value("${spring.datasource.ucp.url}")
    private String url;

    /** Fully qualified class name of the JDBC connection factory (driver). */
    @Value("${spring.datasource.ucp.connection-factory-class-name}")
    private String connectionFactoryClassName;

    /** Initial database username for pool connections. */
    @Value("${spring.datasource.ucp.user}")
    private String user;

    /** Initial database password for pool connections. */
    @Value("${spring.datasource.ucp.password}")
    private String password;

    /** Name of the connection pool for identification and management. */
    @Value("${spring.datasource.ucp.pool-name:UCPPool}")
    private String poolName;

    /** Number of connections to create when the pool is initialized. */
    @Value("${spring.datasource.ucp.initial-pool-size:2}")
    private int initialPoolSize;

    /** Minimum number of connections the pool should maintain. */
    @Value("${spring.datasource.ucp.min-pool-size:2}")
    private int minPoolSize;

    /** Maximum number of connections the pool can create. */
    @Value("${spring.datasource.ucp.max-pool-size:10}")
    private int maxPoolSize;

    /** Seconds to wait when requesting a connection before timing out. */
    @Value("${spring.datasource.ucp.connection-wait-timeout:20}")
    private int connectionWaitTimeout;

    /** Seconds an idle connection can remain in the pool before being closed. */
    @Value("${spring.datasource.ucp.inactive-connection-timeout:30}")
    private int inactiveConnectionTimeout;

    /** Maximum seconds a connection can be reused before being recycled. */
    @Value("${spring.datasource.ucp.max-connection-reuse-time:1800}")
    private int maxConnectionReuseTime;

    /**
     * Creates and configures the Oracle UCP PoolDataSource bean.
     *
     * <p>The pool is configured with all connection and timeout settings from
     * application properties. The returned {@link PoolDataSource} can be used
     * directly or wrapped for additional functionality.</p>
     *
     * @return a configured Oracle UCP PoolDataSource
     * @throws SQLException if the pool cannot be configured or initialized
     */
    @Bean
    public PoolDataSource poolDataSource() throws SQLException {
        PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
        pds.setConnectionPoolName(poolName);
        pds.setConnectionFactoryClassName(connectionFactoryClassName);
        pds.setURL(url);
        pds.setUser(user);
        pds.setPassword(password);
        pds.setInitialPoolSize(initialPoolSize);
        pds.setMinPoolSize(minPoolSize);
        pds.setMaxPoolSize(maxPoolSize);
        pds.setConnectionWaitTimeout(connectionWaitTimeout);
        pds.setInactiveConnectionTimeout(inactiveConnectionTimeout);
        pds.setMaxConnectionReuseTime(maxConnectionReuseTime);
        return pds;
    }

    /**
     * Creates the UCP credentials updater bean for credential rotation support.
     *
     * <p>The updater is registered with the {@link com.maybeitssquid.rotatingsecrets.CredentialsProviderService}
     * to receive notifications when database credentials are rotated. It handles
     * updating the pool's credentials and refreshing connections.</p>
     *
     * @param poolDataSource the Oracle UCP PoolDataSource to manage
     * @return a credentials updater configured for the given pool
     * @see UcpCredentialsUpdater
     */
    @Bean("ucpUpdater")
    public UcpCredentialsUpdater ucpCredentialsUpdater(PoolDataSource poolDataSource) {
        return new UcpCredentialsUpdater(poolDataSource);
    }
}