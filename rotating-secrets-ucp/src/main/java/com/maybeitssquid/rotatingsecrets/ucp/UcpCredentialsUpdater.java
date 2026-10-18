package com.maybeitssquid.rotatingsecrets.ucp;

import com.maybeitssquid.rotatingsecrets.CredentialRotationException;
import com.maybeitssquid.rotatingsecrets.UpdatableCredential;
import java.sql.SQLException;
import java.util.Arrays;
import oracle.ucp.UniversalConnectionPoolException;
import oracle.ucp.admin.UniversalConnectionPoolManager;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import oracle.ucp.jdbc.PoolDataSource;

/**
 * Handles credential updates for Oracle Universal Connection Pool (UCP) data sources.
 *
 * <p>This class implements the {@link UpdatableCredential} interface to receive credential change
 * notifications from the {@link com.maybeitssquid.rotatingsecrets.CredentialsProviderService}. When
 * credentials are updated, it atomically updates the pool's username and password, then triggers a
 * pool refresh to ensure new connections use the updated credentials.
 *
 * <h2>Credential Update Process</h2>
 *
 * <ol>
 *   <li>Acquire a lock on the pool to ensure atomic credential updates
 *   <li>Update the username and password on the {@link PoolDataSource}
 *   <li>If the pool is registered with the manager, call {@link
 *       UniversalConnectionPoolManager#refreshConnectionPool(String)} to gracefully replace
 *       connections with ones using the new credentials. A pool that has not been created yet
 *       already uses the new credentials when it is created, so there is nothing to refresh
 * </ol>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>This class is thread-safe. Credential updates are synchronized on the pool data source to
 * ensure atomic username/password updates. The pool refresh operation is handled asynchronously by
 * UCP.
 *
 * @see UpdatableCredential
 * @see UcpDataSourceConfig
 * @see PoolDataSource
 */
public class UcpCredentialsUpdater implements UpdatableCredential<String> {

  /** The Oracle UCP pool data source to manage credentials for. */
  private final PoolDataSource poolDataSource;

  /** Supplies the UCP pool manager. Replaced in tests. */
  private final ManagerSource managers;

  /**
   * Creates a new credentials updater for the given Oracle UCP pool.
   *
   * @param poolDataSource the Oracle UCP PoolDataSource to manage
   */
  public UcpCredentialsUpdater(final PoolDataSource poolDataSource) {
    this(poolDataSource, UniversalConnectionPoolManagerImpl::getUniversalConnectionPoolManager);
  }

  /**
   * Creates an updater that gets the pool manager from the given source.
   *
   * @param poolDataSource the Oracle UCP PoolDataSource to manage
   * @param managers supplies the UCP pool manager
   */
  UcpCredentialsUpdater(final PoolDataSource poolDataSource, final ManagerSource managers) {
    this.poolDataSource = poolDataSource;
    this.managers = managers;
  }

  /**
   * Updates the credentials used by the Oracle UCP pool and refreshes connections if the pool
   * exists.
   *
   * <p>This method atomically updates the username and password on the pool, then refreshes the
   * pool if it is registered with the manager. The refresh operation gracefully replaces existing
   * connections with new ones using the updated credentials. Active connections continue to work
   * until they are returned to the pool.
   *
   * @param username the new database username
   * @param credential the new database password
   * @throws RuntimeException if the credentials cannot be updated or the pool cannot be refreshed
   */
  @Override
  public void setCredential(final String username, final String credential) {
    final String poolName = this.poolDataSource.getConnectionPoolName();
    try {
      synchronized (this.poolDataSource) {
        this.poolDataSource.setUser(username);
        this.poolDataSource.setPassword(credential);
      }
      final UniversalConnectionPoolManager mgr = managers.get();
      // A pool is registered with the manager only once it is created. Until then the new
      // credentials
      // are already on the PoolDataSource, so there is nothing to refresh.
      if (Arrays.asList(mgr.getConnectionPoolNames()).contains(poolName)) {
        mgr.refreshConnectionPool(poolName);
      }
    } catch (final SQLException e) {
      throw new CredentialRotationException(
          "Failed to update credentials in poolDataSource " + poolName, e);
    } catch (final UniversalConnectionPoolException e) {
      throw new CredentialRotationException("Failed to refresh poolDataSource " + poolName, e);
    }
  }

  /** Source of the UCP pool manager, so tests can substitute a mock. */
  @FunctionalInterface
  interface ManagerSource {
    /**
     * Returns the UCP pool manager.
     *
     * @return the pool manager
     * @throws UniversalConnectionPoolException if the manager cannot be obtained
     */
    UniversalConnectionPoolManager get() throws UniversalConnectionPoolException;
  }
}
