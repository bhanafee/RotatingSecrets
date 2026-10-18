package com.maybeitssquid.rotatingsecrets;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * Checks that the password never reaches a log record, including the one the watch loop writes when
 * an updater fails. Captures every event at TRACE so the debug-level reads are covered too.
 */
class CredentialsProviderServiceLoggingTest {

  private static final String OLD_PASSWORD = "old-Pa55w0rd-marker";
  private static final String NEW_PASSWORD = "new-Pa55w0rd-marker";

  @TempDir Path tempDir;

  private Path usernamePath;
  private Path passwordPath;
  private CredentialsProviderService service;
  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() throws IOException {
    usernamePath = tempDir.resolve("username");
    passwordPath = tempDir.resolve("password");
    Files.writeString(usernamePath, "dbuser");
    Files.writeString(passwordPath, OLD_PASSWORD);

    logger = (Logger) LoggerFactory.getLogger(CredentialsProviderService.class);
    logger.setLevel(Level.TRACE);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    service = new CredentialsProviderService(tempDir.toString(), 100);
  }

  @AfterEach
  void tearDown() {
    service.stop();
    logger.detachAppender(appender);
    logger.setLevel(null);
  }

  @Test
  void refreshCredentials_doesNotLogPasswordOnRotation() throws IOException {
    service.register((username, credential) -> {});
    service.refreshCredentials();

    Files.writeString(passwordPath, NEW_PASSWORD);
    service.refreshCredentials();

    assertFalse(appender.list.isEmpty(), "expected debug events, so the check is not vacuous");
    assertNoPasswordLogged();
  }

  @Test
  void watchLoop_doesNotLogPasswordWhenUpdaterFails() throws Exception {
    AtomicBoolean fail = new AtomicBoolean(false);
    service.register(
        (username, password) -> {
          if (fail.get()) {
            // Generic message, the way a pool adapter wraps its own failures.
            throw new IllegalStateException("pool refresh failed");
          }
        });
    service.start();

    fail.set(true);
    Files.writeString(passwordPath, NEW_PASSWORD);

    waitForEvent("Unexpected error in credential watch loop");
    assertNoPasswordLogged();
  }

  @Test
  void refreshCredentials_doesNotLogPasswordWhenPasswordFileUnreadable() throws IOException {
    service.register((username, credential) -> {});
    Files.delete(passwordPath);
    Files.createDirectory(passwordPath);

    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> service.refreshCredentials());

    assertFalse(thrown.getMessage().contains(OLD_PASSWORD));
    assertNoPasswordLogged();
  }

  private void waitForEvent(String message) throws InterruptedException {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
    while (System.nanoTime() < deadline) {
      if (appender.list.stream().anyMatch(e -> e.getFormattedMessage().startsWith(message))) {
        return;
      }
      Thread.sleep(10);
    }
    fail("timed out waiting for log event: " + message);
  }

  private void assertNoPasswordLogged() {
    for (ILoggingEvent event : appender.list) {
      String where = event.getLoggerName() + ": " + event.getFormattedMessage();
      assertFalse(containsPassword(event.getFormattedMessage()), "password in log: " + where);
      for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
        assertFalse(containsPassword(t.getMessage()), "password in exception of: " + where);
      }
    }
  }

  private static boolean containsPassword(String text) {
    return text != null && (text.contains(OLD_PASSWORD) || text.contains(NEW_PASSWORD));
  }
}
