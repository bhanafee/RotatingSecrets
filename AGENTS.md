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

**NVD key (optional):** `dependencyCheckAnalyze` reads the key from the `NVD_API_KEY` environment variable or the `nvd.apiKey` Gradle property. With a key, it updates the NVD data live. Without one, it uses the local NVD cache and logs a warning. CI runs without a key, so it also uses the cache.

**External dependencies:** Requires Kubernetes, secrets manager (Vault/OpenBao/ESO). Demo requires database.

Build uses Java 25 toolchain, compiles to Java 17 bytecode (`release = "17"`). CI tests on Java 17, 21, and 25.

## Key Entry Points

- **`CredentialsProviderService`** — watches secret files and triggers credential updates (`:rotating-secrets` module)
- **`HikariCredentialsUpdater`** — implements HikariCP credential rotation
- **`UcpCredentialsUpdater`** — implements Oracle UCP credential rotation
- **`RotatingSecretsApplication`** — Spring Boot demo app wiring both pool implementations (`:demo` module)

## Versioning and Releases

Versions are derived from git tags using [`gradle-git-version`](https://github.com/palantir/gradle-git-version):

- **On a tag** (e.g., `v1.0.0`) → version = `1.0.0`
- **After a tag** → version = tag + distance + commit hash (e.g., `1.0.1-3-gABC1234` = 3 commits after v1.0.0)
- **No tags yet** → version synthesized from git history (e.g., `0.0.1-dev-88-gXYZ`)

**To create a release:**

```bash
# Ensure all commits are pushed
git push origin main

# Create and push the tag (triggers automatic version picking in build)
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0

# Build and publish
./gradlew clean build publish
```

**To delete a release tag:**

```bash
git tag -d v1.0.0              # Delete locally
git push origin :v1.0.0        # Delete from remote
```

## Architecture

### Module structure

- **`rotating-secrets/`** — reusable library; no Spring Boot plugin, produces a plain JAR
- **`demo/`** — Spring Boot application that depends on `rotating-secrets` and exercises both connection pools

### Credential rotation flow

`CredentialsProviderService` runs a `WatchService` thread on two files (`username`, `password`) in a Kubernetes-mounted directory (default `/var/run/secrets/database`). It re-reads the files on any directory event, and also when the `refreshInterval` fallback timeout elapses with no event. On first read or any change it calls `setCredential(username, password)` on every registered `UpdatableCredential<String>` bean.

Two implementations are wired as named beans (`hikariUpdater`, `ucpUpdater`) and auto-registered via `@Autowired @Qualifier` setters on `CredentialsProviderService`:

- **`HikariCredentialsUpdater`** — implements both `UpdatableCredential<String>` and HikariCP's `HikariCredentialsProvider`. On update it swaps the immutable `Credentials` object and calls `softEvictConnections()`. There is a deliberate circular dependency: `HikariDataSourceConfig` must set the `HikariCredentialsProvider` on the config *before* creating the `HikariDataSource`, then inject the `HikariDataSource` back into the updater for eviction support.
- **`UcpCredentialsUpdater`** — calls `setUser()`/`setPassword()` and `refreshConnectionPool()` on the Oracle UCP `PoolDataSource`.

### Key configuration properties

| Property | Default | Purpose |
|---|---|---|
| `k8s.secrets.path` | `/var/run/secrets/database` | Directory with `username` and `password` files |
| `k8s.secrets.refreshInterval` | `30000` | Fallback re-check interval (ms) when no file event arrives |
| `spring.datasource.*` | — | HikariCP datasource (primary) |
| `spring.datasource.ucp.*` | — | Oracle UCP datasource |

## Shared build sections

`build.gradle` and `settings.gradle` wrap the build logic this project shares with the other Java/Gradle projects in `// shared:begin NAME` and `// shared:end NAME` markers. The canonical copies live in `claude-skills/init-java-project/shared/`. The `shared-build` CI job checks out that directory and fails if a wrapped section differs from its canonical copy. Indentation, blank lines and comment lines are ignored. To change a shared section, change the canonical copy and the same section in every project in one change set. Run the check locally with `bash <claude-skills>/init-java-project/shared/check-shared-sections.sh . <claude-skills>/init-java-project/shared`.

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

Update the secret files to trigger rotation. The service reacts to file events right away; `refreshInterval` (30 seconds by default) is only the fallback re-check.

## Dependency constraints

**Spring Boot and Spring Cloud versions are coupled.** The demo module uses a Spring Cloud starter, and Spring Cloud's compatibility verifier aborts startup with `CompatibilityNotMetException` if the Boot version is outside its supported range. Always bump `spring-boot` and `spring-cloud` together in `gradle/libs.versions.toml`, not independently. Currently: Spring Cloud 2025.1.2+ supports Spring Boot 4.1.x. (Dependabot PRs that bump only one side of this pair will fail CI.)
