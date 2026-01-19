# RotatingSecrets

A Spring Boot application demonstrating zero-downtime database credential rotation in Kubernetes environments. The application supports both HikariCP and Oracle Universal Connection Pool (UCP), reading fresh credentials from Kubernetes-mounted secret files and seamlessly updating connection pools when passwords are rotated.

## Overview

When running in Kubernetes with a secrets manager (HashiCorp Vault, OpenBao, External Secrets Operator), database credentials can be automatically rotated. This application demonstrates how to integrate connection pools with dynamic credentials through a publisher-subscriber pattern that notifies pools when credentials change.

## How It Works

1. **Secret Mounting**: A secrets manager mounts credentials as files in a configurable directory (default: `/var/run/secrets/database/`)

2. **Credential Monitoring**: `CredentialsProviderService` periodically reads the secret files and detects changes

3. **Pool Notification**: When credentials change, all registered `UpdatableCredential` implementations are notified:
   - **HikariCP**: Updates credentials and soft-evicts existing connections
   - **Oracle UCP**: Updates credentials and refreshes the connection pool

4. **Seamless Rotation**: New connections automatically use updated credentials while existing connections continue unaffected until returned to the pool

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CredentialsProviderService                        │
│                   (Reads secrets, detects changes)                   │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               │ notifies on change
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      UpdatableCredential<T>                          │
│                         (Interface)                                  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
              ┌────────────────┴────────────────┐
              ▼                                 ▼
┌─────────────────────────┐       ┌─────────────────────────┐
│ HikariCredentialsUpdater│       │  UcpCredentialsUpdater  │
│  - Updates Credentials  │       │  - Updates Credentials  │
│  - Soft evicts conns    │       │  - Refreshes pool       │
└─────────────────────────┘       └─────────────────────────┘
              │                                 │
              ▼                                 ▼
┌─────────────────────────┐       ┌─────────────────────────┐
│    HikariDataSource     │       │     PoolDataSource      │
│      (Primary)          │       │      (Oracle UCP)       │
└─────────────────────────┘       └─────────────────────────┘
```

## Prerequisites

- Java 21
- Gradle 9.2+
- Kubernetes cluster with secrets management (for production)

## Project Structure

```
src/main/java/com/maybeitssquid/rotatingsecrets/
├── DemoRotatingSecretsApplication.java    # Entry point with scheduling enabled
├── CredentialsProviderService.java        # Reads secrets, notifies pools on change
├── UpdatableCredential.java               # Interface for credential update notification
├── DemoDatabasePollingService.java        # Demo: exercises connection pool
├── DemoQueryResult.java                   # Demo: query result model
├── hikari/
│   ├── HikariDataSourceConfig.java        # HikariCP configuration (primary)
│   └── HikariCredentialsUpdater.java      # HikariCP credential rotation handler
└── ucp/
    ├── UcpDataSourceConfig.java           # Oracle UCP configuration
    └── UcpCredentialsUpdater.java         # Oracle UCP credential rotation handler
```

## Configuration

### application.properties

```properties
spring.application.name=RotatingSecrets

# Kubernetes secrets path (mounted by Vault Agent or CSI driver)
k8s.secrets.path=/var/run/secrets/database
k8s.secrets.refreshInterval=30000

# Common datasource settings
spring.datasource.url=jdbc:oracle:thin:@//host:1521/service
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver
spring.datasource.username=myuser
spring.datasource.password=mypassword

# HikariCP settings (primary datasource)
spring.datasource.hikari.pool-name=HikariRotatingSecrets
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.idle-timeout=30000
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.max-lifetime=1800000

# Oracle UCP settings
spring.datasource.ucp.pool-name=UCPRotatingSecrets
spring.datasource.ucp.url=${spring.datasource.url}
spring.datasource.ucp.connection-factory-class-name=${spring.datasource.driver-class-name}
spring.datasource.ucp.user=${spring.datasource.username}
spring.datasource.ucp.password=${spring.datasource.password}
spring.datasource.ucp.initial-pool-size=2
spring.datasource.ucp.min-pool-size=2
spring.datasource.ucp.max-pool-size=10
spring.datasource.ucp.connection-wait-timeout=20
spring.datasource.ucp.inactive-connection-timeout=30
spring.datasource.ucp.max-connection-reuse-time=1800
```

### Secret Files

The application expects these files in the secrets directory:

| File | Description |
|------|-------------|
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
    vault.hashicorp.com/agent-inject-secret-username: "database/creds/myapp"
    vault.hashicorp.com/agent-inject-template-username: |
      {{- with secret "database/creds/myapp" -}}
      {{ .Data.username }}
      {{- end }}
    vault.hashicorp.com/agent-inject-secret-password: "database/creds/myapp"
    vault.hashicorp.com/agent-inject-template-password: |
      {{- with secret "database/creds/myapp" -}}
      {{ .Data.password }}
      {{- end }}
spec:
  containers:
    - name: app
      image: rotating-secrets:latest
```

## Testing

The project includes unit tests using H2 in-memory database:

```bash
./gradlew test
```

## Connection Pool Comparison

| Feature | HikariCP | Oracle UCP |
|---------|----------|------------|
| **Default for Spring Boot** | Yes | No |
| **Oracle-specific features** | No | Yes |
| **Credential update** | Via CredentialsProvider interface | Direct pool refresh |
| **Connection eviction** | Soft evict (graceful) | Pool refresh |
| **FAN support** | No | Yes |
| **Application Continuity** | No | Yes |

### Why Two Pools?

- **HikariCP** is the Spring Boot default and works well with any database. It's lightweight and high-performance.
- **Oracle UCP** provides Oracle-specific features essential for enterprise deployments:
  - Fast Application Notification (FAN) for RAC/Data Guard failover
  - Transparent Application Continuity for request replay
  - Oracle Wallet integration
  - Service-aware connections

## Technologies

| Component | Version |
|-----------|---------|
| Spring Boot | 4.0.1 |
| Java | 21 |
| HikariCP | (via Spring Boot) |
| Oracle UCP | (via oracle-jdbc) |
| Oracle JDBC | ojdbc11 |
| Spring Cloud Vault | 2025.1.0 |
| Resilience4j | (via Spring Cloud) |
| Gradle | 9.2.1 |

## Production Considerations

- **Pool Tuning**: Adjust pool sizes based on your workload and database capacity
- **Monitoring**: Both HikariCP and UCP expose metrics via JMX/MXBeans
- **Fail-Fast**: The application throws RuntimeException if secrets cannot be read
- **RBAC**: Ensure the pod has read permissions on mounted secret volumes
- **FAN Events**: For Oracle RAC, enable FAN in UCP configuration
- **Credential Refresh Interval**: Tune `k8s.secrets.refreshInterval` based on your rotation frequency

## License

MIT License