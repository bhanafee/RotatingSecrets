package com.maybeitssquid.rotatingsecrets.hikari;

import com.zaxxer.hikari.util.Credentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class KubernetesCredentialsProviderTest {

    @TempDir
    Path tempDir;

    private KubernetesCredentialsProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(tempDir.resolve("jdbc-url"), "jdbc:h2:mem:testdb");
        Files.writeString(tempDir.resolve("username"), "testuser");
        Files.writeString(tempDir.resolve("password"), "testpass");

        provider = new KubernetesCredentialsProvider(tempDir.toString());
    }

    @Test
    void getCredentials_returnsUsernameAndPassword() {
        Credentials credentials = provider.getCredentials();

        assertEquals("testuser", credentials.getUsername());
        assertEquals("testpass", credentials.getPassword());
    }

    @Test
    void getJdbcUrl_returnsUrl() {
        String jdbcUrl = provider.getJdbcUrl();

        assertEquals("jdbc:h2:mem:testdb", jdbcUrl);
    }

    @Test
    void getCredentials_trimsWhitespace() throws IOException {
        Files.writeString(tempDir.resolve("username"), "  spaceduser  \n");
        Files.writeString(tempDir.resolve("password"), "\tspacedpass\t\n");

        Credentials credentials = provider.getCredentials();

        assertEquals("spaceduser", credentials.getUsername());
        assertEquals("spacedpass", credentials.getPassword());
    }

    @Test
    void getCredentials_readsUpdatedValues() throws IOException {
        Credentials first = provider.getCredentials();
        assertEquals("testuser", first.getUsername());

        Files.writeString(tempDir.resolve("username"), "rotateduser");
        Files.writeString(tempDir.resolve("password"), "rotatedpass");

        Credentials second = provider.getCredentials();
        assertEquals("rotateduser", second.getUsername());
        assertEquals("rotatedpass", second.getPassword());
    }

    @Test
    void constructor_throwsWhenSecretsPathMissing() {
        KubernetesCredentialsProvider badProvider =
                new KubernetesCredentialsProvider("/nonexistent/path");

        assertThrows(RuntimeException.class, badProvider::getCredentials);
    }
}
