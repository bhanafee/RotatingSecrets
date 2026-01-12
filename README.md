# RotatingSecrets

A Spring Boot application demonstrating zero-downtime database credential rotation in Kubernetes environments using Oracle Universal Connection Pool (UCP). The application reads fresh credentials from Kubernetes-mounted secret files each time a new database connection is created, enabling seamless password rotation without requiring an application restart.

## Overview

When running in Kubernetes with a secrets manager (HashiCorp Vault, OpenBao, External Secrets Operator), database credentials can be automatically rotated. This application demonstrates how to integrate Oracle UCP with dynamic credentials through a custom wrapper that reads updated credentials on-the-fly.

## How It Works

1. **Secret Mounting**: A secrets manager mounts credentials as files in a configurable directory (default: `/var/run/secrets/database/`)

2. **Connection Pool**: Oracle UCP manages the connection pool, wrapped by `RotatingCredentialsDataSource`

3. **Fresh Credentials**: When `getConnection()` is called, the wrapper reads current username/password from mounted secret files and delegates to `poolDataSource.getConnection(username, password)`

4. **Seamless Rotation**: When credentials are rotated, new connections automatically use the updated values while existing connections continue unaffected

## Prerequisites

- Java 21
- Gradle 9.2+
- Oracle Database
- Kubernetes cluster with secrets management (for production)

## Project Structure

```
src/main/java/com/maybeitssquid/rotatingsecrets/
├── RotatingSecretsApplication.java      # Entry point with scheduling enabled
├── KubernetesCredentialsProvider.java   # Reads credentials from mounted secrets
├── RotatingCredentialsDataSource.java   # Wrapper providing dynamic credentials to UCP
├── DataSourceConfig.java                # Oracle UCP configuration
├── DatabasePollingService.java          # Demonstrates credential rotation
└── QueryResult.java                     # Result data model
```

## Configuration

### application.properties

```properties
spring.application.name=RotatingSecrets

# Path where Kubernetes mounts the database secrets
k8s.secrets.path=/var/run/secrets/database

# Connection pool settings
pool.min-size=1
pool.max-size=3
pool.connection-timeout-ms=5000
```

### Secret Files

The application expects three files in the secrets directory:

| File | Description |
|------|-------------|
| `jdbc-url` | Oracle JDBC connection URL (e.g., `jdbc:oracle:thin:@//host:1521/service`) |
| `username` | Database username |
| `password` | Database password |

## Building

```bash
./gradlew build
```

## Running

### Local Development

Create the secret files in a local directory:

```bash
mkdir -p /tmp/secrets/database
echo "jdbc:oracle:thin:@//localhost:1521/XEPDB1" > /tmp/secrets/database/jdbc-url
echo "myuser" > /tmp/secrets/database/username
echo "mypassword" > /tmp/secrets/database/password
```

Run with the custom secrets path:

```bash
./gradlew bootRun --args='--k8s.secrets.path=/tmp/secrets/database'
```

### Kubernetes Deployment

Mount your secrets as a volume at the configured path. Example with Vault Agent:

```yaml
apiVersion: v1
kind: Pod
metadata:
  annotations:
    vault.hashicorp.com/agent-inject: "true"
    vault.hashicorp.com/agent-inject-secret-jdbc-url: "database/creds/myapp"
    vault.hashicorp.com/agent-inject-template-jdbc-url: |
      {{- with secret "database/creds/myapp" -}}
      jdbc:oracle:thin:@//db-host:1521/service
      {{- end }}
spec:
  containers:
    - name: app
      image: rotating-secrets:latest
```

## Testing

The project includes unit tests using H2 in-memory database for credential provider testing:

```bash
./gradlew test
```

### Test Coverage

- **KubernetesCredentialsProviderTest**: Credential file reading, whitespace trimming, rotation simulation
- **DataSourceConfigTest**: Credential provider integration tests
- **DatabasePollingServiceTest**: Scheduled polling and output format
- **RotatingSecretsApplicationTests**: Spring context integration tests

Note: Full Oracle UCP integration tests require an Oracle database.

## Technologies

| Component | Version |
|-----------|---------|
| Spring Boot | 4.0.1 |
| Java | 21 |
| Oracle UCP | (via oracle-jdbc) |
| Oracle JDBC | ojdbc11 |
| Spring Cloud Vault | 2025.1.0 |
| Resilience4j | (via Spring Cloud) |
| Gradle | 9.2.1 |

## Why Oracle UCP?

Oracle UCP provides Oracle-specific features not available in generic connection pools:

- **Fast Application Notification (FAN)**: Automatic failover on RAC/Data Guard events
- **Oracle Wallet Integration**: Enterprise credential management
- **Transparent Application Continuity**: Automatic request replay on failover
- **Service-Aware Connections**: Different pools for different database services

## Production Considerations

- **Pool Tuning**: Adjust `pool.min-size` and `pool.max-size` based on your workload
- **Monitoring**: UCP exposes metrics via JMX/MXBeans
- **Fail-Fast**: The application throws RuntimeException if secrets cannot be read
- **RBAC**: Ensure the pod has read permissions on mounted secret volumes
- **FAN Events**: Enable `setFastConnectionFailoverEnabled(true)` for RAC environments

## License

MIT License
