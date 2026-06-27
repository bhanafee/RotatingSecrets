package com.maybeitssquid.rotatingsecrets.hikari;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class HikariDataSourceConfigTest {

  private HikariDataSourceConfig config;
  private HikariDataSource dataSource;

  @BeforeEach
  void setUp() {
    config = new HikariDataSourceConfig();
    ReflectionTestUtils.setField(config, "url", "jdbc:h2:mem:configtest;DB_CLOSE_DELAY=-1");
    ReflectionTestUtils.setField(config, "driverClassName", "org.h2.Driver");
    ReflectionTestUtils.setField(config, "username", "sa");
    ReflectionTestUtils.setField(config, "password", "");
    ReflectionTestUtils.setField(config, "poolName", "TestPool");
    ReflectionTestUtils.setField(config, "maximumPoolSize", 5);
    ReflectionTestUtils.setField(config, "minimumIdle", 1);
    ReflectionTestUtils.setField(config, "idleTimeout", 30000L);
    ReflectionTestUtils.setField(config, "connectionTimeout", 20000L);
    ReflectionTestUtils.setField(config, "maxLifetime", 1800000L);
  }

  @AfterEach
  void tearDown() {
    if (dataSource != null && !dataSource.isClosed()) {
      dataSource.close();
    }
  }

  @Test
  void hikariConfig_appliesConfiguredProperties() {
    HikariConfig hikariConfig = config.hikariConfig();

    assertEquals("jdbc:h2:mem:configtest;DB_CLOSE_DELAY=-1", hikariConfig.getJdbcUrl());
    assertEquals("org.h2.Driver", hikariConfig.getDriverClassName());
    assertEquals("sa", hikariConfig.getUsername());
    assertEquals("TestPool", hikariConfig.getPoolName());
    assertEquals(5, hikariConfig.getMaximumPoolSize());
    assertEquals(1, hikariConfig.getMinimumIdle());
    assertEquals(1800000L, hikariConfig.getMaxLifetime());
  }

  @Test
  void hikariCredentialsUpdater_seededWithConfiguredCredentials() {
    HikariCredentialsUpdater updater = config.hikariCredentialsUpdater();

    assertEquals("sa", updater.getCredentials().getUsername());
    assertEquals("", updater.getCredentials().getPassword());
  }

  @Test
  void dataSource_wiresCredentialsProviderAndInjectsDataSourceBack() throws Exception {
    HikariCredentialsUpdater updater = config.hikariCredentialsUpdater();
    HikariConfig hikariConfig = config.hikariConfig();

    dataSource = config.dataSource(hikariConfig, updater);

    // The updater is registered as the provider before the pool is built.
    assertSame(updater, hikariConfig.getCredentialsProvider());

    // The data source was injected back into the updater, so credential updates can evict.
    assertDoesNotThrow(() -> updater.setCredential("sa", ""));

    // The wired pool is actually usable.
    try (Connection conn = dataSource.getConnection()) {
      assertFalse(conn.isClosed());
    }
  }

  @Test
  void closeDataSource_closesCreatedPool() {
    HikariCredentialsUpdater updater = config.hikariCredentialsUpdater();
    dataSource = config.dataSource(config.hikariConfig(), updater);
    assertFalse(dataSource.isClosed());

    config.closeDataSource();

    assertTrue(dataSource.isClosed());
  }

  @Test
  void closeDataSource_isNoOpWhenNoDataSourceCreated() {
    // No dataSource() call has happened, so there is nothing to close.
    assertDoesNotThrow(() -> config.closeDataSource());
  }
}
