package com.maybeitssquid.rotatingsecrets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class CredentialsProvider {
    private static final Logger log = LoggerFactory.getLogger(CredentialsProvider.class);
    protected final Path usernamePath;
    protected final Path passwordPath;

    private String username;
    private String password;

    private final boolean alwaysRefresh;
    private boolean refresh = true;

    /**
     * Creates a new credentials provider reading from the specified secrets path.
     *
     * @param secretsPath base path where Kubernetes mounts the secret files
     */
    public CredentialsProvider(
            @Value("${k8s.secrets.path:/var/run/secrets/database}") String secretsPath,
            @Value("${k8s.secrets.alwaysRefresh:false}") final boolean alwaysRefresh) {
        this.alwaysRefresh = alwaysRefresh;
        Path basePath = Path.of(secretsPath);
        this.usernamePath = basePath.resolve("username");
        this.passwordPath = basePath.resolve("password");
    }

    @Scheduled(fixedDelayString = "${k8s.secrets.refreshInterval:30000}")
    public void refreshCredentials() {
        this.refresh = true;
    }

    /**
     * Reads the username fresh from the mounted secret file.
     *
     * @return the database username
     */
    public String getCurrentUsername() {
        if (alwaysRefresh || refresh || this.username == null) {
            this.username = readSecret(usernamePath, "username");
        }
        log.info("Providing credentials for user: {}", username);
        return this.username;
    }

    /**
     * Reads the password fresh from the mounted secret file.
     *
     * @return the database password
     */
    public String getCurrentPassword() {
        if (alwaysRefresh || refresh || this.password == null) {
            this.password = readSecret(passwordPath, "password");
        }
        log.info("Providing password");
        return this.password;
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
