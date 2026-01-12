package com.maybeitssquid.rotatingsecrets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KubernetesCredentialsProvider integration.
 *
 * <p>Note: Full DataSourceConfig integration tests require an Oracle database
 * since Oracle UCP is Oracle-specific. These tests verify credential provider
 * behavior which is database-agnostic.</p>
 */
class DataSourceConfigTest {

    @TempDir
    Path tempDir;

    private KubernetesCredentialsProvider credentialsProvider;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(tempDir.resolve("jdbc-url"), "jdbc:oracle:thin:@//localhost:1521/XEPDB1");
        Files.writeString(tempDir.resolve("username"), "testuser");
        Files.writeString(tempDir.resolve("password"), "testpass");

        credentialsProvider = new KubernetesCredentialsProvider(tempDir.toString());
    }

    @Test
    void credentialsProvider_readsJdbcUrl() {
        String jdbcUrl = credentialsProvider.getJdbcUrl();

        assertEquals("jdbc:oracle:thin:@//localhost:1521/XEPDB1", jdbcUrl);
    }

    @Test
    void credentialsProvider_readsUsername() {
        String username = credentialsProvider.getUsername();

        assertEquals("testuser", username);
    }

    @Test
    void credentialsProvider_readsPassword() {
        String password = credentialsProvider.getPassword();

        assertEquals("testpass", password);
    }

    @Test
    void credentialsProvider_detectsRotatedCredentials() throws IOException {
        assertEquals("testuser", credentialsProvider.getUsername());

        Files.writeString(tempDir.resolve("username"), "rotateduser");
        Files.writeString(tempDir.resolve("password"), "rotatedpass");

        assertEquals("rotateduser", credentialsProvider.getUsername());
        assertEquals("rotatedpass", credentialsProvider.getPassword());
    }
}
