package com.maybeitssquid.rotatingsecrets.ucp;

import com.maybeitssquid.rotatingsecrets.UpdatableCredential;
import oracle.ucp.UniversalConnectionPoolException;
import oracle.ucp.admin.UniversalConnectionPoolManager;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import oracle.ucp.jdbc.PoolDataSource;
import java.sql.SQLException;

public class UcpCredentialsUpdater implements UpdatableCredential<String> {

    private final PoolDataSource poolDataSource;

    public UcpCredentialsUpdater(final PoolDataSource poolDataSource) {
        this.poolDataSource = poolDataSource;
    }

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
