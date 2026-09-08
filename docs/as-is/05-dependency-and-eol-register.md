# 05 — Dependency and End-of-Life Register

Scope: the nine **Java/Maven** modules of this repository on branch `devin/1788845133-stage-0-demo-harness`.
The three .NET services (`compliance-service`, `currency-exchange-service`, `fraud-detection-service`) are out of scope
(`docs/OUT-OF-SCOPE-DOTNET.md`).

This document is evidence for the migration plan. Nothing here is asserted without either a repo citation
(`path:line-range`) or a URL.

## 0. Method and evidence classes

Every version in this document carries one of the following tags. They are not interchangeable.

| Tag | Meaning |
| --- | --- |
| `[POM]` | Literally declared in a module POM in this repo. Cited with `path:lines`. |
| `[BOM]` | Not declared here; the version comes from a published dependency-management POM (Spring Boot 2.0.3 / Spring Cloud Finchley and its component BOMs) that was fetched and read. This is what Maven *would* pick as the managed version, before conflict mediation with transitive graphs. |
| `[UNRESOLVED]` | Not established. Maven never produced a dependency graph in this session. |

**No dependency graph was resolved.** `mvn dependency:list` and `mvn dependency:tree` were run as direct plugin goals
(so the `validate`-phase JDK-8 enforcer at `pom.xml:56-80` was never triggered), but every invocation failed at
parent-POM download with `status code: 429, reason phrase: Too Many Requests` from Maven Central, and the box has no
populated local repository and no JDK 8 (`docs/RUNBOOK.md:12` points at `/home/ubuntu/.local/jdks/jdk8u504-b01`, which
does not exist here). Per the task constraints no other JDK was installed, no POM was modified, and the stack was not
booted. Consequently:

* Every `[BOM]` version below is a **declared/managed** fact read out of the published BOMs, **not** a resolved fact.
* Transitive-only artifacts (Jackson, Tomcat, Netty, Logback, …) are listed with their **BOM-managed** versions and are
  explicitly marked as unverified against an actual tree.
* The exact commands to close this gap are in [§8 Open items for runtime verification](#8-open-items-for-runtime-verification).

Baseline coordinates, for reference: Spring Boot parent `2.0.3.RELEASE` (`pom.xml:11-16`), `spring-cloud.version =
Finchley.RELEASE` (`pom.xml:18-22`) imported as a BOM (`pom.xml:24-34`), `java.version = 1.8` (`pom.xml:21`), default
reactor of seven modules (`pom.xml:36-44`) plus `monitoring` and `turbine-stream-service` behind the `full` profile
(`pom.xml:46-54`).

Finchley.RELEASE resolves its component trains as follows (read from the published
`spring-cloud-dependencies:Finchley.RELEASE` POM): `spring-cloud-netflix 2.0.0.RELEASE`, `spring-cloud-openfeign
2.0.0.RELEASE`, `spring-cloud-config 2.0.0.RELEASE`, `spring-cloud-sleuth 2.0.0.RELEASE`, `spring-cloud-bus
2.0.0.RELEASE`, `spring-cloud-security 2.0.0.RELEASE`, `spring-cloud-commons 2.0.0.RELEASE`, `spring-cloud-stream
Elmhurst.RELEASE (2.0.0.RELEASE)`.

---

## 1. Declared dependency inventory

### 1.1 All declared dependencies, by coordinate

`M` = managed by a BOM (no version in this repo). Modules column uses short names.

| groupId:artifactId | Declared version | Managed version | Scope | Modules |
| --- | --- | --- | --- | --- |
| `org.springframework.cloud:spring-cloud-config-server` | M | `2.0.0.RELEASE` `[BOM]` | compile | config |
| `org.springframework.boot:spring-boot-starter-security` | M | `2.0.3.RELEASE` `[BOM]` | compile | config, auth, account, statistics, notification |
| `org.springframework.cloud:spring-cloud-starter-netflix-eureka-server` | M | `2.0.0.RELEASE` `[BOM]` | compile | registry |
| `org.springframework.cloud:spring-cloud-starter-config` | M | `2.0.0.RELEASE` `[BOM]` | compile | registry, gateway, auth, account, statistics, notification, monitoring, turbine-stream |
| `org.springframework.boot:spring-boot-starter-test` | M | `2.0.3.RELEASE` `[BOM]` | test | registry, gateway, auth, account, statistics, notification, monitoring, turbine-stream |
| `org.springframework.cloud:spring-cloud-starter-netflix-zuul` | M | `2.0.0.RELEASE` `[BOM]` | compile | gateway |
| `org.springframework.cloud:spring-cloud-starter` | M | `2.0.0.RELEASE` `[BOM]` | compile | gateway |
| `org.springframework.cloud:spring-cloud-starter-netflix-eureka-client` | M | `2.0.0.RELEASE` `[BOM]` | compile | gateway, auth, account, statistics, notification, turbine-stream |
| `org.springframework.cloud:spring-cloud-starter-sleuth` | M | `2.0.0.RELEASE` `[BOM]` | compile | gateway, auth, account, statistics, notification |
| `org.springframework.boot:spring-boot-starter-data-mongodb` | M | `2.0.3.RELEASE` `[BOM]` | compile | auth, account, statistics, notification |
| `org.springframework.cloud:spring-cloud-starter-oauth2` | M | `2.0.0.RELEASE` `[BOM]` | compile | auth, account, statistics, notification |
| `org.springframework.boot:spring-boot-starter-web` | M | `2.0.3.RELEASE` `[BOM]` | compile | auth, account, statistics, notification |
| `org.springframework.cloud:spring-cloud-starter-openfeign` | M | `2.0.0.RELEASE` `[BOM]` | compile | account, statistics, notification |
| `org.springframework.boot:spring-boot-starter-actuator` | M | `2.0.3.RELEASE` `[BOM]` | compile | account, statistics, notification |
| `org.springframework.cloud:spring-cloud-starter-bus-amqp` | M | `2.0.0.RELEASE` `[BOM]` | compile | account, statistics, notification |
| `org.springframework.cloud:spring-cloud-starter-netflix-hystrix` | M | `2.0.0.RELEASE` `[BOM]` | compile | account |
| `org.springframework.cloud:spring-cloud-netflix-hystrix-stream` | M | `2.0.0.RELEASE` `[BOM]` | compile | account, statistics, notification |
| `org.springframework.cloud:spring-cloud-starter-netflix-hystrix-dashboard` | M | `2.0.0.RELEASE` `[BOM]` | compile | monitoring |
| `org.springframework.cloud:spring-cloud-starter-netflix-turbine-stream` | M | `2.0.0.RELEASE` `[BOM]` | compile | turbine-stream |
| `org.springframework.cloud:spring-cloud-starter-stream-rabbit` | M | `2.0.0.RELEASE` (Elmhurst) `[BOM]` | compile | turbine-stream |
| `org.springframework.boot:spring-boot-starter-mail` | M | `2.0.3.RELEASE` `[BOM]` | compile | notification |
| `com.google.guava:guava` | **`19.0`** `[POM]` | — (pinned locally) | compile | statistics |
| `de.flapdoodle.embed:de.flapdoodle.embed.mongo` | **`1.50.3`** `[POM]` | BOM would give `2.0.3` `[BOM]` | test | auth, account, statistics, notification |
| `com.jayway.jsonpath:json-path` | **`2.2.0`** `[POM]` | BOM would give `2.4.0` `[BOM]` | test | auth, account, statistics, notification |

Line citations for the declarations:
`config/pom.xml:19-28`, `registry/pom.xml:18-32`, `gateway/pom.xml:18-44`, `auth-service/pom.xml:18-64`,
`account-service/pom.xml:18-84`, `statistics-service/pom.xml:18-86`, `notification-service/pom.xml:18-85`,
`monitoring/pom.xml:18-32`, `turbine-stream-service/pom.xml:19-42`.

Three local version pins deliberately override the Boot BOM (`guava 19.0`, `flapdoodle 1.50.3`, `json-path 2.2.0`).
Two of them are the interesting ones: **flapdoodle 1.50.3** predates the Boot-managed `2.0.3` and is bound to the
Mongo 3.x driver, so it will break at the first Boot upgrade that moves the driver generation (§6), and it is the
component that makes `*RepositoryTest` classes runnable at all.

### 1.2 Declared plugins

| Plugin | Version | Modules | Modernization relevance |
| --- | --- | --- | --- |
| `org.apache.maven.plugins:maven-enforcer-plugin` | `3.4.1` `[POM]` (`pom.xml:56-80`) | root (all) | Hard-fails any lifecycle build not on `[1.8,1.9)`. Must be re-targeted, not removed, at each stage. |
| `org.springframework.boot:spring-boot-maven-plugin` | M `[BOM]` | all nine (`config/pom.xml:32-38`, `registry/pom.xml:36-42`, `gateway/pom.xml:48-54`, `auth-service/pom.xml:68-74`, `account-service/pom.xml:88-94`, `statistics-service/pom.xml:90-96`, `notification-service/pom.xml:89-95`, `monitoring/pom.xml:36-42`, `turbine-stream-service/pom.xml:46-52`) | Repackaging behaviour and `finalName` conventions change across Boot 2.x→3.x. |
| `org.jacoco:jacoco-maven-plugin` | `0.7.6.201602180812` `[POM]` (`auth-service/pom.xml:75-93`, `account-service/pom.xml:95-113`, `statistics-service/pom.xml:97-115`, `notification-service/pom.xml:96-114`) | auth, account, statistics, notification | **Blocks the JDK upgrade on its own.** 0.7.6 (2016) predates JaCoCo's support for class-file majors 53+; JaCoCo added Java 17 support in 0.8.7 and Java 21 support in 0.8.9/0.8.11. See <https://www.jacoco.org/jacoco/trunk/doc/changes.html>. Any `mvn verify` on JDK 17 will fail in the agent before a single test runs. |

`monitoring` and `turbine-stream-service` are only built under `-Pfull` (`pom.xml:46-54`), so a default `mvn verify`
never compiles the Hystrix Dashboard or Turbine code. Retirement work on those two must be explicitly scheduled or it
will be silently skipped by CI's default job (`.github/workflows/build.yml:27` runs `mvn -B -fae verify`, line 30 runs
the `-Pfull` variant).

---

## 2. EOL register

Dates are release/support dates from primary sources. Where a component has no formal EOL statement, the register uses
the **last release** (with the source used to establish it) rather than inventing a support date.

Stage labels used in the last column (they follow the intended path Boot 2.0.3 → 2.3.x → retire Netflix OSS → 2.7 on
Java 17 → Boot 3/Jakarta/Security 6 → JDK 21):

| Stage | Content |
| --- | --- |
| **S1** | Boot 2.0.3 → 2.3.12 / Finchley → Hoxton.SR12, still Java 8. Test-stack and pinned-dependency cleanup rides along. |
| **S2** | Netflix OSS retirement **and** the OAuth2 rework, on Boot 2.3/Hoxton. Zuul→Gateway, Hystrix→Resilience4j, Ribbon→LoadBalancer, Turbine/Dashboard→Micrometer, `spring-security-oauth2`→Spring Security 5.7 native + Authorization Server. |
| **S3** | Boot 2.7 / Spring Cloud 2021.0, move the build and runtime to Java 17. |
| **S4** | Boot 3 / Spring Cloud 2022.0: Jakarta namespace, Spring Security 6, Sleuth→Micrometer Tracing. |
| **S5** | JDK 21. |

| Component (current version) | Last release / end of support | Source | Why it blocks the target state | Replacement | Retired in stage |
| --- | --- | --- | --- | --- | --- |
| **Spring Boot `2.0.3.RELEASE`** (`pom.xml:14`) | OSS support ended **2019-03-01**; last 2.0.x was 2.0.9 | <https://endoflife.date/spring-boot> | Unsupported; also caps Spring Framework at 5.0.x and Java at 8 | Boot 2.3.12 → 2.7.18 → 3.x | S1/S3/S4 |
| **Spring Cloud `Finchley.RELEASE`** (`pom.xml:20`) | Finchley train is out of OSS support; the train only ever supported Boot 2.0.x | <https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions> | Cannot coexist with Boot ≥2.2 | Hoxton.SR12 → 2021.0.x → 2022.0.x+ | S1/S3/S4 |
| **`spring-cloud-starter-netflix-zuul` `2.0.0.RELEASE`** (`gateway/pom.xml:19-22`; `@EnableZuulProxy` at `gateway/src/main/java/com/piggymetrics/gateway/GatewayApplication.java:6,10`) | Module last shipped in `spring-cloud-netflix` **2.2.9.RELEASE (Hoxton.SR12, 2021-07-06)**; the underlying Netflix Zuul 1.x last tag `v1.3.1` is **2017-10-23** | Module present in <https://raw.githubusercontent.com/spring-cloud/spring-cloud-netflix/v2.2.9.RELEASE/pom.xml> (line 197) and **absent** from <https://raw.githubusercontent.com/spring-cloud/spring-cloud-netflix/v3.0.0/pom.xml>; train date <https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-Hoxton-Release-Notes> | **Artifact does not exist** in Spring Cloud 2020.0+; no Boot 2.7 or Boot 3 compatible build exists at any version | Spring Cloud Gateway (`spring-cloud-starter-gateway`) | S2 (before Boot 2.7) |
| **`spring-cloud-starter-netflix-hystrix` + `spring-cloud-netflix-hystrix-stream` `2.0.0.RELEASE`** (`account-service/pom.xml:59-66`, `statistics-service/pom.xml:59-62`, `notification-service/pom.xml:59-62`) | Same: last in `spring-cloud-netflix` 2.2.9.RELEASE (2021-07-06). Netflix Hystrix itself last released **`1.5.18`, 2018-11-16**, repo states maintenance mode | Spring Cloud module lists (2.2.9 lines 190-200 vs 3.0.0); Hystrix tag date from `git log v1.5.18`; <https://github.com/Netflix/Hystrix> ("Hystrix is no longer in active development") | Artifact removed from the release train; "Support for ribbon, hystrix and zuul was removed across the release train projects" | Resilience4j via `spring-cloud-starter-circuitbreaker-resilience4j` | S2 |
| **`spring-cloud-starter-netflix-hystrix-dashboard` `2.0.0.RELEASE`** (`monitoring/pom.xml:23-26`; `@EnableHystrixDashboard` at `monitoring/src/main/java/com/piggymetrics/monitoring/MonitoringApplication.java:5,8`) | Last in 2.2.9.RELEASE (2021-07-06) | <https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2020.0-Release-Notes> (removed-module list) | Removed; whole `monitoring` module loses its reason to exist | Micrometer + Prometheus/Grafana (or drop the module) | S2 |
| **`spring-cloud-starter-netflix-turbine-stream` `2.0.0.RELEASE`** (`turbine-stream-service/pom.xml:28-31`) | Last in 2.2.9.RELEASE (2021-07-06). Netflix Turbine's own last tag `turbine-1.0.0` is **2014-09-07** | Spring Cloud 2020.0 removed-module list (above); Turbine tag date from `git log turbine-1.0.0` on <https://github.com/Netflix/Turbine> | Removed; aggregation model (Hystrix streams over RabbitMQ) disappears with Hystrix | Micrometer metrics scraped centrally (no per-service stream aggregation) | S2 |
| **Ribbon (`com.netflix.ribbon` `2.2.5` `[BOM]`, pulled in transitively by the Eureka/Zuul/Feign starters; also configured at `config/src/main/resources/shared/gateway.yml`)** | `spring-cloud-netflix-ribbon` last in 2.2.9.RELEASE (2021-07-06); Netflix Ribbon last release **`v2.7.18`, 2020-03-30**, repo declares maintenance mode | Module list 2.2.9 line 198 vs absent in 3.0.0; ribbon tag date from `git log v2.7.18`; <https://github.com/Netflix/ribbon> | Removed from the train; load-balancing must move before the 2020.0 jump | Spring Cloud LoadBalancer (`spring-cloud-starter-loadbalancer`) | S2 |
| **Eureka client/server `2.0.0.RELEASE`** (`registry/pom.xml:19-22`, six clients) | **Not EOL.** `spring-cloud-netflix-eureka-client/-server` are the only Netflix modules still shipped in Spring Cloud 4.x | <https://raw.githubusercontent.com/spring-cloud/spring-cloud-netflix/v4.0.0/pom.xml> (lines 130-133) | Only the *version* blocks, not the component: the Finchley 2.0.0 artifacts are Boot-2.0-bound | Same artifacts at the train version for each target (2.2.9 → 3.1.x → 4.0.x) | Version-bumped in S1/S3/S4; **no replacement needed** |
| **`spring-cloud-starter-oauth2` `2.0.0.RELEASE`** (`auth-service/pom.xml:31-34`, `account-service/pom.xml:19-22`, `statistics-service/pom.xml:27-30`, `notification-service/pom.xml:19-22`) | Ships from `spring-cloud-security`, which was **removed from the release train in 2020.0**; that repo is archived with no GA past 2.x (last tag `v3.0.0-RC1`) | "Spring Cloud Security was removed and code was moved to the individual Spring Cloud projects" — <https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2020.0-Release-Notes>; tags via `git ls-remote https://github.com/spring-attic/spring-cloud-security.git` | Artifact ceases to exist after Hoxton; it is the only thing pulling `spring-security-oauth2-autoconfigure` in | Direct `spring-boot-starter-oauth2-resource-server` / `-client` | S2 (with the OAuth rework) |
| **`spring-security-oauth2-autoconfigure` `2.0.0.RELEASE` `[BOM]`** (transitive via the starter above — see the published `spring-cloud-starter-oauth2:2.0.0.RELEASE` POM, which declares it as its only OAuth dependency) | Last GA **`2.6.8`, 2022-05-20**; the 2.7 line stopped at **`2.7.0-M3`** (milestone, never GA). Repo archived 2022-05-31 | `git ls-remote --tags https://github.com/spring-attic/spring-security-oauth2-boot.git`; <https://github.com/spring-attic/spring-security-oauth2-boot> | **There is no GA build of this library for Boot 2.7 and none for Boot 3.** It is the component with the earliest hard wall | Spring Security 5.7+/6 native OAuth2 support (`spring-security-oauth2-resource-server`, `-client`, `-jose`); authorization server → Spring Authorization Server | S2/S3 |
| **`spring-security-oauth2` `2.2.1.RELEASE` `[BOM]`** (transitive; used at `auth-service/src/main/java/com/piggymetrics/auth/config/OAuth2AuthorizationConfig.java` and the resource-server configs in account/statistics/notification) | Last GA **`2.5.2.RELEASE`, 2022-04-20**; project archived | `git ls-remote --tags https://github.com/spring-attic/spring-security-oauth.git`; <https://github.com/spring-attic/spring-security-oauth> | Dead, `javax.servlet`-based, incompatible with Spring Security 6 | Spring Security 6 resource server + Spring Authorization Server | S2/S3 |
| **`spring-cloud-starter-sleuth` `2.0.0.RELEASE`** (five modules) | Sleuth was **removed from the 2022.0 train**; core moved to Micrometer Tracing | <https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2022.0-Release-Notes> | Artifact does not exist in the Boot 3 generation | Micrometer Tracing (`micrometer-tracing-bridge-brave`) | S4 |
| **JUnit `4.12` `[BOM]`** (via `spring-boot-starter-test`, eight modules) | JUnit 4 line ended at **`4.13.2`, 2021-02-13**; no further releases | tag date from `git log r4.13.2` on <https://github.com/junit-team/junit4>; Boot-managed version confirmed `4.13.2` in every later Boot BOM (e.g. <https://raw.githubusercontent.com/spring-projects/spring-boot/v3.0.13/spring-boot-project/spring-boot-dependencies/build.gradle> line 703) | Not removed by Boot 3, but Boot 2.4 removed JUnit 5's vintage engine from `spring-boot-starter-test` (<https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.4-Release-Notes>), so JUnit 4 tests stop running unless the engine is added back explicitly | JUnit 5 (Jupiter); vintage engine as a bridge | S1 (Boot 2.3→2.4 boundary forces the choice) |
| **Mockito `2.15.0` `[BOM]`** (via `spring-boot-starter-test`) | Superseded: Boot 2.3.12 manages `3.3.3`, Boot 2.7.18 manages `4.5.1`, Boot 3.0.13 manages `4.8.1` | Boot dependency BOMs: 2.3.12 line 1191, 2.7.18 line 1335, 3.0.13 line 974 (raw `build.gradle` links in §3) | Mockito 2.x's Byte Buddy cannot instrument class-file majors emitted by JDK 17/21 | Boot-managed Mockito (auto-upgraded with each Boot bump) | S1 (upgrade rides along) / verified at S3 |
| **`de.flapdoodle.embed.mongo` `1.50.3`** (`auth-service/pom.xml:52-57`, `account-service/pom.xml:72-77`, `statistics-service/pom.xml:74-79`, `notification-service/pom.xml:68-73`) | Pinned 4 majors behind; Boot 2.3.12 manages MongoDB driver `4.0.6`, Boot 2.7.18 `4.6.1`, Boot 3.0.13 `4.8.2` | Boot BOMs (§3 links) | 1.50.3 is built against the Mongo 3.x driver API; the driver generation changes at the first Boot bump, breaking all repository tests | Newer flapdoodle (3.x/4.x) or Testcontainers MongoDB | S1 |
| **`com.jayway.jsonpath:json-path` `2.2.0`** (four modules, test) | Pinned below the Boot-managed `2.4.0` even today | `[BOM]` value from the Boot 2.0.3 dependency BOM | Low risk, but the local pin defeats BOM management and should be deleted rather than bumped | remove the `<version>`; inherit from Boot | S1 |
| **`com.google.guava:guava` `19.0`** (`statistics-service/pom.xml:63-67`) | 19.0 released 2015; within the affected range of CVE-2018-10237 (<24.1.1) and CVE-2020-8908 (<30.0) | <https://github.com/google/guava/releases>, <https://nvd.nist.gov/vuln/detail/CVE-2018-10237>, <https://nvd.nist.gov/vuln/detail/CVE-2020-8908> | Not a migration blocker; a security-hygiene item that the upgrade should clear | current Guava, or delete (usage is trivial) | S1 |
| **`eclipse-temurin:8-jre` base image** (all nine Java Dockerfiles, line 1: `config/Dockerfile:1`, `registry/Dockerfile:1`, `gateway/Dockerfile:1`, `auth-service/Dockerfile:1`, `account-service/Dockerfile:1`, `statistics-service/Dockerfile:1`, `notification-service/Dockerfile:1`, `monitoring/Dockerfile:1`, `turbine-stream-service/Dockerfile:1`) | **Temurin 8 is still maintained** — "End of Availability: at least Dec 2030"; Temurin 17 at least Oct 2027, Temurin 21 at least Dec 2029 | <https://adoptium.net/support/> | The image is *not* the EOL driver; it is a follow-on change forced by Boot 3 requiring Java 17 | `eclipse-temurin:17-jre` at S3, `eclipse-temurin:21-jre` at S5 | S3 / S5 |
| **`mongo:3` base image** (`mongodb/Dockerfile:1`) | MongoDB 3.x is end-of-life; the current compose core already runs `mongo:7.0` (`docker-compose.core.yml:3`) | <https://www.mongodb.com/legal/support-policy/lifecycles> | Infrastructure image, not a Java dependency; inconsistent with the compose stack actually used | Align on `mongo:7.0` | Any stage (independent) |
| **`sqshq/piggymetrics-*` prebuilt images** (`docker-compose.yml:16,26,41,60,85,111,136,159,174`) | Upstream images of the original project; not rebuilt from this fork | repo state | Any migration work is invisible through these images | build locally / retag | S1 |

Zuul 1.x, Hystrix, Ribbon, Turbine and the Hystrix Dashboard were already declared **maintenance mode** with these exact
replacements in the Greenwich announcement: <https://spring.io/blog/2018/12/12/spring-cloud-greenwich-rc1-available-now>.

---

## 3. Compatibility matrix

Availability of the components this repo depends on, per target generation. "Present/removed" for Spring Cloud modules
is established from the **module list in the release train's own `pom.xml`**, which is the strongest available evidence:

* Hoxton (spring-cloud-netflix `2.2.9.RELEASE`): <https://raw.githubusercontent.com/spring-cloud/spring-cloud-netflix/v2.2.9.RELEASE/pom.xml> — lines 184-202 list `…-archaius, …-hystrix-dashboard, …-hystrix-stream, …-eureka-client, …-eureka-server, …-turbine, …-turbine-stream, …-sidecar, …-zuul, …-ribbon, …-hystrix`.
* 2020.0 (spring-cloud-netflix `3.0.0`): <https://raw.githubusercontent.com/spring-cloud/spring-cloud-netflix/v3.0.0/pom.xml> — lines 154-160 list **only** `…-dependencies, …-eureka-client, …-eureka-server, starter-…-eureka-client, starter-…-eureka-server, …-eureka-client-tls-tests, docs`.
* 2022.0 (spring-cloud-netflix `4.0.0`): <https://raw.githubusercontent.com/spring-cloud/spring-cloud-netflix/v4.0.0/pom.xml> — lines 129-135, identical Eureka-only list.

| Component | Boot 2.0.3 / Finchley (today) | Boot 2.3.x / **Hoxton** | Boot 2.7.x / **2021.0.x** | Boot 3.x / **2022.0.x+** |
| --- | --- | --- | --- | --- |
| Eureka client | present `2.0.0.RELEASE` | **present** `2.2.9.RELEASE` | **present** `3.1.x` | **present** `4.0.x` |
| Eureka server | present | **present** | **present** | **present** |
| Zuul (`…-netflix-zuul`) | present | **present** (deprecated) | **removed** | **removed** |
| Hystrix (`…-netflix-hystrix`, `…-hystrix-stream`) | present | **present** (deprecated) | **removed** | **removed** |
| Hystrix Dashboard | present | **present** (deprecated) | **removed** | **removed** |
| Turbine / Turbine Stream | present | **present** (deprecated) | **removed** | **removed** |
| Ribbon | present | **present** (deprecated) | **removed** | **removed** |
| `spring-cloud-starter-oauth2` (spring-cloud-security) | present | **present** `2.2.x` | **removed** (train drops Spring Cloud Security; repo archived at `3.0.0-RC1`) | **removed** |
| `spring-security-oauth2` / `-autoconfigure` | `2.2.1` / `2.0.0` | works (`2.x`) | **no GA build** — line ends at autoconfigure `2.6.8` (Boot 2.6) / `2.7.0-M3` | **absent** (Spring Security 6 only) |
| Sleuth | present | present | present (`3.1.x`) | **removed** — moved to Micrometer Tracing |
| OpenFeign | present | present | present | present (`javax`→`jakarta` in signatures) |
| Spring Cloud Bus / Stream RabbitMQ | present | present | present | present (binder API changed) |
| Config server/client | present | present | present | present |
| `javax.validation` | required | available (`javax` **and** `jakarta` both managed) | available (both managed) | **removed** — only `jakarta.validation` `3.0.2` |
| `javax.mail` | required | available (Javax Mail `1.6.2` **and** Jakarta Mail `1.6.7`) | available (both) | **removed** — only Jakarta Mail `2.1.2` |
| **Java version** | requires 8 | **requires 8, compatible up to Java 15** | requires 8, **compatible up to Java 21** | **requires 17**, compatible up to 21 |

Java-version row citations (exact sentences from the reference docs):

* Boot 2.3.12: *"Spring Boot 2.3.12.RELEASE requires Java 8 and is compatible up to Java 15 (included)."* — <https://docs.spring.io/spring-boot/docs/2.3.12.RELEASE/reference/html/getting-started.html>
* Boot 2.7.18: *"Spring Boot 2.7.18 requires Java 8 and is compatible up to and including Java 21."* — <https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/getting-started.html>
* Boot 3.0.13: *"Spring Boot 3.0.13 requires Java 17 and is compatible up to and including Java 21."* — <https://docs.spring.io/spring-boot/docs/3.0.13/reference/html/getting-started.html>
* Hoxton↔Boot mapping: *"Hoxton.SR10 is compatible with Spring Boot 2.3.x and 2.2.x."* — <https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-Hoxton-Release-Notes>
* Train↔Boot mapping generally: <https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions> and <https://spring.io/projects/spring-cloud#support>

### 3.1 What the matrix forces

1. **The Netflix retirement cannot be deferred past the 2021.0 jump.** Zuul, Hystrix (+ stream + dashboard), Turbine and
   Ribbon are not "deprecated but shippable" in 2021.0/2022.0 — the *artifacts are not built at all* (module lists
   above). There is no version of `spring-cloud-starter-netflix-zuul` that resolves under Spring Cloud 2021.0.
2. **Boot 2.3/Hoxton is the only generation where old and new can coexist.** Hoxton still builds all Netflix modules
   *and* runs on Boot 2.3, so the Zuul→Gateway, Hystrix→Resilience4j, Ribbon→LoadBalancer swaps can be made one at a
   time on a green build. Doing them after the Boot bump is not an option; doing them before Boot 2.3 means doing them
   on an unsupported Boot 2.0.
3. **Java 17 cannot be adopted at Boot 2.3.** Boot 2.3.12 is compatible only up to Java 15. The JDK-17 move belongs to
   the Boot 2.7 step (2.7 is compatible up to 21), which is also why Boot 2.7 — not Boot 3 — is where JaCoCo and Mockito
   must already be current.
4. **Boot 3 does two irreversible things at once**: requires Java 17 and drops all `javax.*` EE APIs (the Boot 3.0.13
   BOM has no `Javax Mail`/`Javax Validation` entries at all, only `Jakarta Mail 2.1.2` / `Jakarta Validation 3.0.2`).
   Both must be complete before the Boot 3 build is attempted.
5. **The OAuth2 rework has the earliest hard wall of anything in the repo.** `spring-security-oauth2-autoconfigure`
   has no GA release beyond `2.6.8` (Boot 2.6) and `spring-cloud-starter-oauth2` ceases to exist after Hoxton. So the
   OAuth2 rewrite must land in the same stage as the Netflix retirement (Hoxton) — it cannot be carried into Boot 2.7
   even in a degraded form, and it is a *behavioural* rewrite (custom authorization server → Spring Authorization
   Server), not a dependency swap.

---

## 4. `javax.*` → `jakarta.*` inventory

Scan: `grep -rn "^import javax\." --include=*.java .` — **17 files**, 23 import statements (the baseline estimate of
"roughly 15 files" is close; 17 is the reproducible count with this command).

Both packages are removed by Boot 3 / Spring Framework 6, and by nothing earlier: Boot 2.7.18 still manages
`Javax Mail 1.6.2` (line 851) and `Javax Validation 2.0.1.Final` (line 879), while Boot 3.0.13 manages neither and
carries `Jakarta Mail 2.1.2` (line 490) / `Jakarta Validation 3.0.2` (line 532)
(<https://raw.githubusercontent.com/spring-projects/spring-boot/v2.7.18/spring-boot-project/spring-boot-dependencies/build.gradle>,
<https://raw.githubusercontent.com/spring-projects/spring-boot/v3.0.13/spring-boot-project/spring-boot-dependencies/build.gradle>).

### 4.1 `javax.validation` → `jakarta.validation:jakarta.validation-api` (13 files, 19 imports)

Forced by: **Boot 3.0** (Jakarta Validation 3.0.2, Hibernate Validator 8.x). Annotations are source-compatible after the
package rename; `@NotNull` semantics are unchanged.

| File | Lines | Imports |
| --- | --- | --- |
| `account-service/src/main/java/com/piggymetrics/account/controller/AccountController.java` | 10 | `javax.validation.Valid` |
| `account-service/src/main/java/com/piggymetrics/account/domain/Account.java` | 8-9 | `Valid`, `constraints.NotNull` |
| `account-service/src/main/java/com/piggymetrics/account/domain/Item.java` | 5 | `constraints.NotNull` |
| `account-service/src/main/java/com/piggymetrics/account/domain/Saving.java` | 3 | `constraints.NotNull` |
| `account-service/src/main/java/com/piggymetrics/account/domain/User.java` | 5 | `constraints.NotNull` |
| `auth-service/src/main/java/com/piggymetrics/auth/controller/UserController.java` | 12 | `Valid` |
| `notification-service/src/main/java/com/piggymetrics/notification/controller/RecipientController.java` | 11 | `Valid` |
| `notification-service/src/main/java/com/piggymetrics/notification/domain/NotificationSettings.java` | 3 | `constraints.NotNull` |
| `notification-service/src/main/java/com/piggymetrics/notification/domain/Recipient.java` | 7-8 | `Valid`, `constraints.NotNull` |
| `statistics-service/src/main/java/com/piggymetrics/statistics/controller/StatisticsController.java` | 10 | `Valid` |
| `statistics-service/src/main/java/com/piggymetrics/statistics/domain/Account.java` | 6-7 | `Valid`, `constraints.NotNull` |
| `statistics-service/src/main/java/com/piggymetrics/statistics/domain/Item.java` | 5 | `constraints.NotNull` |
| `statistics-service/src/main/java/com/piggymetrics/statistics/domain/Saving.java` | 3 | `constraints.NotNull` |

### 4.2 `javax.mail` → `jakarta.mail:jakarta.mail-api` (4 files, 6 imports)

Forced by: **Boot 3.0** (Jakarta Mail 2.1.2; `spring-boot-starter-mail` switches to `jakarta.mail`). Unlike validation
this is **not** a pure rename: Jakarta Mail 2.1 changed `Session`/`Transport` factory behaviour, and
`MimeMessage`/`MessagingException` move to `jakarta.mail.internet` / `jakarta.mail`.

| File | Lines | Imports |
| --- | --- | --- |
| `notification-service/src/main/java/com/piggymetrics/notification/service/EmailService.java` | 6 | `javax.mail.MessagingException` |
| `notification-service/src/main/java/com/piggymetrics/notification/service/EmailServiceImpl.java` | 16-17 | `MessagingException`, `internet.MimeMessage` |
| `notification-service/src/test/java/com/piggymetrics/notification/service/EmailServiceImplTest.java` | 14-16 | `MessagingException`, `Session`, `internet.MimeMessage` |
| `notification-service/src/test/java/com/piggymetrics/notification/service/NotificationServiceImplTest.java` | 12 | `MessagingException` |

### 4.3 Not in the import scan but part of the same migration

The OAuth2/security stack in `auth-service`, `account-service`, `statistics-service` and `notification-service` is built
on `javax.servlet` APIs *transitively* (via `spring-security-oauth2` and Spring MVC). Those imports don't appear in
application source, but the Servlet 5 (`jakarta.servlet`) switch in Boot 3 is precisely why `spring-security-oauth2`
cannot be carried forward (§2). Tomcat moves `8.5.31` → `9.0.x` → **`10.1.x`** (Jakarta) across the path (§6).

---

## 5. Java language-level inventory (8 → 17 → 21)

Findings are from scans of this repo, not generic advice. Where a claim could be checked locally, it was checked against
the JDK 17 on this machine (`openjdk 17.0.19`).

### 5.1 `com.sun.*` usage — 4 files, and **it is not what it looks like**

| File | Lines | API |
| --- | --- | --- |
| `account-service/src/test/java/com/piggymetrics/account/controller/AccountControllerTest.java` | 7 | `com.sun.security.auth.UserPrincipal` |
| `auth-service/src/test/java/com/piggymetrics/auth/controller/UserControllerTest.java` | 6 | `com.sun.security.auth.UserPrincipal` |
| `notification-service/src/test/java/com/piggymetrics/notification/controller/RecipientControllerTest.java` | 10 | `com.sun.security.auth.UserPrincipal` |
| `statistics-service/src/test/java/com/piggymetrics/statistics/controller/StatisticsControllerTest.java` | 13 | `com.sun.security.auth.UserPrincipal` |
| `statistics-service/src/test/java/com/piggymetrics/statistics/client/ExchangeRatesTestServer.java` | 3-5 | `com.sun.net.httpserver.HttpExchange`, `HttpHandler`, `HttpServer` |

**Verified, not assumed:** both APIs still exist on JDK 17. `java --list-modules` shows `jdk.security.auth@17.0.19` and
`jdk.httpserver@17.0.19`, and `javap --module jdk.security.auth com.sun.security.auth.UserPrincipal` /
`javap --module jdk.httpserver com.sun.net.httpserver.HttpServer` both resolve. They are *exported* JDK-specific APIs,
not `sun.*` internals, so JPMS strong encapsulation does not hit them. The real cost is (a) they are non-portable and
compile with warnings, and (b) `UserPrincipal` is only used to stand in for an authenticated principal in MVC tests —
it should be replaced by `spring-security-test` (`@WithMockUser` / `SecurityMockMvcRequestPostProcessors.user(...)`)
during the security rework anyway. `ExchangeRatesTestServer` should move to WireMock or MockWebServer.

**No `sun.*` (internal) imports exist:** `grep -rn "^import sun\." --include=*.java .` returns nothing. So there is no
JEP-403 strong-encapsulation blocker in application code.

### 5.2 Reflective access

`grep -rn "setAccessible\|getDeclaredField\|getDeclaredMethod\|Class.forName\|ReflectionTestUtils\|Whitebox\|newInstance()" --include=*.java .`
returns **no matches**. The codebase performs no application-level reflection, so the JDK 16+ "illegal reflective
access denied by default" change (JEP 396/403) has no direct effect here. The exposure is entirely in *libraries*:
Mockito 2.15's Byte Buddy, JaCoCo 0.7.6's instrumentation, and CGLIB inside Spring 5.0 all instrument bytecode and all
predate JDK 17 class-file support — which is why §2 flags them as JDK blockers even though the source is clean.

### 5.3 Date/time

Mixed legacy `java.util.Date` and `java.time` — the risk is behavioural, not API removal (nothing used here was removed
in 9-21).

| Location | Lines | Concern |
| --- | --- | --- |
| `statistics-service/src/main/java/com/piggymetrics/statistics/service/StatisticsServiceImpl.java` | 53-54 | `LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()` — the data point key depends on the **container's default time zone**. The Dockerfiles set no `TZ` (`*/Dockerfile:1-5`), so the JVM inherits the image default (UTC). Any base-image change must keep that, or historical keys shift by a day. |
| `statistics-service/.../domain/ExchangeRatesContainer.java` | 6, 12 | `private LocalDate date = LocalDate.now()` — cache freshness key, same default-zone dependency |
| `statistics-service/.../service/ExchangeRatesServiceImpl.java` | 15, 34 | `!container.getDate().equals(LocalDate.now())` — day-boundary comparison, no clock injection, so it is untestable without a fixed `Clock` |
| `account-service/.../service/AccountServiceImpl.java` | 17, 62, 85 | `new Date()` written to Mongo |
| `notification-service/.../service/RecipientServiceImpl.java` | 12, 39, 70 | `new Date()` |
| `notification-service/.../repository/RecipientRepository.java` | 16, 20 | `@Query` with **server-side JavaScript** date arithmetic (`new Date(new Date().setDate(new Date().getDate() - …))`). This is evaluated by the MongoDB server, not the JVM; server-side JavaScript execution can be disabled and its support has changed across server generations (mongo 3 → 7), so this is a driver/server risk, not a JDK one. Needs a runtime check (§ open items). |

Two JDK-level behaviour changes that *do* apply to this code, both at the Java 8→17 step:
* **CLDR locale data** became the default in Java 9 (JEP 252) — affects any date/number formatting that relies on
  default locale patterns (Jackson serialization of `Date`, and the email templates in `notification-service`).
* **Two-digit year / `Date` parsing leniency** is unchanged, but Jackson's `Date` ↔ ISO-8601 output changes with the
  Jackson generation bump (§6), which is the practically visible difference in API responses and stored documents.

### 5.4 JVM flags in scripts and images

| Location | Flags | Status on 17/21 |
| --- | --- | --- |
| all nine Java Dockerfiles, line 5 (e.g. `config/Dockerfile:5`) | `java -Xmx200m -jar …` | Valid on every JDK. But on 17/21 the same heap with a different GC default (G1 vs. Serial choice depends on container CPU/memory detection) and higher metaspace/CDS overhead: 200 MB is tight and must be re-measured. |
| `scripts/demo/start-local.sh:64` | `-Xmx192m -XX:MaxMetaspaceSize=128m` | Both flags still exist on 17/21. `MaxMetaspaceSize=128m` is the risky one: Spring 6/Boot 3 loads more classes, and metaspace exhaustion presents as a startup `OutOfMemoryError: Metaspace`, not as a heap error. |

**No removed flags are used.** `grep -rn "XX:" --include=Dockerfile --include=*.sh .` shows only `MaxMetaspaceSize`;
there is no `-XX:MaxPermSize`, no `-XX:+UseConcMarkSweepGC` (removed in 14/15), and no `-XX:+UseParNewGC`. The scripts
also hard-code a JDK 8 path (`scripts/demo/start-local.sh:11`, `docs/RUNBOOK.md:12`) and CI pins Temurin 8
(`.github/workflows/build.yml:21-22`) — three places that must change together at the JDK step.

---

## 6. Transitive risk list

None of these are declared anywhere in the repo; all are forced to move by the Boot upgrades. Versions are `[BOM]`
(managed) values read from the published Boot BOMs, never resolved here.

| Library | Now (Boot 2.0.3) | Boot 2.3.12 | Boot 2.7.18 | Boot 3.0.13 | Why it matters here |
| --- | --- | --- | --- | --- | --- |
| Jackson | `2.9.6` | `2.11.4` | `2.13.5` | `2.14.3` | Every REST payload and every Mongo document mapping. 2.9→2.10+ tightens polymorphic-type handling and changes `java.time`/`Date` serialization defaults — visible in `ExchangeRatesContainer.date` (a `LocalDate` field) and in `Account.lastSeen` (`java.util.Date`). Also clears the 2.9.x deserialization CVE family. |
| Tomcat (embedded) | `8.5.31` | `9.0.46` | `9.0.83` | **`10.1.16`** | Tomcat 10 is the `jakarta.servlet` cut. This is the mechanical reason no `javax.servlet`-based library (i.e. `spring-security-oauth2`) can survive into Boot 3. |
| Spring Framework | `5.0.7.RELEASE` | `5.2.15.RELEASE` | `5.3.31` | **`6.0.14`** | Spring 6 requires Java 17 and drops `javax`. `WebSecurityConfigurerAdapter`, used by the security configs in auth/account/statistics/notification, is deprecated in 5.7 and **removed** in Spring Security 6. |
| Spring Security | `5.0.6.RELEASE` | `5.3.9.RELEASE` | `5.7.11` | **`6.0.8`** | The 5.7 lambda-DSL / `SecurityFilterChain` rewrite is unavoidable and is where the OAuth2 rework lands. |
| Netty | `4.1.25.Final` | `4.1.65.Final` | `4.1.101.Final` | `4.1.101.Final` | Pulled in by Sleuth/Reactor and the Turbine stream path. No API break expected for this codebase; the version jump is large enough that a CVE re-scan after the bump is worthwhile. |
| Logback | `1.2.3` | `1.2.3` | `1.2.12` | **`1.4.11`** | Logback 1.3/1.4 require Java 11+/17 and pair with SLF4J 2.x (`1.7.25` → `2.0.9`). Any custom appender config in `config/src/main/resources/shared/*.yml` must be re-checked at Boot 3. |
| MongoDB driver | `3.6.4` | `4.0.6` | `4.6.1` | `4.8.2` | The 3.x→4.x driver rewrite is the single most disruptive transitive change before Boot 3: it breaks the pinned `flapdoodle 1.50.3` (§2) and changes `MongoTemplate` behaviour used across four services. |
| Hibernate Validator | `6.0.10.Final` | `6.1.7.Final` | `6.2.5.Final` | **`8.0.1.Final`** | HV 8 implements Jakarta Validation 3.0 — the runtime half of §4.1. |
| Jakarta/Javax Mail | `com.sun.mail:javax.mail 1.6.1` | Javax `1.6.2` + Jakarta `1.6.7` | Javax `1.6.2` + Jakarta `1.6.7` | **Jakarta Mail `2.1.2` only** | The runtime half of §4.2. |
| RxJava | Boot BOM declares `1.3.8`, Spring Cloud Netflix BOM declares `1.2.0` — **mediation result unknown** `[UNRESOLVED]` | — | — | absent | Hystrix's reactive core. Disappears entirely with the Hystrix retirement; listed because the conflicting BOM declarations mean nobody currently knows which version runs. Resolve with the tree command in §8. |
| Archaius / eureka-core / hystrix-core / ribbon-core / zuul-core (`0.7.6` / `1.9.2` / `1.5.12` / `2.2.5` / `1.3.1`) | present | present | Eureka only | Eureka only | The Netflix internals behind the starters; they vanish with §2's retirements (except Eureka's). |
| Guava (transitive, via Netflix stack) | conflicts with the locally pinned `19.0` (`statistics-service/pom.xml:63-67`) `[UNRESOLVED]` | — | — | — | The local pin may already be downgrading what the Netflix libraries expect. Only a resolved tree can say. |

BOM sources for the four columns:
Boot 2.0.3 — published `spring-boot-dependencies:2.0.3.RELEASE` POM;
Boot 2.3.12 / 2.7.18 / 3.0.13 — `spring-boot-dependencies/build.gradle` plus `gradle.properties` at the matching tag:
<https://raw.githubusercontent.com/spring-projects/spring-boot/v2.3.12.RELEASE/spring-boot-project/spring-boot-dependencies/build.gradle>,
<https://raw.githubusercontent.com/spring-projects/spring-boot/v2.7.18/spring-boot-project/spring-boot-dependencies/build.gradle>,
<https://raw.githubusercontent.com/spring-projects/spring-boot/v3.0.13/spring-boot-project/spring-boot-dependencies/build.gradle>,
<https://raw.githubusercontent.com/spring-projects/spring-boot/v3.0.13/gradle.properties>.

---

## 7. Configuration surface that moves with the code

Not dependencies, but the same retirements invalidate these keys (documented, not changed):

| File | Keys |
| --- | --- |
| `config/src/main/resources/shared/application.yml` | `eureka.*` |
| `config/src/main/resources/shared/application-local.yml` | `eureka.*` |
| `config/src/main/resources/shared/account-service.yml` | `hystrix.*` |
| `config/src/main/resources/shared/gateway.yml` | `zuul.*`, `ribbon.*`, `hystrix.*` |
| `config/src/main/resources/shared/gateway-local.yml` | `zuul.*` |

`zuul.routes.*` becomes `spring.cloud.gateway.routes[*]` (different model: predicates/filters, not path→service maps),
`hystrix.command.*` becomes `resilience4j.circuitbreaker.instances.*`, and `ribbon.*` becomes
`spring.cloud.loadbalancer.*`. `eureka.*` keys survive unchanged.

---

## Open items for runtime verification

Each item is blocked on something this session could not have (JDK 8, or a non-rate-limited Maven Central), and each is
a concrete command.

1. **Resolved dependency graph (blocks every `[BOM]`/`[UNRESOLVED]` claim above).**
   ```bash
   JAVA_HOME=/path/to/jdk8 PATH="$JAVA_HOME/bin:$PATH" \
     mvn -B -Pfull dependency:tree -Dverbose -DoutputFile=/tmp/deptree.txt -DappendOutput=true
   JAVA_HOME=/path/to/jdk8 PATH="$JAVA_HOME/bin:$PATH" \
     mvn -B -Pfull dependency:list -DoutputFile=/tmp/deplist.txt -DappendOutput=true
   ```
   Then re-check specifically: the RxJava mediation result (`1.2.0` vs `1.3.8`), the effective Guava version in
   `statistics-service`, `turbine-core`'s version (absent from the fetched BOMs), and the Reactor Core child version.

2. **Baseline green build on JDK 8** — establishes the "before" state the migration is measured against.
   ```bash
   JAVA_HOME=/path/to/jdk8 PATH="$JAVA_HOME/bin:$PATH" mvn -B -fae verify
   JAVA_HOME=/path/to/jdk8 PATH="$JAVA_HOME/bin:$PATH" mvn -B -fae -Pfull verify
   ```
   (Same two commands CI runs: `.github/workflows/build.yml:27,30`.)

3. **Confirm the JaCoCo/JDK-17 blocker empirically** (predicted: agent failure before tests run):
   ```bash
   JAVA_HOME=/path/to/jdk17 PATH="$JAVA_HOME/bin:$PATH" \
     mvn -B -pl account-service -Denforcer.skip=true test
   ```
   Run this in a scratch clone — `-Denforcer.skip` is a CLI flag, not a POM change, but it must not become habit.

4. **Confirm `com.sun.*` availability on JDK 21** (verified here for 17 only):
   ```bash
   /path/to/jdk21/bin/javap --module jdk.security.auth com.sun.security.auth.UserPrincipal
   /path/to/jdk21/bin/javap --module jdk.httpserver com.sun.net.httpserver.HttpServer
   ```

5. **Time-zone assumption in the container** (§5.3 depends on the default zone being UTC):
   ```bash
   docker run --rm eclipse-temurin:8-jre  java -XshowSettings:properties -version 2>&1 | grep user.timezone
   docker run --rm eclipse-temurin:17-jre java -XshowSettings:properties -version 2>&1 | grep user.timezone
   ```

6. **Memory headroom under Boot 3 defaults** — whether `-Xmx200m` / `-XX:MaxMetaspaceSize=128m`
   (`scripts/demo/start-local.sh:64`) still starts a service. Only measurable after the first module reaches Boot 3.

7. **Whether `spring-security-oauth2-autoconfigure 2.6.8` actually runs on Boot 2.7** — the register states only the
   evidenced fact (no GA build targets 2.7). If a fallback is ever considered, it needs an explicit smoke test of
   `auth-service` on Boot 2.7 with the 2.6.8 artifact before anyone relies on it.
