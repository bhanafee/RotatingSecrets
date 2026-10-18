package com.maybeitssquid.rotatingsecrets;

import com.maybeitssquid.rotatingsecrets.hikari.HikariCredentialsUpdater;
import com.maybeitssquid.rotatingsecrets.ucp.UcpCredentialsUpdater;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the credential provider for the demo and registers the pool updaters with it.
 *
 * <p>The library does not create a provider itself. The application creates one, registers the
 * updaters for the pools it uses, and starts it.
 */
@Configuration
public class DemoRotatingSecretsConfig {

  /** Default constructor. */
  public DemoRotatingSecretsConfig() {
    // Spring configuration for the demo's credential provider
  }

  /**
   * Creates the credential provider, registers both pool updaters, and starts watching the secrets
   * directory.
   *
   * @param secretsPath directory with the {@code username} and {@code password} files
   * @param refreshInterval fallback re-check interval, such as {@code 30s}
   * @param hikariUpdater updater for the HikariCP pool
   * @param ucpUpdater updater for the Oracle UCP pool
   * @return the started credential provider
   */
  @Bean(initMethod = "start", destroyMethod = "stop")
  public CredentialsProviderService credentialsProvider(
      @Value("${k8s.secrets.path:/var/run/secrets/database}") String secretsPath,
      @Value("${k8s.secrets.refresh-interval:30s}") Duration refreshInterval,
      HikariCredentialsUpdater hikariUpdater,
      UcpCredentialsUpdater ucpUpdater) {
    CredentialsProviderService provider =
        new CredentialsProviderService(secretsPath, refreshInterval.toMillis());
    provider.register(hikariUpdater);
    provider.register(ucpUpdater);
    return provider;
  }
}
