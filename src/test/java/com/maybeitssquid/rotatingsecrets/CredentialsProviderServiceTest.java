package com.maybeitssquid.rotatingsecrets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CredentialsProviderServiceTest {

    @TempDir
    Path tempDir;

    private Path usernamePath;
    private Path passwordPath;
    private CredentialsProviderService service;

    @BeforeEach
    void setUp() throws IOException {
        usernamePath = tempDir.resolve("username");
        passwordPath = tempDir.resolve("password");

        // Create initial credentials
        Files.writeString(usernamePath, "testuser");
        Files.writeString(passwordPath, "testpass");

        service = new CredentialsProviderService(tempDir.toString());
    }

    @Test
    void refreshCredentials_updatesWhenFilesChange() throws IOException {
        // Setup mock updatable
        UpdatableCredential<String> mockUpdatable = mock(UpdatableCredential.class);
        service.setHikariUpdatable(mockUpdatable);

        // First refresh - should detect initial credentials
        service.refreshCredentials();
        verify(mockUpdatable).setCredential("testuser", "testpass");

        // Update credentials
        Files.writeString(usernamePath, "newuser");
        Files.writeString(passwordPath, "newpass");

        // Second refresh - should detect changes
        service.refreshCredentials();
        verify(mockUpdatable).setCredential("newuser", "newpass");
    }

    @Test
    void refreshCredentials_notifiesAllUpdatables() throws IOException {
        UpdatableCredential<String> updatable1 = mock(UpdatableCredential.class);
        UpdatableCredential<String> updatable2 = mock(UpdatableCredential.class);

        service.setHikariUpdatable(updatable1);
        service.setUcpUpdatable(updatable2);

        service.refreshCredentials();

        verify(updatable1).setCredential("testuser", "testpass");
        verify(updatable2).setCredential("testuser", "testpass");
    }

    @Test
    void refreshCredentials_skipsWhenFilesNotExist() throws IOException {
        UpdatableCredential<String> mockUpdatable = mock(UpdatableCredential.class);
        service.setHikariUpdatable(mockUpdatable);

        // Delete credential files
        Files.delete(usernamePath);
        Files.delete(passwordPath);

        // Should not throw and should not notify updatables
        assertDoesNotThrow(() -> service.refreshCredentials());
        verifyNoInteractions(mockUpdatable);
    }

    @Test
    void refreshCredentials_skipsWhenCredentialsUnchanged() throws IOException {
        UpdatableCredential<String> mockUpdatable = mock(UpdatableCredential.class);
        service.setHikariUpdatable(mockUpdatable);

        // First refresh
        service.refreshCredentials();

        // Reset mock
        reset(mockUpdatable);

        // Second refresh without changes
        service.refreshCredentials();

        // Should not notify since credentials unchanged
        verifyNoInteractions(mockUpdatable);
    }

    @Test
    void refreshCredentials_threadSafeUnderConcurrentAccess() throws Exception {
        AtomicInteger updateCount = new AtomicInteger(0);
        UpdatableCredential<String> countingUpdatable = (username, credential) -> updateCount.incrementAndGet();
        service.setHikariUpdatable(countingUpdatable);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // Submit concurrent refresh tasks
        for (int i = 0; i < threadCount; i++) {
            final int iteration = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Alternate between updating and keeping credentials
                    if (iteration % 2 == 0) {
                        Files.writeString(usernamePath, "user" + iteration);
                    }
                    service.refreshCredentials();
                } catch (Exception e) {
                    fail("Exception during concurrent refresh: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for completion
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "Threads did not complete in time");
        executor.shutdown();

        // Verify no exceptions occurred and at least one update happened
        assertTrue(updateCount.get() > 0, "Expected at least one credential update");
    }

    @Test
    void validateSecretFilePermissions_doesNotThrowWhenFilesExist() {
        // Should not throw when files exist
        assertDoesNotThrow(() -> service.validateSecretFilePermissions());
    }

    @Test
    void validateSecretFilePermissions_doesNotThrowWhenFilesMissing() throws IOException {
        Files.delete(usernamePath);
        Files.delete(passwordPath);

        // Should not throw when files don't exist
        assertDoesNotThrow(() -> service.validateSecretFilePermissions());
    }

    @Test
    void refreshCredentials_logsWarningWhenFilesDisappear() throws IOException {
        UpdatableCredential<String> mockUpdatable = mock(UpdatableCredential.class);
        service.setHikariUpdatable(mockUpdatable);

        // First refresh with files present
        service.refreshCredentials();
        verify(mockUpdatable).setCredential("testuser", "testpass");

        // Delete files
        Files.delete(usernamePath);
        Files.delete(passwordPath);

        // Second refresh should handle missing files gracefully
        assertDoesNotThrow(() -> service.refreshCredentials());

        // Verify no additional updates after files disappear
        verifyNoMoreInteractions(mockUpdatable);
    }
}
