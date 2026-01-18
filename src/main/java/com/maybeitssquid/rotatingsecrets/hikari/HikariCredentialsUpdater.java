package com.maybeitssquid.rotatingsecrets.hikari;

import com.maybeitssquid.rotatingsecrets.UpdatableCredential;
import com.zaxxer.hikari.HikariCredentialsProvider;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.util.Credentials;

public class HikariCredentialsUpdater implements UpdatableCredential<String>, HikariCredentialsProvider {

    private HikariDataSource dataSource;

    private Credentials credentials;

    public HikariCredentialsUpdater(String username, String password) {
        this.credentials = new Credentials(username, password);
    }

    public void setDataSource(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void setCredential(final String username, final String credential) {
        this.credentials = new Credentials(username, credential);
        if (dataSource != null && dataSource.getHikariPoolMXBean() != null) {
            dataSource.getHikariPoolMXBean().softEvictConnections();
        }
    }

    @Override
    public Credentials getCredentials() {
        return this.credentials;
    }
}
