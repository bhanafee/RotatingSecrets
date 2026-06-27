# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

RotatingSecrets is a Java library (`rotating-secrets` module) and demo app (`demo` module) for zero-downtime database credential rotation in Kubernetes. When a secrets manager (Vault, OpenBao, ESO) rotates database credentials by updating mounted secret files, the library detects the change and updates connection pools without dropping connections.

## Commands

```bash
# Build and test everything (also runs Spotless formatting check and OWASP CVE scan)
./gradlew build

# Run tests only
./gradlew test

# Run a single test class
./gradlew :rotating-secrets:test --tests "com.maybeitssquid.rotatingsecrets.CredentialsProviderServiceTest"
./gradlew :demo:test --tests "com.maybeitssquid.rotatingsecrets.RotatingSecretsApplicationTests"

# Apply Google Java Format via Spotless
./gradlew spotlessApply

# Run the demo app (requires a running database; override secrets path for local dev)
./gradlew :demo:bootRun --args='--k8s.secrets.path=/tmp/secrets/database'

# Run OWASP dependency vulnerability check
./gradlew dependencyCheckAnalyze
```

On Windows, use `gradlew.bat` (or `.\gradlew` in PowerShell).

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

Spotless enforces Google Java Format. Run `./gradlew spotlessApply` before committing; `build` will fail if formatting is off. `module-info.java` is excluded from Spotless.

Testing uses JUnit Jupiter. Integration tests use `@TempDir` for real filesystem I/O; no database container needed for unit tests (H2 in-memory for demo tests). Mockito is available via `spring-boot-starter-test`.

The build uses a Java 25 toolchain and compiles to Java 17 bytecode (`release = "17"`). CI tests on Java 17, 21, and 25 on every push/PR to `main`.

## Security patches

Transitive CVE fixes go in `gradle/libs.versions.toml` as `patch-<cve-id>` library entries using `strictly`/`prefer` version constraints, grouped into the `security-patches` bundle. The root `build.gradle` applies this bundle as `implementation` constraints to all subprojects. The `settings.gradle` classpath hack ensures patch constraints are applied to buildscript dependencies too. The OWASP dependency check plugin (`./gradlew dependencyCheckAnalyze`) fails the build at CVSS ≥ 7.
