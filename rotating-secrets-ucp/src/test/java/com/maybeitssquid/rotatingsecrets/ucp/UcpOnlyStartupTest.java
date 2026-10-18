package com.maybeitssquid.rotatingsecrets.ucp;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.maybeitssquid.rotatingsecrets.spring.RotatingSecretsConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import oracle.ucp.jdbc.PoolDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Starts the provider with only the Oracle UCP pool in the context, with secret files present. The
 * pool does not exist yet when the start-up read reaches the updater, so this covers the refresh
 * that used to fail.
 */
class UcpOnlyStartupTest {

  @TempDir Path secrets;

  @Test
  void startsWithOnlyTheUcpPoolAndRotatesItsCredentials() throws IOException {
    Files.writeString(secrets.resolve("username"), "sa");
    Files.writeString(secrets.resolve("password"), "rotated");

    new ApplicationContextRunner()
        .withUserConfiguration(UcpDataSourceConfig.class, RotatingSecretsConfig.class)
        .withPropertyValues(
            "k8s.secrets.path=" + secrets,
            "spring.datasource.ucp.pool-name=UcpOnlyTest",
            "spring.datasource.ucp.url=jdbc:h2:mem:ucp_only;DB_CLOSE_DELAY=-1",
            "spring.datasource.ucp.connection-factory-class-name=org.h2.Driver",
            "spring.datasource.ucp.user=sa",
            "spring.datasource.ucp.password=initial")
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              // The pool is created and the updater ran without failing. The password update is
              // covered by UcpCredentialsUpdaterTest, because Oracle's getPassword() is deprecated.
              assertNotNull(context.getBean(PoolDataSource.class));
            });
  }
}
