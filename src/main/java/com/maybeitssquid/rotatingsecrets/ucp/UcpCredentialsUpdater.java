package com.maybeitssquid.rotatingsecrets.ucp;

import com.maybeitssquid.rotatingsecrets.UpdatableCredential;
import oracle.ucp.UniversalConnectionPoolException;
import oracle.ucp.admin.UniversalConnectionPoolManager;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import oracle.ucp.jdbc.PoolDataSource;

import java.sql.SQLException;

/**
 * Handles credential updates for Oracle Universal Connection Pool (UCP) data sources.
 *
 * <p>This class implements the {@link UpdatableCredential} interface to receive credential
 * change notifications from the {@link com.maybeitssquid.rotatingsecrets.CredentialsProviderService}.
 * When credentials are updated, it atomically updates the pool's username and password,
 * then triggers a pool refresh to ensure new connections use the updated credentials.</p>
 *
 * <h2>Credential Update Process</h2>
 * <ol>
 *   <li>Acquire a lock on the pool to ensure atomic credential updates</li>
 *   <li>Update the username and password on the {@link PoolDataSource}</li>
 *   <li>Call {@link UniversalConnectionPoolManager#refreshConnectionPool(String)} to
 *       gracefully replace connections with ones using the new credentials</li>
 * </ol>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. Credential updates are synchronized on the pool data source
 * to ensure atomic username/password updates. The pool refresh operation is handled
 * asynchronously by UCP.</p>
 *
 * @see UpdatableCredential
 * @see UcpDataSourceConfig
 * @see PoolDataSource
 */
public class UcpCredentialsUpdater implements UpdatableCredential<String> {

    /** The Oracle UCP pool data source to manage credentials for. */
    private final PoolDataSource poolDataSource;

    /**
     * Creates a new credentials updater for the given Oracle UCP pool.
     *
     * @param poolDataSource the Oracle UCP PoolDataSource to manage
     */
    public UcpCredentialsUpdater(final PoolDataSource poolDataSource) {
        this.poolDataSource = poolDataSource;
    }

    /**
     * Updates the credentials used by the Oracle UCP pool and refreshes connections.
     *
     * <p>This method atomically updates the username and password on the pool, then
     * triggers a pool refresh. The refresh operation gracefully replaces existing
     * connections with new ones using the updated credentials. Active connections
     * continue to work until they are returned to the pool.</p>
     *
     * @param username   the new database username
     * @param credential the new database password
     * @throws RuntimeException if the credentials cannot be updated or the pool
     *                          cannot be refreshed
     */
    @Override
    public void setCredential(final String username, final String credential) {
        final String poolName = this.poolDataSource.getConnectionPoolName();
        try {
            synchronized (this.poolDataSource) {
                this.poolDataSource.setUser(username);
                this.poolDataSource.setPassword(credential);
            }
            final UniversalConnectionPoolManager mgr = UniversalConnectionPoolManagerImpl.
                    getUniversalConnectionPoolManager();
            mgr.refreshConnectionPool(poolName);
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to update credentials in poolDataSource " + poolName, e);
        } catch (final UniversalConnectionPoolException e) {
            throw new RuntimeException("Failed to refresh poolDataSource " + poolName, e);
        }
    }
}
