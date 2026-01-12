package com.maybeitssquid.rotatingsecrets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Provides database credentials by reading from Kubernetes-mounted secret files.
 *
 * <p>This provider reads fresh credentials each time its methods are called,
 * ensuring the latest credentials are used after Vault rotates passwords.</p>
 *
 * <p>Expects secrets mounted at the configured path with the following files:</p>
 * <ul>
 *   <li>{@code jdbc-url} - JDBC connection URL</li>
 *   <li>{@code username} - Database username</li>
 *   <li>{@code password} - Database password</li>
 * </ul>
 */
@Component
public class KubernetesCredentialsProvider {

    private static final Logger log = LoggerFactory.getLogger(KubernetesCredentialsProvider.class);

    private final Path jdbcUrlPath;
    private final Path usernamePath;
    private final Path passwordPath;

    /**
     * Creates a new credentials provider reading from the specified secrets path.
     *
     * @param secretsPath base path where Kubernetes mounts the secret files
     */
    public KubernetesCredentialsProvider(
            @Value("${k8s.secrets.path:/var/run/secrets/database}") String secretsPath) {
        Path basePath = Path.of(secretsPath);
        this.jdbcUrlPath = basePath.resolve("jdbc-url");
        this.usernamePath = basePath.resolve("username");
        this.passwordPath = basePath.resolve("password");
    }

    /**
     * Reads the username fresh from the mounted secret file.
     *
     * @return the database username
     */
    public String getUsername() {
        String username = readSecret(usernamePath, "username");
        log.info("Providing credentials for user: {}", username);
        return username;
    }

    /**
     * Reads the password fresh from the mounted secret file.
     *
     * @return the database password
     */
    public String getPassword() {
        return readSecret(passwordPath, "password");
    }

    /**
     * Reads the JDBC URL from the mounted secret file.
     *
     * @return the JDBC connection URL
     */
    public String getJdbcUrl() {
        return readSecret(jdbcUrlPath, "jdbc-url");
    }

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
