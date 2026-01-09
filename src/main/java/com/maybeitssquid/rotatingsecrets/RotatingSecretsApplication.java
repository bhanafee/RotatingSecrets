package com.maybeitssquid.rotatingsecrets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot application demonstrating HashiCorp Vault integration with Kubernetes
 * for Oracle database credential rotation.
 *
 * <p>This application maintains a connection pool that reads fresh credentials from
 * Kubernetes-mounted secret files whenever a new database connection is created,
 * enabling seamless password rotation without application restart.</p>
 */
@SpringBootApplication
@EnableScheduling
public class RotatingSecretsApplication {

    /**
     * Application entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(RotatingSecretsApplication.class, args);
    }

}
