package com.maybeitssquid.rotatingsecrets.hikari;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.maybeitssquid.rotatingsecrets.spring.RotatingSecretsConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Starts the provider with only the HikariCP pool in the context. Before updater discovery was by
 * type, the provider required both pool beans by name, so an app with one pool failed here.
 */
class HikariOnlyStartupTest {

  @TempDir Path secrets;

  @Test
  void startsWithOnlyTheHikariPoolAndRotatesItsCredentials() throws IOException {
    Files.writeString(secrets.resolve("username"), "sa");
    Files.writeString(secrets.resolve("password"), "rotated");

    new ApplicationContextRunner()
        .withUserConfiguration(HikariDataSourceConfig.class, RotatingSecretsConfig.class)
        .withPropertyValues(
            "k8s.secrets.path=" + secrets,
            "spring.datasource.url=jdbc:h2:mem:hikari_only;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=initial")
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              HikariCredentialsUpdater updater = context.getBean(HikariCredentialsUpdater.class);
              // The start-up read from the secret files reached the Hikari updater.
              assertEquals("rotated", updater.getCredentials().getPassword());
            });
  }
}
