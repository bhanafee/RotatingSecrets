package com.maybeitssquid.rotatingsecrets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot application demonstrating integration with dynamic Kubernetes Secrets
 * for database credential rotation.
 *
 * <p>This application maintains a connection pool that reads fresh credentials from
 * Kubernetes-mounted secret files whenever a new database connection is created,
 * enabling seamless password rotation without application restart.</p>
 *
 * <p>Kubernetes Secrets out of the box are mutable, but require API calls to update. In
 * most cases, it is preferable to use a replacement secrets manager (HashiCorp Vault,
 * OpenBao, External Secrets Operator, etc.) integration that supports propagating new
 * secrets as they are changed on the back end.</p>
 */
@SpringBootApplication
@EnableScheduling
public class DemoRotatingSecretsApplication {

    /**
     * Default constructor.
     */
    public DemoRotatingSecretsApplication() {
        // Spring Boot application entry point
    }

    /**
     * Application entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(DemoRotatingSecretsApplication.class, args);
    }

}
