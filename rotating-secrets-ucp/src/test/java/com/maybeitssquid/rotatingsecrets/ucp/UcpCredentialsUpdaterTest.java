package com.maybeitssquid.rotatingsecrets.ucp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.maybeitssquid.rotatingsecrets.CredentialRotationException;
import java.sql.SQLException;
import oracle.ucp.UniversalConnectionPoolException;
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
  void setCredential_updatesUserAndPasswordBeforeRefresh() throws Exception {
    // The pool name is not registered with the real UCP manager, so refreshConnectionPool
    // will fail. We only assert that the credentials were pushed onto the pool first.
    try {
      updater.setCredential("newUser", "newPass");
    } catch (CredentialRotationException expected) {
      // Refreshing an unregistered pool is expected to fail; not the focus of this test.
    }

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
  void setCredential_wrapsRefreshFailureForUnknownPool() {
    // The mocked pool is never registered with the UCP manager, so refreshConnectionPool
    // throws a UniversalConnectionPoolException, which must be wrapped.
    CredentialRotationException thrown =
        assertThrows(
            CredentialRotationException.class, () -> updater.setCredential("user", "pass"));

    assertInstanceOf(UniversalConnectionPoolException.class, thrown.getCause());
    assertTrue(thrown.getMessage().contains("refresh"));
  }
}
