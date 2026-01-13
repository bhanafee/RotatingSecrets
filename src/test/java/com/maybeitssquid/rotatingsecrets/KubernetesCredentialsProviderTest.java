package com.maybeitssquid.rotatingsecrets;

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

    private CredentialsProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(tempDir.resolve("username"), "testuser");
        Files.writeString(tempDir.resolve("password"), "testpass");

        provider = new CredentialsProvider(tempDir.toString());
    }

    @Test
    void getUsername_returnsUsername() {
        String username = provider.getCurrentUsername();

        assertEquals("testuser", username);
    }

    @Test
    void getPassword_returnsPassword() {
        String password = provider.getCurrentPassword();

        assertEquals("testpass", password);
    }

    @Test
    void getCredentials_trimsWhitespace() throws IOException {
        Files.writeString(tempDir.resolve("username"), "  spaceduser  \n");
        Files.writeString(tempDir.resolve("password"), "\tspacedpass\t\n");

        assertEquals("spaceduser", provider.getCurrentUsername());
        assertEquals("spacedpass", provider.getCurrentPassword());
    }

    @Test
    void getCredentials_readsUpdatedValues() throws IOException {
        assertEquals("testuser", provider.getCurrentUsername());

        Files.writeString(tempDir.resolve("username"), "rotateduser");
        Files.writeString(tempDir.resolve("password"), "rotatedpass");

        assertEquals("rotateduser", provider.getCurrentUsername());
        assertEquals("rotatedpass", provider.getCurrentPassword());
    }

    @Test
    void constructor_throwsWhenSecretsPathMissing() {
        CredentialsProvider badProvider =
                new CredentialsProvider("/nonexistent/path");

        assertThrows(RuntimeException.class, badProvider::getCurrentUsername);
    }
}
