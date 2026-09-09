# 05 — Dependency and End-of-Life Register

Scope: the nine **Java/Maven** modules of this repository on branch `devin/1788845133-stage-0-demo-harness`.
The three .NET services (`compliance-service`, `currency-exchange-service`, `fraud-detection-service`) are out of scope
(`docs/OUT-OF-SCOPE-DOTNET.md`).

This document is evidence for the migration plan. Nothing here is asserted without either a repo citation
(`path:line-range`) or a URL.

### Baseline vs. stage 0 — read the citations with this in mind

All `path:line` citations below are against the **stage-0 branch**. Stage 0 (PR #1) is a harness change, not a
migration step, but it did touch four things this register depends on. Where it did, the row states the **baseline
(`master`) value** and notes that stage 0 already changed it — the as-is record is the baseline, not the harness.
Established with `git diff --name-status origin/master origin/devin/1788845133-stage-0-demo-harness`:

| Item | Baseline (`master`) | Stage-0 branch | Effect on this register |
| --- | --- | --- | --- |
| Java base image (9 Dockerfiles) | `FROM java:8-jre` (deprecated Docker Official Image, superseded by `openjdk`/`eclipse-temurin`) | `FROM eclipse-temurin:8-jre` | §2 row states the baseline; the deprecated-image problem is already fixed |
| Reactor layout | all nine modules in `<modules>` | seven modules + `monitoring`/`turbine-stream-service` behind the `full` profile | The "CI's default job skips the Hystrix modules" caveat in §1.3 is a stage-0 property, not baseline |
| JDK-8 enforcer (`pom.xml:56-80`) | **absent** | added, `[1.8,1.9)` | The enforcer is a stage-0 guard rail; the baseline simply fails differently on a newer JDK |
| `ExchangeRatesTestServer` (`com.sun.net.httpserver`) | **absent** | added by stage 0 | §5: the baseline has only the four `com.sun.security.auth.UserPrincipal` usages |

Everything else the register flags (POM coordinates, `javax.*` count, JaCoCo, flapdoodle, `mongo:3`, the Netflix stack)
is identical on `master` and on the stage-0 branch — verified with `git grep` against `origin/master`.

### Pre-work forced before any Netflix work

The register's most actionable finding: three items break at the **first Boot bump (2.0.3 → 2.3.12)**, independently of
the Netflix retirement. They are ordered before every architectural change because they break the build and the test
suite, which is what every later stage is validated against.

1. **JaCoCo `0.7.6.201602180812`** (auth, account, statistics, notification) — the agent fails on class-file majors
   above Java 8, so `mvn verify` dies before a single test runs the moment the build JDK moves. Forces a bump to
   ≥0.8.7 (Java 17) / ≥0.8.11 (Java 21) **before** the JDK move at S3, and there is no reason not to do it at S1.
2. **flapdoodle `1.50.3`** (same four modules) — built against the Mongo 3.x driver API, and the resolved graph confirms
   the baseline driver is exactly `3.6.4` (§1.2), while Boot 2.3.12 moves it to `4.0.6`. Every `*RepositoryTest` breaks at step one of the migration. Forces flapdoodle 3.x/4.x or
   Testcontainers *in the same commit as the Boot 2.3 bump*, not after it.
3. **JUnit 4 / Mockito `2.15.0`** — survives 2.3, but Boot 2.4 drops the vintage engine from `spring-boot-starter-test`
   and Mockito 2's Byte Buddy cannot instrument JDK 17 classes. Forces the JUnit 5 decision at the 2.3 → 2.7 step.

Consequence for the plan: **stage 1 is a test-infrastructure stage, not a version bump.** If it is scheduled as "bump
 Boot and Spring Cloud", it ends with a red suite and no way to tell whether the Netflix work that follows is correct.

## 0. Method and evidence classes

Every version in this document carries one of the following tags. They are not interchangeable.

| Tag | Meaning |
| --- | --- |
| `[POM]` | Literally declared in a module POM in this repo. Cited with `path:lines`. |
| `[BOM]` | Not declared here; the version comes from a published dependency-management POM (Spring Boot 2.0.3 / Spring Cloud Finchley and its component BOMs) that was fetched and read. This is what Maven *would* pick as the managed version, before conflict mediation with transitive graphs. |
| `[RESOLVED]` | Maven actually resolved it. From a `dependency:tree` / `dependency:list` run over the full nine-module reactor (`-Pfull`, runtime scope) under JDK 8 against a working Maven mirror, both reaching `BUILD SUCCESS` with the enforcer satisfied. That run was performed **outside this session** (see below) and its output is the authority for §1.2. |
| `[UNRESOLVED]` | Not established by either route. |

**The resolution did not happen on this box.** `mvn dependency:list` and `mvn dependency:tree` were run here as direct
plugin goals (so the `validate`-phase JDK-8 enforcer at `pom.xml:56-80` was never triggered), but every invocation
failed at parent-POM download with `status code: 429, reason phrase: Too Many Requests` from Maven Central, and the box
has no populated local repository and no JDK 8 (`docs/RUNBOOK.md:12` points at `/home/ubuntu/.local/jdks/jdk8u504-b01`,
which does not exist here). Per the task constraints no other JDK was installed, no POM was modified, and the stack was
not booted. The `[RESOLVED]` figures in §1.2 come from an equivalent run on a machine with JDK 8 and a working mirror.
Consequently:

* A `[BOM]` version is a **declared/managed** fact read out of the published BOMs — what Maven *would* select before
  mediation. Where §1.2 gives a `[RESOLVED]` value for the same artifact, the resolved value wins.
* Transitive-only artifacts not covered by §1.2 (Tomcat, Netty, Logback, …) still carry only their BOM-managed versions
  and remain unverified against a tree.
* Remaining gaps and their commands are in [Open items for runtime verification](#open-items-for-runtime-verification).

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

## 1. Dependency inventory (declared, managed, and resolved)

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

### 1.2 Resolved reactor graph

From the external `-Pfull` resolution described in §0. **254 distinct artifacts across 104 distinct groupIds** in
runtime scope. Three findings here are stronger evidence than anything the POMs declare.

**(a) The baseline depends on a non-GA artifact.** `com.netflix.turbine:turbine-core` resolves to **`2.0.0-DP.2`** — a
*developer preview*, reached transitively through `spring-cloud-starter-netflix-turbine-stream` (§1.1). Nothing in this
repo asked for a preview build; the starter's own dependency chain did. There is no GA 2.x Turbine to upgrade to (the
last GA tag is `turbine-1.0.0`, 2014-09-07 — §2), so "port Turbine forward" has no target. This is a decisive argument
for **deleting** the Turbine tier rather than migrating it, and it reinforces §7.1: the stream it aggregates is not
even served.

**(b) The baseline already has classpath divergence — eight artifacts resolve to more than one version**, before any
migration work:

| Artifact | Versions present | Note |
| --- | --- | --- |
| `org.codehaus.jackson:jackson-core-asl` | `1.9.2`, `1.9.13` | **The one that matters.** Jackson **1.x** is a dead library (superseded by `com.fasterxml.jackson` in 2012), present at *two* versions, alongside Jackson `2.9.6`. It is not purely transitive noise — it reaches the source: `account-service/src/main/java/com/piggymetrics/account/domain/Account.java:3` and `statistics-service/src/main/java/com/piggymetrics/statistics/domain/Account.java:3` both `import org.codehaus.jackson.annotate.JsonIgnoreProperties`, i.e. the **Jackson 1.x** annotation, on domain types that Jackson **2.x** serializes. |
| `org.codehaus.jackson:jackson-mapper-asl` | `1.9.2`, `1.9.13` | Same. See (f) below — this pair has a behavioural consequence, not just a hygiene one. |
| `com.google.guava:guava` | `15.0`, `16.0`, `19.0` | Confirms the suspicion in §6: the local `19.0` pin (`statistics-service/pom.xml:63-67`) sits over two older versions the Netflix stack drags in. |
| `com.jayway.jsonpath:json-path` | `2.2.0`, `2.4.0` | The local test pin (§1.1) versus the BOM value, both present. |
| `net.minidev:json-smart` | `2.2.1`, `2.3` | Follows json-path. |
| `net.minidev:accessors-smart` | `1.1`, `1.2` | Follows json-smart. |
| `org.hdrhistogram:HdrHistogram` | `2.1.9`, `2.1.10` | Netflix metrics stack. |
| `org.ow2.asm:asm` | `5.0.3`, `5.0.4` | ASM 5.x cannot read class files above Java 8 — relevant to the JDK-17 step alongside JaCoCo (§2). |

Maven mediation picks one version per artifact per module, so the divergence is mostly latent today. It stops being
latent when the upgrades move these libraries: the Netflix retirement (S2) removes the older side of most of these
pairs, which is a *benefit* of doing S2 before the version jumps, not merely a cost.

**(c) Two reactive stacks, and a split-groupId RxNetty.** `io.reactivex:rxnetty` / `rxnetty-contexts` / `rxnetty-servo`
resolve to `0.4.9` across seven modules, while **`com.netflix.rxnetty:rx-netty` `0.3.18`** — a *different groupId at a
much older version* — resolves into `turbine-stream-service` alongside them. This is not Maven mediation failing; the
two coordinates are distinct artifacts, so mediation never considers them together and both land on the classpath with
overlapping packages. Nothing in this repo asks for either. It is one more reason the Turbine module is a deletion
candidate rather than a migration target, and a reminder that "same library, different groupId" divergence is invisible
to any tooling that dedupes on coordinate.

**(d) `feign-hystrix 9.5.1` is present, so a dead Feign fallback is a *configuration* fact, not a missing dependency.**
`io.github.openfeign:feign-hystrix 9.5.1` resolves into account, statistics and notification. Two clients declare a
fallback — `account-service/.../client/StatisticsServiceClient.java:10` and
`statistics-service/.../client/ExchangeRatesClient.java:10` — but `feign.hystrix.enabled: true` is set only for
`account-service` (`config/src/main/resources/shared/account-service.yml:24-26`). `statistics-service` has no such key,
and the property defaults to false, so `ExchangeRatesClientFallback` is **never wired**: the resilience it looks like it
provides does not exist. The artifact being present is what makes this diagnosable — the gap is one line of YAML, not a
classpath problem.

**(e) No JWT library is present.** No `io.jsonwebtoken:*` artifact appears anywhere in the resolved runtime graph. The
baseline uses opaque OAuth2 tokens checked against `auth-service` (`CustomUserInfoTokenServices` in the resource-server
configs), not JWTs. Any Spring Authorization Server design at S2 should treat "introduce JWTs" as a **new capability**
with its own decision, not as a like-for-like port.

**(f) An inert `@JsonIgnoreProperties` — the register's clearest argument for capturing golden masters before any
dependency work.** Both `Account` domain classes carry the annotation, imported from **Jackson 1.x**:

```java
// account-service/src/main/java/com/piggymetrics/account/domain/Account.java:3,14
// statistics-service/src/main/java/com/piggymetrics/statistics/domain/Account.java:3
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@Document(collection = "accounts")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Account { … }
```

All serialization is done by Jackson **2.9.6** (§1.2, all nine modules), which reads only
`com.fasterxml.jackson.annotation` annotations. It never sees this one. **The annotation does nothing today.**

What makes it the sharpest item in the register is that the *obvious* fix — rewrite the import to
`com.fasterxml.jackson.annotation.JsonIgnoreProperties` — is not behaviour-preserving, and its effect differs by call
path:

| Path | `FAIL_ON_UNKNOWN_PROPERTIES` today | Effect of porting the import |
| --- | --- | --- |
| The running services (Boot's auto-configured `ObjectMapper`) | **disabled** by Boot — "`DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` is disabled" (<https://docs.spring.io/spring-boot/docs/2.0.3.RELEASE/reference/htmlsingle/#howto-customize-the-jackson-objectmapper>) | none — unknown properties are already ignored, so the annotation is *redundant* here |
| The controller tests, which build their own mapper: `private static final ObjectMapper mapper = new ObjectMapper();` (`account-service/src/test/java/com/piggymetrics/account/controller/AccountControllerTest.java:32`, and the same line in the auth, statistics and notification controller tests) | **enabled** — Jackson's own default, since Boot's customizations do not apply to a hand-constructed mapper | **behaviour changes**: payloads with unknown properties that throw today would start deserializing silently |

So the one-line "tidy the import" diff is inert in production and semantically load-bearing in the test suite — the exact
opposite of what a reviewer would assume, and invisible in the diff itself. Deleting the import and the annotation
outright is the behaviour-preserving option; porting it is a behaviour change that needs its own decision.

This is why golden masters (recorded request/response captures, plus a green baseline suite) must exist **before** stage
1 rather than being produced alongside it: the repo already contains a change that looks like cleanup and is not, and
nothing but a recorded before/after can distinguish them. Handling: leave the import alone through S1-S3 (harmless while
dead), then treat it as its own change once the Codehaus artifacts are confirmed gone from the graph after S2.

**Resolved versions of the migration-order shortlist** (all `[RESOLVED]`; module counts as reported by the run):

| Artifact | Resolved version | Modules |
| --- | --- | --- |
| `com.fasterxml.jackson.core:jackson-databind` / `jackson-core` | `2.9.6` | all nine |
| `com.fasterxml.jackson.core:jackson-annotations` | `2.9.0` | all nine |
| `com.netflix.archaius:archaius-core` | `0.7.6` | 8 |
| `com.netflix.eureka:eureka-client` / `eureka-core` | `1.9.2` | 7 |
| `com.netflix.hystrix:hystrix-core` | `1.5.12` | 8 |
| `com.netflix.hystrix:hystrix-javanica` | `1.5.12` | gateway, account |
| `com.netflix.hystrix:hystrix-metrics-event-stream` / `hystrix-serialization` | `1.5.12` | gateway, account, monitoring |
| `com.netflix:netflix-commons-util` / `netflix-eventbus` / `netflix-infix` | `0.3.0` | Netflix stack |
| `com.netflix:netflix-statistics` | `0.1.1` | Netflix stack |
| `com.netflix.ribbon:ribbon`, `-core`, `-eureka`, `-httpclient`, `-loadbalancer`, `-transport` | `2.2.5` | 7 |
| `com.netflix.rxnetty:rx-netty` | `0.3.18` | turbine-stream |
| `com.netflix.servo:servo-core` | `0.12.21` | Netflix stack |
| `com.netflix.turbine:turbine-core` | **`2.0.0-DP.2`** (non-GA) | turbine-stream |
| `com.netflix.zuul:zuul-core` | `1.3.1` | gateway only |
| `junit:junit` | `4.12` | test scope |
| `org.mockito:mockito-core` | `2.15.0` | test scope |
| `io.reactivex:rxjava` | `1.3.8` (single version — the Boot BOM value wins over Netflix's `1.2.0`) | all eight non-config modules |
| `io.reactivex:rxjava-reactive-streams` | `1.2.1` | gateway, account, turbine-stream |
| `io.reactivex:rxnetty` / `rxnetty-contexts` / `rxnetty-servo` | `0.4.9` | 7 |
| `io.projectreactor:reactor-core` | `3.1.8.RELEASE` | account, statistics, notification, turbine-stream |
| `io.projectreactor.ipc:reactor-netty` | `0.7.8.RELEASE` | turbine-stream |
| `org.springframework.boot:spring-boot-starter-reactor-netty` | `2.0.3.RELEASE` | turbine-stream |
| `io.github.openfeign:feign-hystrix` | `9.5.1` | account, statistics, notification |
| `org.mongodb:bson` / `mongodb-driver` / `mongodb-driver-core` | `3.6.4` | auth, account, statistics, notification |
| `org.springframework.boot:spring-boot` | `2.0.3.RELEASE` | all nine |
| **every** `org.springframework.cloud:*` artifact | `2.0.0.RELEASE` | incl. `-netflix-zuul` (gateway), `-netflix-hystrix-dashboard` (monitoring), `-netflix-hystrix-stream` (account/statistics/notification), `-netflix-turbine-stream` (turbine-stream), `-netflix-ribbon` (8), `-netflix-eureka-client` (7) / `-eureka-server` (registry), `-security` and `-starter-oauth2` (the four secured services), `-sleuth-core` (5), `-stream` / `-stream-binder-rabbit` / `-starter-stream-rabbit` / `-starter-bus-amqp` (account/statistics/notification + turbine-stream), `-openfeign-core` (3), `-config-server` (config) / `-config-client` (all nine) |
| `org.springframework.security.oauth:spring-security-oauth2` | `2.2.1.RELEASE` | the four secured services |
| `org.springframework.security.oauth.boot:spring-security-oauth2-autoconfigure` | `2.0.0.RELEASE` | the four secured services |

The MongoDB driver line is the one to read twice: `3.6.4` **resolved** confirms the flapdoodle finding (§2, and the
pre-work list at the top) against a real graph rather than a BOM — the pinned flapdoodle `1.50.3` is bound to exactly
this driver generation, and Boot 2.3.12 moves it to `4.0.6`.

**Baseline resolved version ≠ upgrade ceiling.** For the two legacy OAuth2 artifacts these are different facts and are
kept apart deliberately: the *resolved baseline* is `spring-security-oauth2 2.2.1.RELEASE` and
`spring-security-oauth2-autoconfigure 2.0.0.RELEASE` (above); the *highest GA that will ever exist* is `2.5.2.RELEASE`
and `2.6.8` respectively (§2). The gap between them is upgrade headroom; the ceiling is what makes Boot 2.7 unreachable
with these libraries.

**The baseline binds a message broker in four modules that no demo tier runs.**
`spring-cloud-stream-binder-rabbit` / `-starter-stream-rabbit` / `-starter-bus-amqp` resolve into account, statistics,
notification and turbine-stream, and `spring.rabbitmq.host` is configured (`config/src/main/resources/shared/application.yml:26-27`,
`application-local.yml:15-16`). Only T3 actually starts a broker (`docker-compose.yml:3-4`, `rabbitmq:3-management`);
T1 (bare JVMs) and T2 (`docker-compose.core.yml`) run none. So in the tiers the demo is measured on, four services carry
a binder, a `hystrix-stream` publisher and a Bus AMQP client against a broker that is not there. That is an as-is fact
about the baseline, not a harness artefact, and it compounds §7.1: the entire Hystrix/Turbine metrics path is inert in
the measured configuration.

### 1.3 Declared plugins

| Plugin | Version | Modules | Modernization relevance |
| --- | --- | --- | --- |
| `org.apache.maven.plugins:maven-enforcer-plugin` | `3.4.1` `[POM]` (`pom.xml:56-80`) | root (all) | Hard-fails any lifecycle build not on `[1.8,1.9)`. Must be re-targeted, not removed, at each stage. |
| `org.springframework.boot:spring-boot-maven-plugin` | M `[BOM]` | all nine (`config/pom.xml:32-38`, `registry/pom.xml:36-42`, `gateway/pom.xml:48-54`, `auth-service/pom.xml:68-74`, `account-service/pom.xml:88-94`, `statistics-service/pom.xml:90-96`, `notification-service/pom.xml:89-95`, `monitoring/pom.xml:36-42`, `turbine-stream-service/pom.xml:46-52`) | Repackaging behaviour and `finalName` conventions change across Boot 2.x→3.x. |
| `org.jacoco:jacoco-maven-plugin` | `0.7.6.201602180812` `[POM]` (`auth-service/pom.xml:75-93`, `account-service/pom.xml:95-113`, `statistics-service/pom.xml:97-115`, `notification-service/pom.xml:96-114`) | auth, account, statistics, notification | **Blocks the JDK upgrade on its own.** 0.7.6 (2016) predates JaCoCo's support for class-file majors 53+; JaCoCo added Java 17 support in 0.8.7 and Java 21 support in 0.8.9/0.8.11. See <https://www.jacoco.org/jacoco/trunk/doc/changes.html>. Any `mvn verify` on JDK 17 will fail in the agent before a single test runs. |

`monitoring` and `turbine-stream-service` are only built under `-Pfull` (`pom.xml:46-54`) **on this branch** — on
`master` all nine modules are in the default reactor, so the profile split is a stage-0 change, not baseline. With the
split in place a default `mvn verify` never compiles the Hystrix Dashboard or Turbine code. Retirement work on those two must be explicitly scheduled or it
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
| **`spring-cloud-starter-netflix-turbine-stream` `2.0.0.RELEASE`** (`turbine-stream-service/pom.xml:28-31`), pulling **`com.netflix.turbine:turbine-core 2.0.0-DP.2`** — a *developer-preview* build `[RESOLVED]` (§1.2) | Last in 2.2.9.RELEASE (2021-07-06). Netflix Turbine's last **GA** tag is `turbine-1.0.0`, **2014-09-07**; the resolved `2.0.0-DP.2` never reached GA | Spring Cloud 2020.0 removed-module list (above); Turbine tag date from `git log turbine-1.0.0` on <https://github.com/Netflix/Turbine> | Removed from the train, and there is **no GA artifact to upgrade to** — the baseline is already on a preview build. Combined with §7.1 (the aggregated stream is not served at all), this tier has no working behaviour to preserve | **Delete the module.** Micrometer + a central scrape replaces the intent; nothing replaces Turbine as such | S2 (schedule first — it is a deletion, not a migration) |
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
| **Java base image — baseline `java:8-jre`** (`git show origin/master:config/Dockerfile` line 1, and the same in the other eight). `java:8-jre` is a **deprecated Docker Official Image** (the `java` repository was deprecated in favour of `openjdk`, itself now superseded by vendor images such as `eclipse-temurin`; the repository's last image push was 2017-03-23, so the baseline images carry eight years of unpatched OS packages). **Stage 0 already replaced it** with `eclipse-temurin:8-jre` on this branch (`config/Dockerfile:1`, `registry/Dockerfile:1`, `gateway/Dockerfile:1`, `auth-service/Dockerfile:1`, `account-service/Dockerfile:1`, `statistics-service/Dockerfile:1`, `notification-service/Dockerfile:1`, `monitoring/Dockerfile:1`, `turbine-stream-service/Dockerfile:1`) | The baseline `java:8-jre` image is deprecated and unmaintained (<https://hub.docker.com/_/java>). Its stage-0 replacement, Temurin 8, is *still maintained* — "End of Availability: at least Dec 2030" (Temurin 17 at least Oct 2027, Temurin 21 at least Dec 2029) | <https://hub.docker.com/_/java>, <https://adoptium.net/support/> | Two separate problems. The baseline image is a supply-chain problem (deprecated repository, no patches) — already fixed by stage 0. The **Java 8 runtime** must still go, and the reason is not Java 8 EOL: it is that **Boot 3 requires Java 17** (§3), which is a hard requirement on a fixed date in the plan. Temurin 8 being available until at least 2030 does not make staying on Java 8 viable — it only means the deadline is set by the Spring generation, not by the JDK vendor | `eclipse-temurin:17-jre` at S3, `eclipse-temurin:21-jre` at S5 | S3 / S5 |
| **`mongo:3` base image** (`mongodb/Dockerfile:1`, still present on this branch) | MongoDB 3.x is end-of-life | <https://www.mongodb.com/legal/support-policy/lifecycles> | **Legacy/full tier only — do not "fix" it.** This image is built solely by `docker-compose.dev.yml` (`build: mongodb` at lines 24, 34, 44, 54) for the four per-service Mongos of the T3 full Java Compose tier (`docs/RUNBOOK.md:83-91`). The demo's core path does not use it: T1 uses a local mongod (`docs/RUNBOOK.md:20-34`) and T2 uses `mongo:7.0` (`docker-compose.core.yml:3`). T3 is deliberately frozen as the "original" tier, so bumping this image changes the thing the migration is measured against | None during the migration. Record it as known EOL infrastructure in the frozen tier; retire it only if/when T3 is retired | **No stage** — deliberately not touched |
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

### 5.1 `com.sun.*` usage — 5 files on this branch, 4 in the baseline, and **it is not what it looks like**

| File | Lines | API |
| --- | --- | --- |
| `account-service/src/test/java/com/piggymetrics/account/controller/AccountControllerTest.java` | 7 | `com.sun.security.auth.UserPrincipal` |
| `auth-service/src/test/java/com/piggymetrics/auth/controller/UserControllerTest.java` | 6 | `com.sun.security.auth.UserPrincipal` |
| `notification-service/src/test/java/com/piggymetrics/notification/controller/RecipientControllerTest.java` | 10 | `com.sun.security.auth.UserPrincipal` |
| `statistics-service/src/test/java/com/piggymetrics/statistics/controller/StatisticsControllerTest.java` | 13 | `com.sun.security.auth.UserPrincipal` |
| `statistics-service/src/test/java/com/piggymetrics/statistics/client/ExchangeRatesTestServer.java` **(added by stage 0, not baseline)** | 3-5 | `com.sun.net.httpserver.HttpExchange`, `HttpHandler`, `HttpServer` |

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
| **Jackson 1.x** (`org.codehaus.jackson:jackson-core-asl` / `-mapper-asl`) | `1.9.2` **and** `1.9.13` both present `[RESOLVED]` (§1.2) | — | — | — | Dead since 2012, unmaintained, and *used in source*: two `Account` domain classes import `org.codehaus.jackson.annotate.JsonIgnoreProperties` while Jackson 2.x does the actual serialization — so the annotation is silently ignored today. Nothing upgrades it. Do **not** treat rewriting the import as a cleanup — see §1.2(f): it is inert in the running services (Boot disables `FAIL_ON_UNKNOWN_PROPERTIES`) but load-bearing in the four controller tests that construct their own `ObjectMapper`. Deleting the annotation preserves behaviour; porting it does not. Confirm the transitive copies are gone after the S2 retirement. |
| Jackson 2.x | `2.9.6` `[RESOLVED]` (§1.2) | `2.11.4` | `2.13.5` | `2.14.3` | Every REST payload and every Mongo document mapping. 2.9→2.10+ tightens polymorphic-type handling and changes `java.time`/`Date` serialization defaults — visible in `ExchangeRatesContainer.date` (a `LocalDate` field) and in `Account.lastSeen` (`java.util.Date`). Also clears the 2.9.x deserialization CVE family. |
| Tomcat (embedded) | `8.5.31` | `9.0.46` | `9.0.83` | **`10.1.16`** | Tomcat 10 is the `jakarta.servlet` cut. This is the mechanical reason no `javax.servlet`-based library (i.e. `spring-security-oauth2`) can survive into Boot 3. |
| Spring Framework | `5.0.7.RELEASE` | `5.2.15.RELEASE` | `5.3.31` | **`6.0.14`** | Spring 6 requires Java 17 and drops `javax`. `WebSecurityConfigurerAdapter`, used by the security configs in auth/account/statistics/notification, is deprecated in 5.7 and **removed** in Spring Security 6. |
| Spring Security | `5.0.6.RELEASE` | `5.3.9.RELEASE` | `5.7.11` | **`6.0.8`** | The 5.7 lambda-DSL / `SecurityFilterChain` rewrite is unavoidable and is where the OAuth2 rework lands. |
| Netty | `4.1.25.Final` | `4.1.65.Final` | `4.1.101.Final` | `4.1.101.Final` | Pulled in by Sleuth/Reactor and the Turbine stream path. No API break expected for this codebase; the version jump is large enough that a CVE re-scan after the bump is worthwhile. |
| Logback | `1.2.3` | `1.2.3` | `1.2.12` | **`1.4.11`** | Logback 1.3/1.4 require Java 11+/17 and pair with SLF4J 2.x (`1.7.25` → `2.0.9`). Any custom appender config in `config/src/main/resources/shared/*.yml` must be re-checked at Boot 3. |
| MongoDB driver | `3.6.4` | `4.0.6` | `4.6.1` | `4.8.2` | The 3.x→4.x driver rewrite is the single most disruptive transitive change before Boot 3: it breaks the pinned `flapdoodle 1.50.3` (§2) and changes `MongoTemplate` behaviour used across four services. |
| Hibernate Validator | `6.0.10.Final` | `6.1.7.Final` | `6.2.5.Final` | **`8.0.1.Final`** | HV 8 implements Jakarta Validation 3.0 — the runtime half of §4.1. |
| Jakarta/Javax Mail | `com.sun.mail:javax.mail 1.6.1` | Javax `1.6.2` + Jakarta `1.6.7` | Javax `1.6.2` + Jakarta `1.6.7` | **Jakarta Mail `2.1.2` only** | The runtime half of §4.2. |
| RxJava | `1.3.8`, single version across all eight non-config modules `[RESOLVED]` (§1.2) — the Boot BOM's `1.3.8` wins the mediation against the Spring Cloud Netflix BOM's `1.2.0` | — | — | absent | Hystrix's reactive core. No divergence after all, and it disappears entirely with the Hystrix retirement (S2) rather than needing an upgrade. |
| Reactor | `reactor-core 3.1.8.RELEASE` (account, statistics, notification, turbine-stream); `reactor-netty 0.7.8.RELEASE` + `spring-boot-starter-reactor-netty 2.0.3.RELEASE` (turbine-stream only) `[RESOLVED]` (§1.2) | Boot-managed | Boot-managed | Boot-managed (Reactor 2022.x) | Not used by application code; pulled in by the stream/Turbine path. Moves with Boot on its own. The `turbine-stream-service`-only Netty server disappears with that module. |
| Archaius / eureka-core / hystrix-core / ribbon-core / zuul-core (`0.7.6` / `1.9.2` / `1.5.12` / `2.2.5` / `1.3.1`) | present | present | Eureka only | Eureka only | The Netflix internals behind the starters; they vanish with §2's retirements (except Eureka's). |
| Guava (transitive, via Netflix stack) | **`15.0`, `16.0` and `19.0` all present in the reactor** `[RESOLVED]` (§1.2), against the local `19.0` pin (`statistics-service/pom.xml:63-67`) | — | — | — | Confirmed divergence, not a suspicion: the Netflix stack brings 15.0/16.0 while `statistics-service` pins 19.0. Most of the old side disappears with the S2 Netflix retirement. |

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

### 7.1 Reconciliation with observed T1 runtime behaviour

Four observations were made against a live T1 stack (not by this session — recorded here because they change what the
register implies). The static evidence explains all four, and one of them narrows the migration scope:

| Observation | Explanation grounded in this repo | Consequence for the register |
| --- | --- | --- |
| `config`: `/actuator/health` → 200, `/health` → 401 | Boot 2 moved actuator endpoints under `/actuator` (Boot 1's `/health` no longer maps); `config` declares `spring-boot-starter-security` (`config/pom.xml:23-26`) with no permit rule, so the unmapped path is authenticated before it can 404 | No change; confirms the Boot-2 actuator base path is already in effect |
| Five Eureka registrations | Matches the six Eureka clients minus whichever T1 process is not run; Eureka is the one Netflix component the matrix keeps | Reinforces §2: Eureka is version-bumped, not replaced |
| `/hystrix.stream` → **404 on every port, including 4000** | **Expected, and it matters.** No `management.endpoints.web.exposure.include` exists anywhere in the repo (`grep -rn "management\." --include=*.yml .` → no matches), and Boot 2.0 web-exposes only `health` and `info` by default. `hystrix.stream` is a management endpoint, so it is on the classpath but not exposed | **The Hystrix metrics tier is already non-functional in the baseline.** `spring-cloud-netflix-hystrix-stream` (3 services), the `monitoring` dashboard and `turbine-stream-service` aggregate a stream that nothing serves. Their retirement (S2) is therefore a *deletion*, not a migration — there is no working behaviour to preserve or to replicate in Micrometer. This is the cheapest item in S2 and should be scheduled first |
| `/actuator/health` → 404 on all four resource services | Two distinct causes: `auth-service` does not declare `spring-boot-starter-actuator` at all (only account/statistics/notification do — §1.1), and the other three set a servlet context path (`/accounts`, `/statistics`, `/notifications` at `config/src/main/resources/shared/account-service.yml:19-21`, `statistics-service.yml:19-21`, `notification-service.yml:10-12`), so their actuator lives at `/{context}/actuator/health`. *Inference from configuration, not verified against the running stack* | Health-check surface is inconsistent in the baseline. Worth fixing during the migration (Boot 3 makes actuator the only supported health mechanism), but it is not a blocker. Verification command in the open-items section |

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
   **Closed** by the external resolution in §1.2 for every artifact this register names, including the RxJava mediation
   (`1.3.8`) and the Reactor versions. Re-run it after each stage, not to fill a gap, but to confirm the divergence in
   §1.2(b) actually shrinks as the Netflix stack leaves.

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

7. **Actuator surface** — §7.1 explains the observed `/actuator/health` 404s from configuration alone; confirm on the
   running stack, since the fix (or the decision to leave it) belongs to the plan:
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' http://localhost:6000/accounts/actuator/health
   curl -s -o /dev/null -w '%{http_code}\n' http://localhost:7000/statistics/actuator/health
   curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8000/notifications/actuator/health
   ```
   If those return 200, the 404s were purely the context-path, and `auth-service` (no actuator dependency at all) is the
   only real gap.

8. **Confirm the Hystrix stream is inert rather than misconfigured** — §7.1 concludes the metrics tier serves nothing.
   The discriminating check is whether exposing the endpoint brings it back:
   ```bash
   MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE='*' <start account-service> \
     && curl -s -o /dev/null -w '%{http_code}\n' http://localhost:6000/accounts/actuator/hystrix.stream
   ```
   A 200 here means the tier is merely unexposed (still delete it, but the S2 note should say "disabled", not "never
   worked"); a 404 means it is genuinely absent.

9. **Whether `spring-security-oauth2-autoconfigure 2.6.8` actually runs on Boot 2.7** — the register states only the
   evidenced fact (no GA build targets 2.7). If a fallback is ever considered, it needs an explicit smoke test of
   `auth-service` on Boot 2.7 with the 2.6.8 artifact before anyone relies on it.
