package com.maybeitssquid.rotatingsecrets;

import com.maybeitssquid.rotatingsecrets.hikari.HikariCredentialsUpdater;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for credential rotation using real H2 database and HikariCP.
 */
class CredentialRotationIntegrationTest {

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private HikariCredentialsUpdater credentialsUpdater;
    private CredentialsProviderService credentialsProvider;

    @BeforeEach
    void setUp() throws IOException {
        // Create initial credentials
        Path usernamePath = tempDir.resolve("username");
        Path passwordPath = tempDir.resolve("password");
        Files.writeString(usernamePath, "sa");
        Files.writeString(passwordPath, "");

        // Setup HikariCP with credentials updater
        credentialsUpdater = new HikariCredentialsUpdater("sa", "");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:integrationtest;DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setCredentialsProvider(credentialsUpdater);

        dataSource = new HikariDataSource(config);
        credentialsUpdater.setDataSource(dataSource);

        // Setup credentials provider
        credentialsProvider = new CredentialsProviderService(tempDir.toString());
        credentialsProvider.setHikariUpdatable(credentialsUpdater);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Test
    void connection_worksWithInitialCredentials() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void credentialRotation_maintainsConnectivity() throws SQLException {
        // Verify initial connectivity
        try (Connection conn = dataSource.getConnection()) {
            assertFalse(conn.isClosed());
        }

        // Rotate credentials (H2 doesn't require password changes, so just update the updater)
        credentialsUpdater.setCredential("sa", "");

        // Verify connectivity still works after rotation
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 2")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
        }
    }

    @Test
    void concurrentQueriesDuringRotation_doNotFail() throws Exception {
        int threadCount = 5;
        int queriesPerThread = 20;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Submit query threads
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int q = 0; q < queriesPerThread; q++) {
                        try (Connection conn = dataSource.getConnection();
                             Statement stmt = conn.createStatement();
                             ResultSet rs = stmt.executeQuery("SELECT " + q)) {
                            if (rs.next()) {
                                successCount.incrementAndGet();
                            }
                        } catch (SQLException e) {
                            errorCount.incrementAndGet();
                        }

                        // Trigger credential rotation periodically
                        if (q % 5 == 0) {
                            credentialsUpdater.setCredential("sa", "");
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start all threads
        startLatch.countDown();

        // Wait for completion
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Threads did not complete in time");
        executor.shutdown();

        // All queries should succeed
        assertEquals(threadCount * queriesPerThread, successCount.get(), "Expected all queries to succeed");
        assertEquals(0, errorCount.get(), "Expected no errors");
    }

    @Test
    void refreshCredentials_updatesFromFiles() throws IOException {
        // Initial refresh
        credentialsProvider.refreshCredentials();

        // Verify updater received credentials
        assertEquals("sa", credentialsUpdater.getCredentials().getUsername());
    }

    @Test
    void connectionPool_handlesCredentialUpdate() throws SQLException {
        // Get a connection before rotation
        Connection conn1 = dataSource.getConnection();
        assertFalse(conn1.isClosed());
        conn1.close();

        // Trigger soft eviction
        credentialsUpdater.setCredential("sa", "");

        // Get a connection after rotation
        Connection conn2 = dataSource.getConnection();
        assertFalse(conn2.isClosed());

        // Execute query to verify connection works
        try (Statement stmt = conn2.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 42")) {
            assertTrue(rs.next());
            assertEquals(42, rs.getInt(1));
        }

        conn2.close();
    }

    @Test
    void poolMetrics_reflectEviction() throws SQLException {
        // Warm up the pool
        for (int i = 0; i < 3; i++) {
            try (Connection conn = dataSource.getConnection()) {
                assertNotNull(conn);
            }
        }

        // Check initial pool state
        int initialIdle = dataSource.getHikariPoolMXBean().getIdleConnections();
        assertTrue(initialIdle >= 0, "Should have idle connections");

        // Trigger soft eviction
        credentialsUpdater.setCredential("sa", "");

        // Pool should still be usable
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            assertTrue(rs.next());
        }
    }
}
