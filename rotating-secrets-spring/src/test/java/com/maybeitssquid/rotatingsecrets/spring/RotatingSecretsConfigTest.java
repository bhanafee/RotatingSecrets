package com.maybeitssquid.rotatingsecrets.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.maybeitssquid.rotatingsecrets.CredentialsProviderService;
import com.maybeitssquid.rotatingsecrets.UpdatableCredential;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RotatingSecretsConfigTest {

  @TempDir Path secrets;

  private ApplicationContextRunner runner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(RotatingSecretsConfig.class)
        .withPropertyValues("k8s.secrets.path=" + secrets);
  }

  private void writeSecrets(String username, String password) throws IOException {
    Files.writeString(secrets.resolve("username"), username);
    Files.writeString(secrets.resolve("password"), password);
  }

  @Test
  void createsProviderWithNoUpdaters() throws IOException {
    writeSecrets("sa", "pw");

    runner()
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              assertNotNull(context.getBean(CredentialsProviderService.class));
            });
  }

  @Test
  void registersEveryUpdaterBeanAndNotifiesOnStart() throws IOException {
    writeSecrets("sa", "pw");

    runner()
        .withBean("first", RecordingUpdater.class, RecordingUpdater::new)
        .withBean("second", RecordingUpdater.class, RecordingUpdater::new)
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              for (RecordingUpdater updater :
                  context.getBeansOfType(RecordingUpdater.class).values()) {
                assertEquals(List.of("sa:pw"), updater.received());
              }
            });
  }

  @Test
  void acceptsDurationAndPlainMilliseconds() throws IOException {
    writeSecrets("sa", "pw");

    runner()
        .withPropertyValues("k8s.secrets.refresh-interval=30s")
        .run(context -> assertNull(context.getStartupFailure()));
    runner()
        .withPropertyValues("k8s.secrets.refresh-interval=30000")
        .run(context -> assertNull(context.getStartupFailure()));
  }

  @Test
  void parsesUnitsIsoAndBareMilliseconds() {
    assertEquals(Duration.ofSeconds(30), RotatingSecretsConfig.parseRefreshInterval("30s"));
    assertEquals(Duration.ofMinutes(2), RotatingSecretsConfig.parseRefreshInterval("2m"));
    assertEquals(Duration.ofHours(1), RotatingSecretsConfig.parseRefreshInterval("1h"));
    assertEquals(Duration.ofDays(1), RotatingSecretsConfig.parseRefreshInterval("1d"));
    assertEquals(Duration.ofSeconds(45), RotatingSecretsConfig.parseRefreshInterval("PT45S"));
    assertEquals(Duration.ofMillis(30000), RotatingSecretsConfig.parseRefreshInterval("30000"));
    assertEquals(Duration.ofMillis(250), RotatingSecretsConfig.parseRefreshInterval(" 250ms "));
  }

  @Test
  void rejectsUnparseableIntervals() {
    assertThrows(
        DateTimeParseException.class, () -> RotatingSecretsConfig.parseRefreshInterval("soon"));
  }

  /** Records every credential it receives, so tests can assert on notifications. */
  static class RecordingUpdater implements UpdatableCredential<String> {
    private final List<String> received = new CopyOnWriteArrayList<>();

    @Override
    public void setCredential(String username, String credential) {
      received.add(username + ":" + credential);
    }

    List<String> received() {
      return received;
    }
  }
}
