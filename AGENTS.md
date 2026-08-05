# Codebase Guidance

This file documents key information about the project architecture, build commands, code style, and security practices.

## Project overview

RotatingSecrets is a Java library (`rotating-secrets` module) and demo app (`demo` module) for zero-downtime database credential rotation in Kubernetes. When a secrets manager (Vault, OpenBao, ESO) rotates database credentials by updating mounted secret files, the library detects the change and updates connection pools without dropping connections.

## Commands

**Build and test:**
```bash
./gradlew build              # compile, test, spotless check
./gradlew test               # run all tests (both modules)
./gradlew :rotating-secrets:test  # test only rotating-secrets module
./gradlew :demo:test         # test only demo module
./gradlew test --tests "*ProviderTest"        # run tests by class name
./gradlew test --tests "*ProviderTest.test*"  # run tests by method pattern
```

**Code quality:**
```bash
./gradlew spotlessApply           # auto-format (required before commit)
./gradlew dependencyCheckAnalyze  # OWASP vulnerability scan (slow; fails at CVSS ≥ 7)
```

**External dependencies:** Requires Kubernetes, secrets manager (Vault/OpenBao/ESO). Demo requires database.

Build uses Java 25 toolchain, compiles to Java 17 bytecode (`release = "17"`). CI tests on Java 17, 21, and 25.

## Key Entry Points

- **`CredentialsProviderService`** — watches secret files and triggers credential updates (`:rotating-secrets` module)
- **`HikariCredentialsUpdater`** — implements HikariCP credential rotation
- **`UcpCredentialsUpdater`** — implements Oracle UCP credential rotation
- **`RotatingSecretsApplication`** — Spring Boot demo app wiring both pool implementations (`:demo` module)

## Architecture

### Module structure

- **`rotating-secrets/`** — reusable library; no Spring Boot plugin, produces a plain JAR
- **`demo/`** — Spring Boot application that depends on `rotating-secrets` and exercises both connection pools

### Credential rotation flow

`CredentialsProviderService` is a `@Scheduled` Spring service that polls two files (`username`, `password`) from a Kubernetes-mounted directory (default `/var/run/secrets/database`). On first read or any change it calls `setCredential(username, password)` on every registered `UpdatableCredential<String>` bean.

Two implementations are wired as named beans (`hikariUpdater`, `ucpUpdater`) and auto-registered via `@Autowired @Qualifier` setters on `CredentialsProviderService`:

- **`HikariCredentialsUpdater`** — implements both `UpdatableCredential<String>` and HikariCP's `HikariCredentialsProvider`. On update it swaps the immutable `Credentials` object and calls `softEvictConnections()`. There is a deliberate circular dependency: `HikariDataSourceConfig` must set the `HikariCredentialsProvider` on the config *before* creating the `HikariDataSource`, then inject the `HikariDataSource` back into the updater for eviction support.
- **`UcpCredentialsUpdater`** — calls `setUser()`/`setPassword()` and `refreshConnectionPool()` on the Oracle UCP `PoolDataSource`.

### Key configuration properties

| Property | Default | Purpose |
|---|---|---|
| `k8s.secrets.path` | `/var/run/secrets/database` | Directory with `username` and `password` files |
| `k8s.secrets.refreshInterval` | `30000` | Poll interval (ms) |
| `spring.datasource.*` | — | HikariCP datasource (primary) |
| `spring.datasource.ucp.*` | — | Oracle UCP datasource |

## Code style

Spotless enforces Google Java Format. Run `./gradlew spotlessApply` before committing. `module-info.java` is excluded from formatting.

## Security patches

For CVE patch management, see the `gradle-security-patch` skill. Use `/gradle-security-patch` to pin a CVE fix in the version catalog.

## Local Development

**Running the demo:**

The demo requires a running database and a secrets manager. To run locally with filesystem-based secrets:

```bash
mkdir -p /tmp/secrets/database
echo "dbuser" > /tmp/secrets/database/username
echo "dbpassword" > /tmp/secrets/database/password

./gradlew :demo:bootRun --args='--k8s.secrets.path=/tmp/secrets/database'
```

Update the secret files to trigger rotation (the service polls every 30 seconds by default).

## Dependency constraints

**Spring Boot and Spring Cloud versions are coupled.** The demo module uses a Spring Cloud starter, and Spring Cloud's compatibility verifier aborts startup with `CompatibilityNotMetException` if the Boot version is outside its supported range. The `2025.1.x` Spring Cloud train (the current `spring-cloud` in the catalog) targets Boot **4.0.x**, so a Boot **4.1.x** bump fails its tests until a Boot-4.1-compatible Spring Cloud release ships. Bump `spring-boot` and `spring-cloud` together in `gradle/libs.versions.toml`, not independently. (Dependabot PRs that bump only one side of this pair will fail CI — see #15.)
