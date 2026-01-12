# RotatingSecrets

A Spring Boot application demonstrating zero-downtime database credential rotation in Kubernetes environments. The application reads fresh credentials from Kubernetes-mounted secret files each time a new database connection is created, enabling seamless password rotation without requiring an application restart.

## Overview

When running in Kubernetes with a secrets manager (HashiCorp Vault, OpenBao, External Secrets Operator), database credentials can be automatically rotated. This application demonstrates how to integrate with HikariCP's credential provider interface to read updated credentials on-the-fly.

## How It Works

1. **Secret Mounting**: A secrets manager mounts credentials as files in a configurable directory (default: `/var/run/secrets/database/`)

2. **Connection Pool**: HikariCP manages the connection pool and calls `KubernetesCredentialsProvider.getCredentials()` when creating new physical connections

3. **Fresh Credentials**: The provider reads the current values from the mounted secret files for each new connection

4. **Seamless Rotation**: When credentials are rotated, new connections automatically use the updated values while existing connections continue unaffected

## Prerequisites

- Java 21
- Gradle 9.2+
- Any JDBC-compatible database (PostgreSQL, Oracle, MySQL, MariaDB, SQL Server, DB2, etc.)
- Kubernetes cluster with secrets management (for production)

## Project Structure

```
src/main/java/com/maybeitssquid/rotatingsecrets/
├── RotatingSecretsApplication.java    # Entry point with scheduling enabled
├── KubernetesCredentialsProvider.java # Reads credentials from mounted secrets
├── DataSourceConfig.java              # HikariCP datasource configuration
├── DatabasePollingService.java        # Demonstrates credential rotation
└── QueryResult.java                   # Result data model
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
| `jdbc-url` | JDBC connection URL (e.g., `jdbc:postgresql://host:5432/database`) |
| `username` | Database username |
| `password` | Database password |

### Supported Databases

The application uses standard ANSI SQL and works with any JDBC-compatible database. Configure the appropriate driver in `build.gradle`:

```groovy
runtimeOnly 'org.postgresql:postgresql'                   // PostgreSQL
runtimeOnly 'com.oracle.database.jdbc:ojdbc11'            // Oracle
runtimeOnly 'com.mysql:mysql-connector-j'                 // MySQL
runtimeOnly 'org.mariadb.jdbc:mariadb-java-client'        // MariaDB
runtimeOnly 'com.microsoft.sqlserver:mssql-jdbc'          // SQL Server
runtimeOnly 'com.ibm.db2:jcc'                             // IBM DB2
```

See the [HikariCP documentation](https://github.com/brettwooldridge/HikariCP#popular-datasource-class-names) for additional supported databases.

## Building

```bash
./gradlew build
```

## Running

### Local Development

Create the secret files in a local directory:

```bash
mkdir -p /tmp/secrets/database
echo "jdbc:postgresql://localhost:5432/mydb" > /tmp/secrets/database/jdbc-url
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
      jdbc:postgresql://db-host:5432/mydb
      {{- end }}
spec:
  containers:
    - name: app
      image: rotating-secrets:latest
```

## Testing

The project includes comprehensive unit and integration tests using H2 in-memory database:

```bash
./gradlew test
```

### Test Coverage

- **KubernetesCredentialsProviderTest**: Credential file reading, whitespace trimming, rotation simulation
- **DataSourceConfigTest**: HikariCP configuration and pool behavior
- **DatabasePollingServiceTest**: Scheduled polling and output format
- **RotatingSecretsApplicationTests**: Full Spring context integration tests

## Technologies

| Component | Version |
|-----------|---------|
| Spring Boot | 4.0.1 |
| Java | 21 |
| HikariCP | (via Spring Data JPA) |
| Spring Cloud Vault | 2025.1.0 |
| Resilience4j | (via Spring Cloud) |
| Gradle | 9.2.1 |

## Production Considerations

- **Pool Tuning**: Adjust `pool.min-size` and `pool.max-size` based on your workload
- **Monitoring**: HikariCP exposes metrics via JMX/MXBeans
- **Fail-Fast**: The application throws RuntimeException if secrets cannot be read
- **RBAC**: Ensure the pod has read permissions on mounted secret volumes
- **Connection Lifetime**: Consider setting `maxLifetime` in HikariCP to ensure connections refresh periodically

## License

MIT License