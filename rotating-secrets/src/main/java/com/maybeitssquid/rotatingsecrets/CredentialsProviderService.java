package com.maybeitssquid.rotatingsecrets;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service that reads database credentials from Kubernetes-mounted secret files and notifies
 * registered {@link UpdatableCredential} components when credentials change.
 *
 * <p>This service implements the credential rotation pattern for Kubernetes environments. It
 * watches the secrets directory for changes using {@link WatchService} (typically mounted by a
 * secrets manager like HashiCorp Vault, OpenBao, or the External Secrets Operator). When
 * credentials change, all registered connection pools are notified to update their credentials.
 *
 * <h2>File Structure</h2>
 *
 * <p>The service expects the following files in the secrets directory:
 *
 * <ul>
 *   <li>{@code username} - Contains the database username
 *   <li>{@code password} - Contains the database password
 * </ul>
 *
 * <h2>Kubernetes Secret Mounting</h2>
 *
 * <p>Kubernetes mounts secrets via atomic symlink swaps on the {@code ..data} directory. Because
 * individual credential files are symlinks, the watch is registered on the parent directory rather
 * than the files themselves, so that the directory-level events fired during the symlink swap are
 * captured.
 *
 * <h2>Configuration Properties</h2>
 *
 * <ul>
 *   <li>{@code k8s.secrets.path} - Base directory for secret files (default: {@code
 *       /var/run/secrets/database})
 *   <li>{@code k8s.secrets.refreshInterval} - Fallback poll timeout in milliseconds; a credential
 *       re-check is forced after this interval even if no watch event fires (default: 30000)
 * </ul>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>This service is thread-safe. Credential reads and updates are performed atomically, and the
 * list of updatable components is safely managed.
 *
 * @see UpdatableCredential
 * @see com.maybeitssquid.rotatingsecrets.hikari.HikariCredentialsUpdater
 * @see com.maybeitssquid.rotatingsecrets.ucp.UcpCredentialsUpdater
 */
@Service("credentialsProvider")
public class CredentialsProviderService {

  private static final Logger log = LoggerFactory.getLogger(CredentialsProviderService.class);

  /** Path to the file containing the database username. */
  protected final Path usernamePath;

  /** Path to the file containing the database password. */
  protected final Path passwordPath;

  private final long refreshIntervalMs;

  private volatile String username;
  private volatile String password;
  private volatile boolean secretsAvailable = false;

  private final List<UpdatableCredential<String>> updatables = new CopyOnWriteArrayList<>();

  private Thread watchThread;
  private WatchService watchService;

  /**
   * Creates a new credentials provider reading from the specified secrets path.
   *
   * <p>The provider will look for {@code username} and {@code password} files within the specified
   * directory.
   *
   * @param secretsPath base path where Kubernetes mounts the secret files; defaults to {@code
   *     /var/run/secrets/database}
   * @param refreshIntervalMs fallback poll timeout in milliseconds; a credential re-check is forced
   *     after this interval even if no watch event fires; defaults to 30000
   */
  public CredentialsProviderService(
      @Value("${k8s.secrets.path:/var/run/secrets/database}") String secretsPath,
      @Value("${k8s.secrets.refreshInterval:30000}") long refreshIntervalMs) {
    Path basePath = Path.of(secretsPath);
    this.usernamePath = basePath.resolve("username");
    this.passwordPath = basePath.resolve("password");
    this.refreshIntervalMs = refreshIntervalMs;
  }

  /**
   * Validates file permissions on startup and starts the directory watch thread.
   *
   * <p>Checks if secret files are world-readable and logs a security warning if they are. On
   * non-POSIX filesystems, the permission check is skipped.
   *
   * @throws IOException if the {@link WatchService} cannot be created or the directory cannot be
   *     registered
   */
  @PostConstruct
  public void start() throws IOException {
    checkPermissions(usernamePath);
    checkPermissions(passwordPath);

    watchService = FileSystems.getDefault().newWatchService();
    Path watchDir = usernamePath.getParent();
    watchDir.register(
        watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);

    refreshCredentials();

    watchThread = new Thread(this::watchLoop, "credentials-watch");
    watchThread.setDaemon(true);
    watchThread.start();
  }

  /** Stops the directory watch thread and closes the {@link WatchService}. */
  @PreDestroy
  public void stop() {
    if (watchThread != null) {
      watchThread.interrupt();
    }
    if (watchService != null) {
      try {
        watchService.close();
      } catch (IOException e) {
        log.debug("Error closing WatchService: {}", e.getMessage());
      }
    }
  }

  /**
   * Checks if a file is world-readable and logs a security warning if so.
   *
   * @param path the path to check
   */
  private void checkPermissions(Path path) {
    if (!Files.exists(path)) {
      return;
    }
    try {
      Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
      if (perms.contains(PosixFilePermission.OTHERS_READ)) {
        log.warn("SECURITY: {} is world-readable. Recommend chmod 600.", path);
      }
    } catch (UnsupportedOperationException e) {
      // Non-POSIX filesystem, skip check
    } catch (IOException e) {
      log.debug("Could not check permissions for {}: {}", path, e.getMessage());
    }
  }

  /**
   * Blocks on the {@link WatchService}, calling {@link #refreshCredentials()} whenever a directory
   * event fires or the fallback timeout elapses.
   */
  private void watchLoop() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        WatchKey key = watchService.poll(refreshIntervalMs, TimeUnit.MILLISECONDS);
        if (key != null) {
          key.pollEvents();
          key.reset();
        }
        refreshCredentials();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        log.warn("Unexpected error in credential watch loop", e);
      }
    }
  }

  /**
   * Reads the current credentials from the mounted secret files and notifies registered {@link
   * UpdatableCredential} components if they have changed.
   */
  void refreshCredentials() {
    if (!Files.exists(usernamePath) || !Files.exists(passwordPath)) {
      if (secretsAvailable) {
        log.warn("Credential files no longer available at {}", usernamePath.getParent());
      }
      secretsAvailable = false;
      return;
    }
    secretsAvailable = true;

    final String newUsername = readSecret(usernamePath, "username");
    final String newPassword = readSecret(passwordPath, "password");

    synchronized (this) {
      boolean changed = !newUsername.equals(this.username) || !newPassword.equals(this.password);
      if (changed) {
        this.username = newUsername;
        this.password = newPassword;
        updateCredentials();
      }
    }
  }

  /**
   * Registers the HikariCP credentials updater to receive credential change notifications.
   *
   * @param updatable the HikariCP credentials updater bean
   */
  @Autowired
  @Qualifier("hikariUpdater")
  public void setHikariUpdatable(UpdatableCredential<String> updatable) {
    this.updatables.add(updatable);
  }

  /**
   * Registers the Oracle UCP credentials updater to receive credential change notifications.
   *
   * @param updatable the Oracle UCP credentials updater bean
   */
  @Autowired
  @Qualifier("ucpUpdater")
  public void setUcpUpdatable(UpdatableCredential<String> updatable) {
    this.updatables.add(updatable);
  }

  /**
   * Notifies all registered {@link UpdatableCredential} components of the current credentials.
   *
   * <p>This method iterates through all registered updatable components and calls {@link
   * UpdatableCredential#setCredential(String, Object)} with the current username and password. Each
   * component is responsible for its own thread-safe credential update logic.
   */
  void updateCredentials() {
    for (UpdatableCredential<String> updatable : updatables) {
      updatable.setCredential(this.username, this.password);
    }
  }

  /**
   * Reads and trims a secret value from a file.
   *
   * @param path the path to the secret file
   * @param name a descriptive name for logging purposes
   * @return the trimmed content of the secret file
   * @throws RuntimeException if the file cannot be read
   */
  private String readSecret(Path path, String name) {
    try {
      String value = Files.readString(path).trim();
      log.debug("Read {} from Kubernetes secrets", name);
      return value;
    } catch (IOException e) {
      throw new RuntimeException("Failed to read " + name + " from " + path, e);
    }
  }
}
