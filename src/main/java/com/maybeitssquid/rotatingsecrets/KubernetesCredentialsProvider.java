package com.maybeitssquid.rotatingsecrets;

import com.zaxxer.hikari.HikariCredentialsProvider;
import com.zaxxer.hikari.util.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class KubernetesCredentialsProvider implements HikariCredentialsProvider {

    private static final Logger log = LoggerFactory.getLogger(KubernetesCredentialsProvider.class);

    private final Path jdbcUrlPath;
    private final Path usernamePath;
    private final Path passwordPath;

    public KubernetesCredentialsProvider(
            @Value("${k8s.secrets.path:/var/run/secrets/database}") String secretsPath) {
        Path basePath = Path.of(secretsPath);
        this.jdbcUrlPath = basePath.resolve("jdbc-url");
        this.usernamePath = basePath.resolve("username");
        this.passwordPath = basePath.resolve("password");
    }

    @Override
    public Credentials getCredentials() {
        String username = readSecret(usernamePath, "username");
        String password = readSecret(passwordPath, "password");
        log.info("Providing credentials for user: {}", username);
        return new Credentials(username, password);
    }

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
