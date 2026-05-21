# MaybeItsSquid.RotatingSecrets for .NET

Zero-downtime database credential rotation for Kubernetes environments using .NET 8.0.

## Overview

This library reads database credentials from Kubernetes-mounted secret files, detects changes via polling, and updates connection pools transparently. When credentials rotate, the library:

1. Reads new credentials from the mounted secret files
2. Builds a new connection string
3. Clears the old connection pool (`ClearPool()`)
4. New connections automatically use the updated credentials

## Installation

Add the package to your project:

```bash
dotnet add package MaybeItsSquid.RotatingSecrets
```

Also add your database provider:

```bash
# SQL Server
dotnet add package Microsoft.Data.SqlClient

# PostgreSQL
dotnet add package Npgsql

# MySQL
dotnet add package MySqlConnector
```

## Quick Start

### 1. Configure in `appsettings.json`

```json
{
  "RotatingSecrets": {
    "SecretsPath": "/var/run/secrets/database",
    "UsernameFileName": "username",
    "PasswordFileName": "password",
    "RefreshIntervalMs": 30000,
    "ConnectionStringTemplate": "Server=myserver;Database=mydb;User Id={Username};Password={Password};"
  }
}
```

### 2. Register Services

```csharp
builder.Services.AddRotatingSecrets(builder.Configuration)
    .AddSqlServerProvider();  // or AddPostgreSqlProvider() or AddMySqlProvider()
```

### 3. Use the Connection Factory

```csharp
public class MyService
{
    private readonly IDbConnectionFactory _connectionFactory;

    public MyService(IDbConnectionFactory connectionFactory)
    {
        _connectionFactory = connectionFactory;
    }

    public async Task<IEnumerable<Order>> GetOrdersAsync()
    {
        await using var conn = await _connectionFactory.CreateOpenConnectionAsync();
        // Use with Dapper, raw ADO.NET, etc.
        return await conn.QueryAsync<Order>("SELECT * FROM Orders");
    }
}
```

## Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `SecretsPath` | `/var/run/secrets/database` | Directory containing credential files |
| `UsernameFileName` | `username` | Name of the username file |
| `PasswordFileName` | `password` | Name of the password file |
| `RefreshIntervalMs` | `30000` | Polling interval in milliseconds |
| `ConnectionStringTemplate` | (required) | Connection string with `{Username}` and `{Password}` placeholders |
| `ValidateFilePermissions` | `true` | Warn if files are world-readable (Linux/macOS) |

## Usage Patterns

### Basic ADO.NET

```csharp
await using var conn = await _connectionFactory.CreateOpenConnectionAsync();
using var cmd = conn.CreateCommand();
cmd.CommandText = "SELECT * FROM Users WHERE Id = @Id";
cmd.Parameters.AddWithValue("@Id", userId);
using var reader = await cmd.ExecuteReaderAsync();
```

### Dapper

```csharp
await using var conn = await _connectionFactory.CreateOpenConnectionAsync();
return await conn.QueryAsync<User>("SELECT * FROM Users WHERE Active = @Active", new { Active = true });
```

### Entity Framework Core

```csharp
builder.Services.AddDbContext<AppDbContext>((sp, options) =>
{
    var factory = sp.GetRequiredService<IDbConnectionFactory>();
    options.UseSqlServer(factory.GetConnectionString());
});
```

Note: EF Core caches the connection string, so for full rotation support, consider using the connection factory directly or implementing a custom `DbConnection` approach.

## Custom Subscribers

Implement `ICredentialUpdatable` to receive rotation notifications:

```csharp
public class MyCustomSubscriber : ICredentialUpdatable
{
    public void OnCredentialsRotated(DatabaseCredentials credentials)
    {
        // React to credential changes
        Console.WriteLine($"Credentials rotated to user: {credentials.Username}");
    }
}

// Register
builder.Services.AddRotatingSecrets(builder.Configuration)
    .AddSqlServerProvider()
    .AddSubscriber<MyCustomSubscriber>();
```

## Kubernetes Deployment

### Secret Volume Mount

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: myapp
spec:
  containers:
    - name: app
      volumeMounts:
        - name: db-credentials
          mountPath: /var/run/secrets/database
          readOnly: true
  volumes:
    - name: db-credentials
      secret:
        secretName: database-credentials
        items:
          - key: username
            path: username
          - key: password
            path: password
```

### With Vault Agent

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: myapp
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
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Kubernetes Pod                           │
│  ┌──────────────────┐      ┌──────────────────────────────┐│
│  │  Secret Volume   │      │         Application          ││
│  │  /var/run/       │      │                              ││
│  │  secrets/        │      │  ┌────────────────────────┐  ││
│  │  database/       │◀────▶│  │ CredentialsProvider    │  ││
│  │  ├─ username     │ poll │  │ Service (Background)   │  ││
│  │  └─ password     │      │  └───────────┬────────────┘  ││
│  └──────────────────┘      │              │ notify        ││
│                            │              ▼               ││
│                            │  ┌────────────────────────┐  ││
│                            │  │ RotatingCredentials    │  ││
│                            │  │ ConnectionFactory      │  ││
│                            │  └───────────┬────────────┘  ││
│                            │              │               ││
│                            │              ▼               ││
│                            │  ┌────────────────────────┐  ││
│                            │  │ SqlConnection.ClearPool│  ││
│                            │  │ (evict old connections)│  ││
│                            │  └────────────────────────┘  ││
│                            └──────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

## Key Differences from Java Version

| Aspect | Java (HikariCP) | .NET (ADO.NET) |
|--------|-----------------|----------------|
| Pool eviction | `softEvictConnections()` | `ClearPool()` |
| Pool key | DataSource instance | Connection string |
| Credential provider | `CredentialsProvider` interface | Direct string replacement |
| Background service | Spring `@Scheduled` | `BackgroundService` |

## Thread Safety

- All credential updates are atomic (immutable `DatabaseCredentials` record)
- Connection string updates use `volatile` fields
- Subscriber notifications use snapshot iteration
- `ClearPool()` is thread-safe in all ADO.NET providers

## Requirements

- .NET 8.0 or later
- Linux/macOS for file permission validation (optional)
- One of: Microsoft.Data.SqlClient, Npgsql, or MySqlConnector

## License

MIT
