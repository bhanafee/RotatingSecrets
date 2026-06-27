package com.maybeitssquid.rotatingsecrets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CredentialsProviderServiceTest {

  @TempDir Path tempDir;

  private Path usernamePath;
  private Path passwordPath;
  private CredentialsProviderService service;

  @BeforeEach
  void setUp() throws IOException {
    usernamePath = tempDir.resolve("username");
    passwordPath = tempDir.resolve("password");

    Files.writeString(usernamePath, "testuser");
    Files.writeString(passwordPath, "testpass");

    // start() is NOT called here; tests call refreshCredentials() or start() directly.
    service = new CredentialsProviderService(tempDir.toString(), 30000);
  }

  @AfterEach
  void tearDown() {
    service.stop();
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
    UpdatableCredential<String> countingUpdatable =
        (username, credential) -> updateCount.incrementAndGet();
    service.setHikariUpdatable(countingUpdatable);

    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    // Submit concurrent refresh tasks
    for (int i = 0; i < threadCount; i++) {
      final int iteration = i;
      executor.submit(
          () -> {
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
  void start_doesNotThrowWhenFilesExist() {
    assertDoesNotThrow(() -> service.start());
  }

  @Test
  void start_doesNotThrowWhenFilesMissing() throws IOException {
    Files.delete(usernamePath);
    Files.delete(passwordPath);

    assertDoesNotThrow(() -> service.start());
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

  @Test
  void refreshCredentials_resumesAfterFilesReappear() throws IOException {
    UpdatableCredential<String> mockUpdatable = mock(UpdatableCredential.class);
    service.setHikariUpdatable(mockUpdatable);

    service.refreshCredentials();
    verify(mockUpdatable).setCredential("testuser", "testpass");

    // Files vanish (e.g. during an atomic secret swap)
    Files.delete(usernamePath);
    Files.delete(passwordPath);
    service.refreshCredentials();

    // Files reappear with rotated values
    Files.writeString(usernamePath, "rotateduser");
    Files.writeString(passwordPath, "rotatedpass");
    service.refreshCredentials();

    verify(mockUpdatable).setCredential("rotateduser", "rotatedpass");
  }

  @Test
  void refreshCredentials_throwsWhenSecretUnreadable() throws IOException {
    // Replace the username file with a directory so readString fails with IOException
    // even though Files.exists() returns true.
    Files.delete(usernamePath);
    Files.createDirectory(usernamePath);

    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> service.refreshCredentials());
    assertTrue(thrown.getMessage().contains("username"));
  }

  @Test
  void refreshCredentials_trimsWhitespaceFromSecrets() throws IOException {
    UpdatableCredential<String> mockUpdatable = mock(UpdatableCredential.class);
    service.setHikariUpdatable(mockUpdatable);

    Files.writeString(usernamePath, "  spaced-user\n");
    Files.writeString(passwordPath, "\tspaced-pass  \n");

    service.refreshCredentials();

    verify(mockUpdatable).setCredential("spaced-user", "spaced-pass");
  }

  @Test
  void start_toleratesWorldReadableSecretFiles() throws IOException {
    try {
      Files.setPosixFilePermissions(
          usernamePath, java.util.EnumSet.allOf(PosixFilePermission.class));
      Files.setPosixFilePermissions(
          passwordPath, java.util.EnumSet.allOf(PosixFilePermission.class));
    } catch (UnsupportedOperationException e) {
      org.junit.jupiter.api.Assumptions.abort(
          "Non-POSIX filesystem; permission check not exercised");
    }

    // World-readable files trigger only a security warning, not a failure.
    assertDoesNotThrow(() -> service.start());
  }

  @Test
  void start_watchThreadPicksUpRotatedCredentials() throws Exception {
    UpdatableCredential<String> mockUpdatable = mock(UpdatableCredential.class);
    // Short fallback interval so the watch loop re-checks quickly even without an OS event.
    service = new CredentialsProviderService(tempDir.toString(), 100);
    service.setHikariUpdatable(mockUpdatable);

    service.start();
    verify(mockUpdatable, timeout(2000)).setCredential("testuser", "testpass");

    Files.writeString(usernamePath, "watcheduser");
    Files.writeString(passwordPath, "watchedpass");

    verify(mockUpdatable, timeout(2000)).setCredential("watcheduser", "watchedpass");
  }
}
