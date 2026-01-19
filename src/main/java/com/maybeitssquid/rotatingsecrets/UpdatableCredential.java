package com.maybeitssquid.rotatingsecrets;

/**
 * Interface for components that need to receive updated credentials when they change.
 *
 * <p>This interface enables a publish-subscribe pattern for credential rotation. When
 * credentials are rotated (e.g., by reading new values from Kubernetes-mounted secret files),
 * all registered {@code UpdatableCredential} implementations are notified with the new values.</p>
 *
 * <p>Implementations typically wrap connection pools and handle the pool-specific logic
 * for updating credentials and refreshing connections. For example:</p>
 * <ul>
 *   <li>{@link com.maybeitssquid.rotatingsecrets.hikari.HikariCredentialsUpdater} - Updates HikariCP credentials and soft-evicts connections</li>
 *   <li>{@link com.maybeitssquid.rotatingsecrets.ucp.UcpCredentialsUpdater} - Updates Oracle UCP credentials and refreshes the pool</li>
 * </ul>
 *
 * @param <T> the type of the credential (typically {@link String} for passwords)
 * @see CredentialsProviderService
 */
public interface UpdatableCredential<T> {

    /**
     * Updates the credentials used by this component.
     *
     * <p>Implementations should atomically update their stored credentials and trigger
     * any necessary pool refresh operations. This method may be called from a scheduled
     * background thread, so implementations must be thread-safe.</p>
     *
     * @param username   the new username to use for connections
     * @param credential the new credential (password) to use for connections
     */
    void setCredential(String username, T credential);
}

