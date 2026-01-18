package com.maybeitssquid.rotatingsecrets.ucp;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.SQLException;

@Configuration
public class UcpDataSourceConfig {

    @Value("${spring.datasource.ucp.url}")
    private String url;

    @Value("${spring.datasource.ucp.connection-factory-class-name}")
    private String connectionFactoryClassName;

    @Value("${spring.datasource.ucp.user}")
    private String user;

    @Value("${spring.datasource.ucp.password}")
    private String password;

    @Value("${spring.datasource.ucp.pool-name:UCPPool}")
    private String poolName;

    @Value("${spring.datasource.ucp.initial-pool-size:2}")
    private int initialPoolSize;

    @Value("${spring.datasource.ucp.min-pool-size:2}")
    private int minPoolSize;

    @Value("${spring.datasource.ucp.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${spring.datasource.ucp.connection-wait-timeout:20}")
    private int connectionWaitTimeout;

    @Value("${spring.datasource.ucp.inactive-connection-timeout:30}")
    private int inactiveConnectionTimeout;

    @Value("${spring.datasource.ucp.max-connection-reuse-time:1800}")
    private int maxConnectionReuseTime;

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

    @Bean("ucpUpdater")
    public UcpCredentialsUpdater ucpCredentialsUpdater(PoolDataSource poolDataSource) {
        return new UcpCredentialsUpdater(poolDataSource);
    }
}