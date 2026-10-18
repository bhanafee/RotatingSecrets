package com.maybeitssquid.rotatingsecrets.spring;

import com.maybeitssquid.rotatingsecrets.CredentialsProviderService;
import com.maybeitssquid.rotatingsecrets.UpdatableCredential;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that creates and starts the credential provider.
 *
 * <p>Every {@link UpdatableCredential}{@code <String>} bean in the context is registered with the
 * provider, so an application needs no bean names. Any subset of pools works: one, the other, or
 * both.
 *
 * <h2>Configuration Properties</h2>
 *
 * <ul>
 *   <li>{@code k8s.secrets.path} - Directory with the {@code username} and {@code password} files
 *       (default: {@code /var/run/secrets/database})
 *   <li>{@code k8s.secrets.refresh-interval} - Fallback re-check interval. Accepts {@code 30s},
 *       {@code 2m}, {@code 1h}, {@code 1d}, an ISO-8601 duration such as {@code PT30S}, or a bare
 *       number of milliseconds (default: {@code 30s})
 * </ul>
 *
 * @see CredentialsProviderService
 */
@Configuration
public class RotatingSecretsConfig {

  /** A number with an optional unit. A bare number is milliseconds. */
  private static final Pattern SIMPLE_DURATION = Pattern.compile("(\\d+)(ms|s|m|h|d)?");

  /** Default constructor. */
  public RotatingSecretsConfig() {
    // Spring configuration for the credential provider
  }

  /**
   * Creates the credential provider, registers every updater bean, and starts it.
   *
   * <p>Updaters are discovered by type. Registration happens before {@code start()}, so the first
   * credential read reaches every updater.
   *
   * @param secretsPath directory with the {@code username} and {@code password} files
   * @param refreshInterval fallback re-check interval, parsed by {@link #parseRefreshInterval}
   * @param updatables all {@code UpdatableCredential<String>} beans in the context
   * @return the started credential provider
   */
  @Bean(initMethod = "start", destroyMethod = "stop")
  public CredentialsProviderService credentialsProvider(
      @Value("${k8s.secrets.path:/var/run/secrets/database}") String secretsPath,
      @Value("${k8s.secrets.refresh-interval:30s}") String refreshInterval,
      ObjectProvider<UpdatableCredential<String>> updatables) {
    CredentialsProviderService provider =
        new CredentialsProviderService(
            secretsPath, parseRefreshInterval(refreshInterval).toMillis());
    updatables.orderedStream().forEach(provider::register);
    return provider;
  }

  /**
   * Parses the refresh interval without relying on Spring Boot's conversion service, so plain
   * Spring applications accept the same values.
   *
   * @param value {@code 30s}, {@code 2m}, {@code 1h}, {@code 1d}, an ISO-8601 duration such as
   *     {@code PT30S}, or a bare number of milliseconds
   * @return the parsed duration
   * @throws java.time.format.DateTimeParseException if the value is neither form
   */
  static Duration parseRefreshInterval(String value) {
    String trimmed = value.trim();
    Matcher matcher = SIMPLE_DURATION.matcher(trimmed);
    if (!matcher.matches()) {
      return Duration.parse(trimmed);
    }
    long amount = Long.parseLong(matcher.group(1));
    String unit = matcher.group(2) == null ? "ms" : matcher.group(2);
    return switch (unit) {
      case "s" -> Duration.ofSeconds(amount);
      case "m" -> Duration.ofMinutes(amount);
      case "h" -> Duration.ofHours(amount);
      case "d" -> Duration.ofDays(amount);
      default -> Duration.ofMillis(amount);
    };
  }
}
