# PiggyMetrics runbook

The migration demonstration has four run tiers. The three .NET services are
out of scope and are not part of any tier.

## T0: tests

T0 is the smallest repeatable check. It runs the default seven-module core
reactor:

```bash
export JAVA_HOME=/path/to/jdk8   # e.g. /usr/lib/jvm/temurin-8-jdk-amd64
mvn -B -fae test
```

The repository enforces JDK 8 through maven-enforcer, so the build fails fast on
any other JDK rather than producing Java 8-incompatible bytecode. Every Maven
command below assumes `JAVA_HOME` points at a JDK 8; the path above is an
example, not a fixed location. The environment blueprint installs Temurin 8 at
`/usr/lib/jvm/temurin-8-jdk-amd64`.

GitHub-hosted CI reaches Maven Central directly. Networks that cannot (Maven
Central rate-limits some environments with HTTP 429, and corporate networks
often block it outright) need a `central` mirror in `~/.m2/settings.xml`; the
repository's environment blueprint writes one, and every Maven command below
picks it up automatically. Nothing in the build depends on a mirror being
present.

## T1: capped bare JVMs

T1 runs seven Java processes, one local MongoDB, and the local rates stub:

```bash
scripts/demo/start-local.sh
scripts/demo/smoke.sh
```

The harness starts each JVM with:

```text
-Xmx192m -XX:MaxMetaspaceSize=128m
```

The measured T1 launch-to-routed-health time was approximately **55.24
seconds**. Peak RSS was approximately **2,939.7 MiB** for the seven JVMs and
**79.9 MiB** for MongoDB:

| Process | Peak RSS |
| --- | ---: |
| config | 341.7 MiB |
| registry | 397.1 MiB |
| auth-service | 443.9 MiB |
| account-service | 446.7 MiB |
| statistics-service | 459.7 MiB |
| notification-service | 468.4 MiB |
| gateway | 382.2 MiB |
| Seven JVM total | 2,939.7 MiB |
| mongod | 79.9 MiB |

Stop T1 with:

```bash
scripts/demo/stop-local.sh
```

The harness defaults `MONGO_BIN` to `mongod` on `PATH`. Set `MONGO_BIN` to
another `mongod` binary when needed. The seed script similarly defaults to
`mongo` on `PATH` and accepts `MONGO_BIN` for an alternate Mongo shell.
When MongoDB is not installed system-wide, a tarball fallback is:

```bash
export MONGO_BIN=/path/to/mongodb/bin/mongod
```

The matching `mongo` shell must be at `/path/to/mongodb/bin/mongo`.

## T2: standalone core Compose

T2 is the low-footprint container tier. It contains one MongoDB, seven Java
services, and one rates stub:

1. Build the Java artifacts with JDK 8:

   ```bash
   mvn -B package
   ```

   The Dockerfiles `ADD target/*.jar`, so this step is mandatory on a fresh
   clone: `target/` is not tracked, and the image build fails without it. Note
   `package` here without `-fae` — a module skipped by fail-at-end produces no
   jar and the failure resurfaces later as a confusing Docker build error.

2. Validate and start the core Compose tier:

   ```bash
   docker compose -f docker-compose.core.yml config
   docker compose -f docker-compose.core.yml up
   ```

The services receive `MONGO_HOST=mongodb`, `AUTH_HOST=auth-service`,
`REGISTRY_HOST=registry`, `RATES_URL=http://rates-stub:18080`,
`SPRING_PROFILES_ACTIVE=local`, and
`SPRING_CLOUD_CONFIG_URI=http://config:8888`.

## T3: full Java Compose

T3 preserves the repository's original Java Compose tier, without the
out-of-scope .NET services:

1. Build the full Java reactor with JDK 8:

   ```bash
   mvn -B -Pfull package
   ```

2. Build and start the full Compose tier:

   ```bash
   docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml build
   docker compose --env-file .env -f docker-compose.yml up -d
   ```

The original full-stack measurements before this runbook were approximately
**12,724.9 MiB** peak RSS for seven uncapped JVMs and **79.9 MiB** for
MongoDB. With the capped flags, the seven-JVM total was approximately
**2,939.7 MiB**.
