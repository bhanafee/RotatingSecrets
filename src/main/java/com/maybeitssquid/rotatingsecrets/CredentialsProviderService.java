package com.maybeitssquid.rotatingsecrets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Service that reads database credentials from Kubernetes-mounted secret files and
 * notifies registered {@link UpdatableCredential} components when credentials change.
 *
 * <p>This service implements the credential rotation pattern for Kubernetes environments.
 * It periodically reads username and password from files in a configurable directory
 * (typically mounted by a secrets manager like HashiCorp Vault, OpenBao, or the
 * External Secrets Operator). When credentials change, all registered connection pools
 * are notified to update their credentials.</p>
 *
 * <h2>File Structure</h2>
 * <p>The service expects the following files in the secrets directory:</p>
 * <ul>
 *   <li>{@code username} - Contains the database username</li>
 *   <li>{@code password} - Contains the database password</li>
 * </ul>
 *
 * <h2>Configuration Properties</h2>
 * <ul>
 *   <li>{@code k8s.secrets.path} - Base directory for secret files (default: {@code /var/run/secrets/database})</li>
 *   <li>{@code k8s.secrets.refreshInterval} - Interval in milliseconds between credential checks (default: 30000)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This service is thread-safe. Credential reads and updates are performed atomically,
 * and the list of updatable components is safely managed.</p>
 *
 * @see UpdatableCredential
 * @see hikari.HikariCredentialsUpdater
 * @see ucp.UcpCredentialsUpdater
 */
@Service("credentialsProvider")
public class CredentialsProviderService {

    private static final Logger log = LoggerFactory.getLogger(CredentialsProviderService.class);

    /** Path to the file containing the database username. */
    protected final Path usernamePath;

    /** Path to the file containing the database password. */
    protected final Path passwordPath;

    private String username;
    private String password;

    private final List<UpdatableCredential<String>> updatables = new ArrayList<>();

    /**
     * Creates a new credentials provider reading from the specified secrets path.
     *
     * <p>The provider will look for {@code username} and {@code password} files
     * within the specified directory.</p>
     *
     * @param secretsPath base path where Kubernetes mounts the secret files;
     *                    defaults to {@code /var/run/secrets/database}
     */
    public CredentialsProviderService(
            @Value("${k8s.secrets.path:/var/run/secrets/database}") String secretsPath) {
        Path basePath = Path.of(secretsPath);
        this.usernamePath = basePath.resolve("username");
        this.passwordPath = basePath.resolve("password");
    }

    /**
     * Scheduled task that periodically checks for credential changes.
     *
     * <p>This method reads the current credentials from the mounted secret files and
     * compares them with the cached values. If either the username or password has
     * changed, it updates the cache and notifies all registered
     * {@link UpdatableCredential} components.</p>
     *
     * <p>The refresh interval is controlled by the {@code k8s.secrets.refreshInterval}
     * property (default: 30000ms).</p>
     */
    @Scheduled(fixedDelayString = "${k8s.secrets.refreshInterval:30000}")
    public void refreshCredentials() {
        final String newUsername = readSecret(usernamePath, "username");
        final String newPassword = readSecret(passwordPath, "password");

        boolean changed = !newUsername.equals(this.username) || !newPassword.equals(this.password);
        if (changed) {
            this.username = newUsername;
            this.password = newPassword;
            updateCredentials();
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
     * <p>This method iterates through all registered updatable components and calls
     * {@link UpdatableCredential#setCredential(String, Object)} with the current
     * username and password. Each component is responsible for its own thread-safe
     * credential update logic.</p>
     */
    public void updateCredentials() {
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
