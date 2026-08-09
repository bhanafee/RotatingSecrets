# Graph Report - .  (2026-08-01)

## Corpus Check
- Corpus is ~18,243 words - fits in a single context window. You may not need a graph.

## Summary
- 477 nodes · 872 edges · 34 communities (26 shown, 8 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 82 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- CredentialsProviderService Core
- HikariCP Integration
- Oracle UCP Integration
- Kubernetes Secrets
- Spring Boot Configuration
- Connection Pool Management
- Credential Updaters
- Spring Cloud Dependencies
- Jackson Library
- Test Infrastructure
- Build Configuration
- Java Toolchain
- Gradle Plugins
- Security Patches
- CVE Patches
- CI/CD Workflows
- Documentation
- License & Legal
- Project Metadata
- UpdatableCredential Interface
- Resilience4j
- HTTP Retry Strategy
- Scheduled Polling
- Collection Types
- Exception Handling
- Interface Implementations
- Version Catalog
- Demo Application
- Module Exports

## God Nodes (most connected - your core abstractions)
1. `FileSecretReaderTests` - 19 edges
2. `CredentialsProviderService` - 17 edges
3. `CredentialsProviderServiceTest` - 17 edges
4. `RotatingCredentialsConnectionFactoryTests` - 17 edges
5. `CredentialsProviderService` - 17 edges
6. `RotatingCredentialsConnectionFactory` - 17 edges
7. `HikariCredentialsUpdater` - 16 edges
8. `CredentialsProviderServiceTests` - 15 edges
9. `CredentialRotationIntegrationTests` - 14 edges
10. `DemoDatabasePollingService` - 13 edges

## Surprising Connections (you probably didn't know these)
- `CredentialRotationIntegrationTest` --references--> `CredentialsProviderService`  [EXTRACTED]
  rotating-secrets/src/test/java/com/maybeitssquid/rotatingsecrets/CredentialRotationIntegrationTest.java → rotating-secrets/src/main/java/com/maybeitssquid/rotatingsecrets/CredentialsProviderService.java
- `CredentialsProviderServiceTest` --references--> `CredentialsProviderService`  [EXTRACTED]
  rotating-secrets/src/test/java/com/maybeitssquid/rotatingsecrets/CredentialsProviderServiceTest.java → rotating-secrets/src/main/java/com/maybeitssquid/rotatingsecrets/CredentialsProviderService.java
- `HikariCredentialsUpdater` --implements--> `UpdatableCredential`  [EXTRACTED]
  rotating-secrets/src/main/java/com/maybeitssquid/rotatingsecrets/hikari/HikariCredentialsUpdater.java → rotating-secrets/src/main/java/com/maybeitssquid/rotatingsecrets/UpdatableCredential.java
- `UcpCredentialsUpdater` --implements--> `UpdatableCredential`  [EXTRACTED]
  rotating-secrets/src/main/java/com/maybeitssquid/rotatingsecrets/ucp/UcpCredentialsUpdater.java → rotating-secrets/src/main/java/com/maybeitssquid/rotatingsecrets/UpdatableCredential.java
- `CredentialRotationIntegrationTests` --references--> `RotatingSecretsOptions`  [EXTRACTED]
  src/dotnet/MaybeItsSquid.RotatingSecrets.Tests/Integration/CredentialRotationIntegrationTests.cs → src/dotnet/MaybeItsSquid.RotatingSecrets/Configuration/RotatingSecretsOptions.cs

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Credential Updater Implementations** — hikari_credentials_updater, ucp_credentials_updater [EXTRACTED 1.00]
- **Connection Pool Management Flow** — credentials_provider_service, hikari_credentials_updater, ucp_credentials_updater, hikari_data_source, pool_data_source [EXTRACTED 1.00]
- **Kubernetes Secrets Manager Integration** — vault_hashicorp, openbao_secrets, eso_external_secrets, kubernetes_secrets_management, credentials_provider_service [EXTRACTED 1.00]

## Communities (34 total, 8 thin omitted)

### Community 0 - "CredentialsProviderService Core"
Cohesion: 0.09
Nodes (20): BackgroundService, bool, DatabaseCredentials, ICredentialUpdatable, CancellationToken, ILogger, List, object (+12 more)

### Community 1 - "HikariCP Integration"
Cohesion: 0.06
Nodes (23): CancellationToken, DbConnection, Task, IDbConnectionFactory, DbConnection, IConnectionProvider, DbConnection, ILogger (+15 more)

### Community 2 - "Oracle UCP Integration"
Cohesion: 0.13
Nodes (14): Credentials, HikariCredentialsProvider, HikariCredentialsUpdater, HikariDataSource, Override, CredentialRotationIntegrationTest, AfterEach, BeforeEach (+6 more)

### Community 3 - "Kubernetes Secrets"
Cohesion: 0.12
Nodes (16): Connection, DemoDatabasePollingService, DataSource, Logger, Service, DatabasePollingServiceTest, AfterEach, BeforeEach (+8 more)

### Community 4 - "Spring Boot Configuration"
Cohesion: 0.07
Nodes (33): Connection Pool Refresh Pattern, CredentialsProviderService, CVE-2026-54515 Jackson Databind Vulnerability, demo Spring Boot Application Module, External Secrets Operator (ESO), GHSA-5gvw-p9qm-jgwh Jackson @JsonView Bypass, GitHub Actions CI Workflow, Gradle Package Publishing Workflow (+25 more)

### Community 5 - "Connection Pool Management"
Cohesion: 0.11
Nodes (14): Action, MaybeItsSquid.RotatingSecrets.Tests.Integration, MaybeItsSquid.RotatingSecrets.Providers, MaybeItsSquid.RotatingSecrets.Configuration, MaybeItsSquid.RotatingSecrets.Core, MaybeItsSquid.RotatingSecrets.Tests.Unit, MaybeItsSquid.RotatingSecrets.Services, Exception (+6 more)

### Community 6 - "Credential Updaters"
Cohesion: 0.16
Nodes (13): HikariConfig, Primary, HikariDataSourceConfig, Bean, Configuration, HikariDataSource, Logger, PreDestroy (+5 more)

### Community 7 - "Spring Cloud Dependencies"
Cohesion: 0.08
Nodes (25): coverlet.collector (6.0.1), FluentAssertions (6.12.0), Microsoft.Data.SqlClient (5.2.0), Microsoft.Extensions.DependencyInjection.Abstractions (8.0.0), Microsoft.Extensions.Hosting (8.0.0), Microsoft.Extensions.Hosting.Abstractions (8.0.0), Microsoft.Extensions.Logging (8.0.0), Microsoft.Extensions.Logging.Abstractions (8.0.0) (+17 more)

### Community 8 - "Jackson Library"
Cohesion: 0.16
Nodes (9): Autowired, PostConstruct, Qualifier, CredentialsProviderService, Logger, PreDestroy, Service, UpdatableCredential (+1 more)

### Community 9 - "Test Infrastructure"
Cohesion: 0.17
Nodes (5): CredentialsProviderServiceTest, AfterEach, BeforeEach, CredentialsProviderService, Test

### Community 10 - "Build Configuration"
Cohesion: 0.25
Nodes (6): IDisposable, Fact, Mock, string, Task, FileSecretReaderTests

### Community 11 - "Java Toolchain"
Cohesion: 0.19
Nodes (8): CredentialRotationException, Override, PoolDataSource, UcpCredentialsUpdater, BeforeEach, PoolDataSource, Test, UcpCredentialsUpdaterTest

### Community 12 - "Gradle Plugins"
Cohesion: 0.27
Nodes (4): Fact, Mock, Task, RotatingCredentialsConnectionFactoryTests

### Community 13 - "Security Patches"
Cohesion: 0.36
Nodes (4): Fact, Mock, Task, CredentialsProviderServiceTests

### Community 14 - "CVE Patches"
Cohesion: 0.25
Nodes (7): Bean, Configuration, PoolDataSource, UcpDataSourceConfig, BeforeEach, Test, UcpDataSourceConfigTest

### Community 15 - "CI/CD Workflows"
Cohesion: 0.22
Nodes (7): string, DatabaseProviderType, RotatingSecretsOptions, CancellationToken, ILogger, Task, FileSecretReader

### Community 16 - "Documentation"
Cohesion: 0.33
Nodes (4): DemoQueryResult, Override, Test, QueryResultTest

### Community 17 - "License & Legal"
Cohesion: 0.29
Nodes (5): DbConnection, ILogger, MethodInfo, Type, NpgsqlConnectionProvider

### Community 18 - "Project Metadata"
Cohesion: 0.38
Nodes (5): IEnumerable, IHostedService, CancellationToken, Task, CredentialSubscriptionInitializer

### Community 19 - "UpdatableCredential Interface"
Cohesion: 0.47
Nodes (3): DemoRotatingSecretsApplication, EnableScheduling, SpringBootApplication

### Community 21 - "HTTP Retry Strategy"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **43 isolated node(s):** `net8.0`, `Microsoft.Extensions.Hosting (8.0.0)`, `Microsoft.Data.SqlClient (5.2.0)`, `Npgsql (8.0.3)`, `MySqlConnector (2.3.5)` (+38 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **8 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `RotatingCredentialsConnectionFactory` connect `HikariCP Integration` to `CredentialsProviderService Core`, `Connection Pool Management`, `Gradle Plugins`, `CI/CD Workflows`, `Project Metadata`?**
  _High betweenness centrality (0.066) - this node is a cross-community bridge._
- **Why does `RotatingSecretsOptions` connect `CI/CD Workflows` to `CredentialsProviderService Core`, `HikariCP Integration`, `Connection Pool Management`, `Build Configuration`, `Gradle Plugins`, `Security Patches`?**
  _High betweenness centrality (0.065) - this node is a cross-community bridge._
- **Why does `UpdatableCredential` connect `Jackson Library` to `Oracle UCP Integration`, `Java Toolchain`?**
  _High betweenness centrality (0.056) - this node is a cross-community bridge._
- **What connects `net8.0`, `Microsoft.Extensions.Hosting (8.0.0)`, `Microsoft.Data.SqlClient (5.2.0)` to the rest of the system?**
  _43 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `CredentialsProviderService Core` be split into smaller, more focused modules?**
  _Cohesion score 0.09408033826638477 - nodes in this community are weakly interconnected._
- **Should `HikariCP Integration` be split into smaller, more focused modules?**
  _Cohesion score 0.06039488966318235 - nodes in this community are weakly interconnected._
- **Should `Oracle UCP Integration` be split into smaller, more focused modules?**
  _Cohesion score 0.12660028449502134 - nodes in this community are weakly interconnected._