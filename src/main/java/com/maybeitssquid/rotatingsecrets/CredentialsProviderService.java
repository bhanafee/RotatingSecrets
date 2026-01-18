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

@Service("credentialsProvider")
public class CredentialsProviderService {
    private static final Logger log = LoggerFactory.getLogger(CredentialsProviderService.class);
    protected final Path usernamePath;
    protected final Path passwordPath;

    private String username;
    private String password;

    private final List<UpdatableCredential<String>> updatables = new ArrayList<>();

    /**
     * Creates a new credentials provider reading from the specified secrets path.
     *
     * @param secretsPath base path where Kubernetes mounts the secret files
     */
    public CredentialsProviderService(
            @Value("${k8s.secrets.path:/var/run/secrets/database}") String secretsPath) {
        Path basePath = Path.of(secretsPath);
        this.usernamePath = basePath.resolve("username");
        this.passwordPath = basePath.resolve("password");
    }

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

    @Autowired
    @Qualifier("hikariUpdater")
    public void setHikariUpdatable(UpdatableCredential<String> updatable) {
        this.updatables.add(updatable);
    }

    @Autowired
    @Qualifier("ucpUpdater")
    public void setUcpUpdatable(UpdatableCredential<String> updatable) {
        this.updatables.add(updatable);
    }

    public void updateCredentials() {
        for (UpdatableCredential<String> updatable : updatables) {
            updatable.setCredential(this.username, this.password);
        }
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
