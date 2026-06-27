package com.maybeitssquid.rotatingsecrets.ucp;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import oracle.ucp.jdbc.PoolDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UcpDataSourceConfigTest {

  private UcpDataSourceConfig config;

  @BeforeEach
  void setUp() {
    config = new UcpDataSourceConfig();
    ReflectionTestUtils.setField(config, "url", "jdbc:oracle:thin:@//localhost:1521/XEPDB1");
    ReflectionTestUtils.setField(
        config, "connectionFactoryClassName", "oracle.jdbc.pool.OracleDataSource");
    ReflectionTestUtils.setField(config, "user", "scott");
    ReflectionTestUtils.setField(config, "password", "tiger");
    ReflectionTestUtils.setField(config, "poolName", "TestUcpPool");
    ReflectionTestUtils.setField(config, "initialPoolSize", 3);
    ReflectionTestUtils.setField(config, "minPoolSize", 3);
    ReflectionTestUtils.setField(config, "maxPoolSize", 15);
    ReflectionTestUtils.setField(config, "connectionWaitTimeout", 25);
    ReflectionTestUtils.setField(config, "inactiveConnectionTimeout", 35);
    ReflectionTestUtils.setField(config, "maxConnectionReuseTime", 1234);
  }

  @Test
  void poolDataSource_appliesConfiguredProperties() throws SQLException {
    PoolDataSource pds = config.poolDataSource();

    assertEquals("TestUcpPool", pds.getConnectionPoolName());
    assertEquals("jdbc:oracle:thin:@//localhost:1521/XEPDB1", pds.getURL());
    assertEquals("scott", pds.getUser());
    assertEquals("oracle.jdbc.pool.OracleDataSource", pds.getConnectionFactoryClassName());
    assertEquals(3, pds.getInitialPoolSize());
    assertEquals(3, pds.getMinPoolSize());
    assertEquals(15, pds.getMaxPoolSize());
    assertEquals(25, pds.getConnectionWaitTimeout());
    assertEquals(35, pds.getInactiveConnectionTimeout());
    assertEquals(1234, pds.getMaxConnectionReuseTime());
  }

  @Test
  void ucpCredentialsUpdater_wrapsProvidedPool() throws SQLException {
    PoolDataSource pds = config.poolDataSource();

    UcpCredentialsUpdater updater = config.ucpCredentialsUpdater(pds);

    assertNotNull(updater);
    assertSame(pds, ReflectionTestUtils.getField(updater, "poolDataSource"));
  }
}
