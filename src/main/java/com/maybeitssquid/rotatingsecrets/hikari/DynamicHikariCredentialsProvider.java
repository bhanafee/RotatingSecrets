package com.maybeitssquid.rotatingsecrets.hikari;

import com.zaxxer.hikari.HikariCredentialsProvider;
import com.maybeitssquid.rotatingsecrets.CredentialsProvider;
import com.zaxxer.hikari.util.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
public class DynamicHikariCredentialsProvider extends CredentialsProvider implements HikariCredentialsProvider {
    private static final Logger log = LoggerFactory.getLogger(DynamicHikariCredentialsProvider.class);

    /**
     * Creates a new credentials provider reading from the specified secrets path.
     *
     * @param secretsPath base path where Kubernetes mounts the secret files
     */
    public DynamicHikariCredentialsProvider(
            @Value("${k8s.secrets.path:/var/run/secrets/database}") String secretsPath) {
        super(secretsPath);
    }

    @Override
    public Credentials getCredentials() {
        String username = getUsername();
        String password = getPassword();
        log.info("Providing credentials for user: {}", username);
        return new Credentials(username, password);    }
}
