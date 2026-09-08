# PiggyMetrics runbook

The migration demonstration has four run tiers. The three .NET services are
out of scope and are not part of any tier.

## T0: tests

T0 is the smallest repeatable check. It runs the default seven-module core
reactor:

```bash
JAVA_HOME=/home/ubuntu/.local/jdks/jdk8u504-b01 \
PATH="$JAVA_HOME/bin:$PATH" \
mvn -s /home/ubuntu/repos/piggymetrics-baseline/settings-mirror.xml -fae test
```

The repository enforces JDK 8. GitHub-hosted CI can use Maven Central directly;
the mirror above is for this environment's HTTP 429 limitation.

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

The harness defaults `MONGO_BIN` to
`../piggymetrics-demo-harness/mongodb/bin/mongod`, relative to the repository
root. Set `MONGO_BIN` to another `mongod` binary when needed.

## T2: standalone core Compose

T2 is the low-footprint container tier. It contains one MongoDB, seven Java
services, and one rates stub:

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

```bash
docker compose --env-file .env -f docker-compose.yml up -d
docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml build
```

The original full-stack measurements before this runbook were approximately
**12,724.9 MiB** peak RSS for seven uncapped JVMs and **79.9 MiB** for
MongoDB. With the capped flags, the seven-JVM total was approximately
**2,939.7 MiB**.
