package com.maybeitssquid.rotatingsecrets.hikari;

import com.maybeitssquid.rotatingsecrets.UpdatableCredential;
import com.zaxxer.hikari.HikariCredentialsProvider;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.util.Credentials;

/**
 * Handles credential updates for HikariCP data sources.
 *
 * <p>This class implements both {@link UpdatableCredential} and {@link HikariCredentialsProvider}
 * interfaces, serving as a bridge between the credential rotation system and HikariCP's
 * built-in credential provider mechanism. It receives credential change notifications from the
 * {@link com.maybeitssquid.rotatingsecrets.CredentialsProviderService} and provides those
 * credentials to HikariCP when new connections are created.</p>
 *
 * <h2>How It Works</h2>
 * <ol>
 *   <li>HikariCP is configured to use this class as its {@link HikariCredentialsProvider}</li>
 *   <li>When HikariCP needs credentials for a new connection, it calls {@link #getCredentials()}</li>
 *   <li>When credentials are rotated, {@link #setCredential(String, String)} is called</li>
 *   <li>The updater stores the new credentials and triggers soft eviction of existing connections</li>
 *   <li>Soft eviction marks connections for closure after they are returned to the pool</li>
 *   <li>New connections use the updated credentials from {@link #getCredentials()}</li>
 * </ol>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. The {@link Credentials} object is immutable and is replaced
 * atomically when credentials are updated. The soft eviction operation is thread-safe as
 * implemented by HikariCP.</p>
 *
 * @see UpdatableCredential
 * @see HikariCredentialsProvider
 * @see HikariDataSourceConfig
 */
public class HikariCredentialsUpdater implements UpdatableCredential<String>, HikariCredentialsProvider {

    /** Reference to the HikariCP DataSource, used for connection eviction. May be null during initialization. */
    private HikariDataSource dataSource;

    /** Current credentials to provide to HikariCP. Replaced atomically on credential updates. */
    private Credentials credentials;

    /**
     * Creates a new credentials updater with the specified initial credentials.
     *
     * @param username the initial database username
     * @param password the initial database password
     */
    public HikariCredentialsUpdater(String username, String password) {
        this.credentials = new Credentials(username, password);
    }

    /**
     * Sets the HikariCP DataSource reference for connection eviction.
     *
     * <p>This method is called after the DataSource is created to enable soft eviction
     * of connections when credentials are rotated. The circular dependency (DataSource needs
     * credentials provider, credentials provider needs DataSource for eviction) is resolved
     * by injecting the DataSource after creation.</p>
     *
     * @param dataSource the HikariCP DataSource to manage
     */
    public void setDataSource(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Updates the stored credentials and soft-evicts existing connections.
     *
     * <p>This method atomically replaces the stored credentials with new ones, then
     * triggers a soft eviction of all existing connections in the pool. Soft eviction
     * marks connections for closure after they are returned to the pool, allowing
     * in-flight transactions to complete while ensuring new checkouts get fresh
     * connections with the updated credentials.</p>
     *
     * @param username   the new database username
     * @param credential the new database password
     */
    @Override
    public void setCredential(final String username, final String credential) {
        this.credentials = new Credentials(username, credential);
        if (dataSource != null && dataSource.getHikariPoolMXBean() != null) {
            dataSource.getHikariPoolMXBean().softEvictConnections();
        }
    }

    /**
     * Provides the current credentials to HikariCP for new connection creation.
     *
     * <p>This method is called by HikariCP's connection factory when creating new
     * connections. It returns the most recently set credentials.</p>
     *
     * @return the current credentials for database connections
     */
    @Override
    public Credentials getCredentials() {
        return this.credentials;
    }
}
