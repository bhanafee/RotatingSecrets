package com.maybeitssquid.rotatingsecrets.ucp;

import com.maybeitssquid.rotatingsecrets.KubernetesCredentialsProvider;
import oracle.ucp.jdbc.PoolDataSource;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * A DataSource wrapper that provides dynamic credential rotation for Oracle UCP.
 *
 * <p>Oracle UCP does not have an equivalent to HikariCP's CredentialsProvider
 * interface. This wrapper intercepts {@link #getConnection()} calls and delegates
 * to {@link PoolDataSource#getConnection(String, String)} with fresh credentials
 * read from Kubernetes-mounted secret files.</p>
 *
 * <p>This allows the application to use standard {@code dataSource.getConnection()}
 * calls while still benefiting from credential rotation.</p>
 */
public class RotatingCredentialsDataSource implements DataSource {

    private final PoolDataSource poolDataSource;
    private final KubernetesCredentialsProvider credentialsProvider;

    /**
     * Creates a new rotating credentials DataSource.
     *
     * @param poolDataSource       the underlying Oracle UCP pool
     * @param credentialsProvider  provides fresh credentials from Kubernetes secrets
     */
    public RotatingCredentialsDataSource(PoolDataSource poolDataSource,
                                         KubernetesCredentialsProvider credentialsProvider) {
        this.poolDataSource = poolDataSource;
        this.credentialsProvider = credentialsProvider;
    }

    /**
     * Gets a connection using fresh credentials from Kubernetes secrets.
     *
     * <p>Reads the current username and password from the mounted secret files,
     * then delegates to the underlying pool's {@code getConnection(username, password)}
     * method. This ensures rotated credentials are used for new connections.</p>
     *
     * @return a connection to the database
     * @throws SQLException if a database access error occurs
     */
    @Override
    public Connection getConnection() throws SQLException {
        String username = credentialsProvider.getUsername();
        String password = credentialsProvider.getPassword();
        return poolDataSource.getConnection(username, password);
    }

    /**
     * Gets a connection using the specified credentials.
     *
     * <p>Delegates directly to the underlying pool without reading from
     * Kubernetes secrets.</p>
     *
     * @param username the database user
     * @param password the user's password
     * @return a connection to the database
     * @throws SQLException if a database access error occurs
     */
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return poolDataSource.getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return poolDataSource.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        poolDataSource.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        poolDataSource.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return poolDataSource.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("getParentLogger not supported");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        if (iface.isInstance(poolDataSource)) {
            return iface.cast(poolDataSource);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || iface.isInstance(poolDataSource);
    }
}
