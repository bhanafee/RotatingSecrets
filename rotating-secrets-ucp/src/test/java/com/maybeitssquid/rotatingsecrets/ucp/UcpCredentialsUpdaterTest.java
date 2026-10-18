package com.maybeitssquid.rotatingsecrets.ucp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.maybeitssquid.rotatingsecrets.CredentialRotationException;
import java.sql.SQLException;
import oracle.ucp.UniversalConnectionPoolException;
import oracle.ucp.admin.UniversalConnectionPoolManager;
import oracle.ucp.jdbc.PoolDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UcpCredentialsUpdaterTest {

  private PoolDataSource poolDataSource;
  private UcpCredentialsUpdater updater;

  @BeforeEach
  void setUp() {
    poolDataSource = mock(PoolDataSource.class);
    when(poolDataSource.getConnectionPoolName()).thenReturn("TestPool-" + System.nanoTime());
    updater = new UcpCredentialsUpdater(poolDataSource);
  }

  @Test
  void setCredential_updatesUserAndPasswordOfUnregisteredPool() throws Exception {
    // The pool has not been created, so there is nothing to refresh and no exception.
    assertDoesNotThrow(() -> updater.setCredential("newUser", "newPass"));

    verify(poolDataSource).setUser("newUser");
    verify(poolDataSource).setPassword("newPass");
  }

  @Test
  void setCredential_wrapsSqlExceptionFromSetUser() throws Exception {
    SQLException cause = new SQLException("bad user");
    doThrow(cause).when(poolDataSource).setUser(anyString());

    CredentialRotationException thrown =
        assertThrows(
            CredentialRotationException.class, () -> updater.setCredential("user", "pass"));

    assertSame(cause, thrown.getCause());
    assertTrue(thrown.getMessage().contains("update credentials"));
    // Pool refresh must not run if updating the credentials failed.
    verify(poolDataSource, never()).setPassword(anyString());
  }

  @Test
  void setCredential_wrapsSqlExceptionFromSetPassword() throws Exception {
    SQLException cause = new SQLException("bad password");
    doThrow(cause).when(poolDataSource).setPassword(anyString());

    CredentialRotationException thrown =
        assertThrows(
            CredentialRotationException.class, () -> updater.setCredential("user", "pass"));

    assertSame(cause, thrown.getCause());
    assertTrue(thrown.getMessage().contains("update credentials"));
  }

  @Test
  void setCredential_wrapsRefreshFailureForRegisteredPool() throws Exception {
    UniversalConnectionPoolManager manager = mock(UniversalConnectionPoolManager.class);
    String poolName = poolDataSource.getConnectionPoolName();
    when(manager.getConnectionPoolNames()).thenReturn(new String[] {poolName});
    UniversalConnectionPoolException cause = new UniversalConnectionPoolException("refresh failed");
    doThrow(cause).when(manager).refreshConnectionPool(poolName);

    updater = new UcpCredentialsUpdater(poolDataSource, () -> manager);

    CredentialRotationException thrown =
        assertThrows(
            CredentialRotationException.class, () -> updater.setCredential("user", "pass"));

    assertSame(cause, thrown.getCause());
    assertTrue(thrown.getMessage().contains("refresh"));
  }

  @Test
  void setCredential_refreshesRegisteredPool() throws Exception {
    UniversalConnectionPoolManager manager = mock(UniversalConnectionPoolManager.class);
    String poolName = poolDataSource.getConnectionPoolName();
    when(manager.getConnectionPoolNames()).thenReturn(new String[] {poolName});

    updater = new UcpCredentialsUpdater(poolDataSource, () -> manager);

    updater.setCredential("user", "pass");

    verify(manager).refreshConnectionPool(poolName);
  }
}
