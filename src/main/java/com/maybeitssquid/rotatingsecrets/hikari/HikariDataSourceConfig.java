package com.maybeitssquid.rotatingsecrets.hikari;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring configuration for HikariCP DataSource with dynamic credential rotation support.
 *
 * <p>This configuration creates a HikariCP {@link HikariDataSource} as the primary application
 * DataSource. HikariCP is a high-performance JDBC connection pool that is the default for
 * Spring Boot applications. This configuration integrates HikariCP with the credential
 * rotation system via {@link HikariCredentialsUpdater}.</p>
 *
 * <h2>Credential Rotation</h2>
 * <p>Unlike standard HikariCP configurations that use static credentials, this configuration
 * uses a {@link com.zaxxer.hikari.HikariCredentialsProvider} to support dynamic credentials.
 * When credentials are rotated:</p>
 * <ol>
 *   <li>The {@link HikariCredentialsUpdater} receives the new credentials</li>
 *   <li>The updater stores the new credentials for future connections</li>
 *   <li>Existing connections are soft-evicted via {@code softEvictConnections()}</li>
 *   <li>New connections automatically use the updated credentials</li>
 * </ol>
 *
 * <h2>Configuration Properties</h2>
 * <p>Standard Spring datasource properties:</p>
 * <ul>
 *   <li>{@code spring.datasource.url} - JDBC URL (required)</li>
 *   <li>{@code spring.datasource.driver-class-name} - JDBC driver class (required)</li>
 *   <li>{@code spring.datasource.username} - Initial database username (required)</li>
 *   <li>{@code spring.datasource.password} - Initial database password (required)</li>
 * </ul>
 *
 * <p>HikariCP-specific properties (prefixed with {@code spring.datasource.hikari.}):</p>
 * <ul>
 *   <li>{@code pool-name} - Connection pool name (default: HikariPool)</li>
 *   <li>{@code maximum-pool-size} - Maximum connections in pool (default: 10)</li>
 *   <li>{@code minimum-idle} - Minimum idle connections (default: 2)</li>
 *   <li>{@code idle-timeout} - Milliseconds before idle connection is removed (default: 30000)</li>
 *   <li>{@code connection-timeout} - Milliseconds to wait for connection (default: 20000)</li>
 *   <li>{@code max-lifetime} - Maximum connection lifetime in milliseconds (default: 1800000)</li>
 * </ul>
 *
 * @see HikariDataSource
 * @see HikariCredentialsUpdater
 */
@Configuration
public class HikariDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(HikariDataSourceConfig.class);

    /** Reference to the created DataSource for cleanup. */
    private HikariDataSource createdDataSource;

    /** JDBC URL for the database connection. */
    @Value("${spring.datasource.url}")
    private String url;

    /** Fully qualified class name of the JDBC driver. */
    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    /** Initial database username for pool connections. */
    @Value("${spring.datasource.username}")
    private String username;

    /** Initial database password for pool connections. */
    @Value("${spring.datasource.password}")
    private String password;

    /** Name of the connection pool for identification and JMX registration. */
    @Value("${spring.datasource.hikari.pool-name:HikariPool}")
    private String poolName;

    /** Maximum number of connections in the pool (active + idle). */
    @Value("${spring.datasource.hikari.maximum-pool-size:10}")
    private int maximumPoolSize;

    /** Minimum number of idle connections the pool maintains. */
    @Value("${spring.datasource.hikari.minimum-idle:2}")
    private int minimumIdle;

    /** Milliseconds a connection can remain idle before being removed. */
    @Value("${spring.datasource.hikari.idle-timeout:30000}")
    private long idleTimeout;

    /** Milliseconds to wait when requesting a connection before timing out. */
    @Value("${spring.datasource.hikari.connection-timeout:20000}")
    private long connectionTimeout;

    /** Maximum lifetime of a connection in milliseconds before it is retired. */
    @Value("${spring.datasource.hikari.max-lifetime:1800000}")
    private long maxLifetime;

    /**
     * Creates the HikariCP configuration bean.
     *
     * <p>This configuration is used to create the {@link HikariDataSource}. Note that
     * while initial credentials are set here, the actual credentials used for connections
     * come from the {@link HikariCredentialsUpdater} via the credentials provider interface.</p>
     *
     * @return a configured HikariConfig
     */
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

    /**
     * Creates the primary HikariCP DataSource bean with credential rotation support.
     *
     * <p>This method wires up the credential rotation infrastructure:</p>
     * <ol>
     *   <li>Sets the {@link HikariCredentialsUpdater} as the credentials provider on the config</li>
     *   <li>Creates the {@link HikariDataSource} from the config</li>
     *   <li>Injects the DataSource back into the updater for connection eviction support</li>
     * </ol>
     *
     * <p>The resulting DataSource is marked as {@code @Primary}, making it the default
     * DataSource for the application when multiple DataSource beans exist.</p>
     *
     * @param hikariConfig        the HikariCP configuration
     * @param credentialsUpdater  the credentials updater that provides dynamic credentials
     * @return a configured HikariDataSource with credential rotation support
     */
    @Bean
    @Primary
    public HikariDataSource dataSource(HikariConfig hikariConfig, HikariCredentialsUpdater credentialsUpdater) {
        // Set the credentials provider BEFORE creating the datasource
        hikariConfig.setCredentialsProvider(credentialsUpdater);
        HikariDataSource dataSource = new HikariDataSource(hikariConfig);
        // Inject datasource back into updater for connection eviction
        credentialsUpdater.setDataSource(dataSource);
        // Store reference for cleanup
        this.createdDataSource = dataSource;
        return dataSource;
    }

    /**
     * Explicitly closes the HikariDataSource on application shutdown.
     *
     * <p>While Spring typically handles DataSource cleanup, explicit closure
     * ensures all connections are properly released.</p>
     */
    @PreDestroy
    public void closeDataSource() {
        if (createdDataSource != null && !createdDataSource.isClosed()) {
            log.info("Closing HikariDataSource: {}", createdDataSource.getPoolName());
            createdDataSource.close();
        }
    }

    /**
     * Creates the HikariCP credentials updater bean for credential rotation support.
     *
     * <p>The updater is initialized with the configured username and password, and is
     * registered with the {@link com.maybeitssquid.rotatingsecrets.CredentialsProviderService}
     * to receive notifications when credentials are rotated.</p>
     *
     * @return a credentials updater initialized with the configured credentials
     * @see HikariCredentialsUpdater
     */
    @Bean("hikariUpdater")
    public HikariCredentialsUpdater hikariCredentialsUpdater() {
        return new HikariCredentialsUpdater(username, password);
    }
}