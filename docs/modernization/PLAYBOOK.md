# Stage 1 playbook — test infrastructure (D-08, D-09, D-10, D-19)

Written from the mandatory `account-service` pilot
(`.agents/skills/modernization-uplift/SKILL.md:140-151`) for an engineer with
no prior context. Everything below was executed and observed on this box; no
step is speculative.

## 0. Facts you need before you start

| Fact | Value | Where it comes from |
| --- | --- | --- |
| JDK | Temurin 8, `JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64` | root enforcer `pom.xml:58-78`; never switch JDK to make a build pass |
| Boot / Spring Cloud | `2.0.3.RELEASE` / `Finchley.RELEASE` — unchanged in Stage 1 | `pom.xml:11-22` |
| BOM-managed flapdoodle | `2.0.3` (`embedded-mongo.version`) | `spring-boot-dependencies-2.0.3.RELEASE.pom:54` |
| BOM-managed json-path | `2.4.0` (`json-path.version`) | `spring-boot-dependencies-2.0.3.RELEASE.pom:108` |
| BOM-managed JUnit 5 | Jupiter `5.1.1`, Platform `1.1.0` | `spring-boot-dependencies-2.0.3.RELEASE.pom:112-113` |
| BOM-managed Mockito | `2.15.0` — keep it, do not pin | `spring-boot-dependencies-2.0.3.RELEASE.pom:143` |
| Surefire | `2.21.0` by default; **too old to run JUnit 5 without a provider artifact** | `spring-boot-dependencies-2.0.3.RELEASE.pom:140` |
| Guava | **not** managed by either BOM; Netflix Ribbon pulls `16.0`/`16.0.1` at `runtime` scope only | `ribbon-core-2.2.5.pom:21-24`, `ribbon-httpclient-2.2.5.pom:63-66` |
| Mirror | `aliyun-central`; first build of a module downloads a lot and is slow | `docs/modernization/PREFLIGHT.md` |

## 1. Shared file — one line, one reason

`pom.xml` gets exactly one addition:

```xml
<maven-surefire-plugin.version>2.22.2</maven-surefire-plugin.version>
```

Surefire `2.21.0` (the Boot-managed version) needs the separate
`junit-platform-surefire-provider` artifact to see a Jupiter engine; `2.22.x`
detects the JUnit Platform natively. Overriding the Boot property is one line
in the shared POM and keeps every module identical, versus six per-module
plugin declarations plus a provider dependency. This is the only shared-file
change in Stage 1 and it is build-scope only.

## 2. Per-module POM edits

For each of `auth-service`, `account-service`, `statistics-service`,
`notification-service`:

1. **D-08** — `jacoco-maven-plugin` `0.7.6.201602180812` → `0.8.11`.
2. **D-09** — delete `<version>1.50.3</version>` from
   `de.flapdoodle.embed:de.flapdoodle.embed.mongo`; it then resolves to the
   Boot-managed `2.0.3`.
3. **D-19** — delete `<version>2.2.0</version>` from
   `com.jayway.jsonpath:json-path`; it then resolves to `2.4.0`.
4. **D-10** — exclude JUnit 4 from the starter and add the two Jupiter
   artifacts *without versions*:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
    <exclusions>
        <exclusion>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-api</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-engine</artifactId>
    <scope>test</scope>
</dependency>
```

`gateway`, `monitoring`, and `turbine-stream-service` get step 4 only — they
have no JaCoCo, flapdoodle, or json-path declaration.

Excluding `junit:junit` is deliberate: without it a leftover JUnit 4
annotation compiles and is then silently **not executed** by the Jupiter
engine, which reads as a green build with fewer tests. With the exclusion the
compiler finds every leftover.

## 3. Per-test-class edits

Mechanical, in this order:

| JUnit 4 | JUnit 5 |
| --- | --- |
| `@RunWith(SpringRunner.class)` | `@ExtendWith(SpringExtension.class)` (`org.springframework.test.context.junit.jupiter.SpringExtension`, present in Spring 5.0) |
| `org.junit.Test` | `org.junit.jupiter.api.Test` |
| `@Before` | `@BeforeEach` |
| `org.junit.Assert.*` | `org.junit.jupiter.api.Assertions.*` |
| `@Test(expected = X.class)` | `assertThrows(X.class, () -> …)` around **only** the statement that was expected to throw |
| `org.mockito.Matchers.any` | `org.mockito.ArgumentMatchers.any` (same class, `Matchers` is the deprecated alias) |

Rules that carried the pilot:

- Do **not** move a whole method body into `assertThrows`. The JUnit 4
  `expected` attribute accepts a throw from anywhere in the method, but the
  fixture lines before the throwing call are setup, not assertions; wrapping
  the throwing call alone preserves the assertion and tightens nothing that
  was previously observable.
- Keep `MockitoAnnotations.initMocks(this)` in the `@BeforeEach`. Mockito
  `2.15.0` (Boot-managed) predates `mockito-junit-jupiter`, so there is no
  Jupiter extension to switch to, and `openMocks` does not exist until
  Mockito 3.4. Same call, same lifecycle position.
- Keep the assertion library and the matchers exactly as they are (Hamcrest
  stays Hamcrest).

## 4. The one non-mechanical case — `OutputCapture`

`account-service/.../StatisticsServiceClientFallbackTest` used
`@Rule public OutputCapture outputCapture`. Spring Boot 2.0.x ships
`OutputCapture` only as a JUnit 4 `TestRule`; the Jupiter
`OutputCaptureExtension` arrives in Boot 2.2. Under Jupiter a `@Rule` field is
**ignored**, so leaving it in place turns the only assertion in that test into
a no-op.

The pilot adds
`account-service/src/test/java/com/piggymetrics/account/test/OutputCaptureExtension.java`:
a `BeforeEachCallback`/`AfterEachCallback` that tees `System.out`/`System.err`
into a buffer, exposes the same `reset()` / `expect(Matcher)` /
`toString()` surface, and — like the rule — verifies the expectations *after*
the test method. The test body is otherwise untouched.

It was proved non-vacuous: with the expected string changed to a string that
never appears, the test failed with
`Expected: a string containing "ZZZ_SHOULD_NOT_MATCH"`, and the change was
reverted. Do this check once if you port the extension elsewhere.

## 5. Build and proof commands

Pilot (what was actually run):

```bash
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -pl account-service test
```

Result: `Tests run: 14, Failures: 0, Errors: 0`, per class 1/1/6/1/5 —
identical to the baseline in `docs/modernization/BASELINE.md` §2, with
`jacoco-maven-plugin:0.8.11:report` running and flapdoodle `2.0.3` resolved.

Stage proof (both profiles, both required):

```bash
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -fae verify          # expect 59
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -fae -Pfull verify   # expect 61
```

Compare per-module counts against `docs/modernization/BASELINE.md`, not just
the exit code: a test that silently stops running is the failure mode this
stage is most exposed to.

## 6. Errors seen and their resolution

| Symptom | Cause | Resolution |
| --- | --- | --- |
| Jupiter tests not executed, `Tests run: 0` | Surefire `2.21.0` has no JUnit Platform provider | Surefire `2.22.2` property override in the root POM (§1) |
| `@Rule` assertion silently skipped | JUnit 4 rules are inert under Jupiter | `OutputCaptureExtension` (§4) |
| Guava `<version>` cannot simply be deleted | Guava is in neither BOM, and its only transitive path is `runtime`-scope Ribbon `16.x`, which would break compilation of `ImmutableMap` use in `statistics-service` main code | Left pinned; see the gap list |
| Mongo `MongoSocketOpenException` lines in the log | flapdoodle shutdown noise after the repository test finishes | Expected; the build is green, baseline shows the same |

## 7. Gap list (not done in Stage 1)

1. **Guava `19.0` pin (part of D-19).** Not BOM-managed under Boot 2.0.3 /
   Finchley, compile-scope in `statistics-service` main code, and the only
   transitive supply is Ribbon's `runtime`-scope `16.x`. Deleting the version
   breaks compilation; bumping it to a CVE-fixed line (≥ 32.0.0 for
   CVE-2020-8908) moves a compile-scope library out from under Netflix
   Eureka/Ribbon, which is runtime behaviour and outside a test-scope stage.
   Lands with the Netflix retirement (D-05/D-06/D-07, stage 3) or with the
   Boot generation that manages Guava.
2. **flapdoodle 3.x/4.x (driver-coupled half of D-09).** `3.x` renames
   `IMongodConfig` → `MongodConfig` and targets the Mongo `4.x` driver, which
   Boot 2.0.3's `EmbeddedMongoAutoConfiguration` cannot drive. It lands with
   the Boot 2.3 bump in Stage 2, where the driver moves 3.6.4 → 4.0.6 (D-16).
3. **Mockito Jupiter extension.** Available only from Mockito 2.17; revisit
   when a later Boot generation manages it.
4. **`com.sun.security.auth.UserPrincipal` in controller tests** stays as-is —
   D-18, stage 3.

# Stage 2 playbook — Boot 2.3.12 / Hoxton.SR12 (D-01, D-09 driver-coupled half, D-16, D-23)

Written from the completed `account-service` pilot on
`stage-2-boot-23-hoxton`. The facts and resolutions below are the observed
pilot results, not predictions. The pilot artifacts are under
`/home/ubuntu/stage2/artifacts/`.

## 0. Facts you need before you start

| Fact | Before | After | Evidence |
| --- | --- | --- | --- |
| JDK | Temurin 8, `JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64` | unchanged | root enforcer and pilot verify log |
| Spring Boot parent | `2.0.3.RELEASE` | `2.3.12.RELEASE` | `pom.xml`; `pilot-account-verify.log` |
| Spring Cloud train | `Finchley.RELEASE` | `Hoxton.SR12` | `pom.xml`; `pilot-account-verify.log` |
| Spring Data MongoDB | `2.0.8.RELEASE` | `3.0.9.RELEASE` | `pilot-account-deps-before/after-spring-data-mongodb.log` |
| MongoDB driver (D-16) | `org.mongodb:mongodb-driver`, plus `bson`/`mongodb-driver-core`, `3.6.4` | `org.mongodb:mongodb-driver-sync`, plus `bson`/`mongodb-driver-core`, `4.0.6` | `pilot-account-deps-before/after-mongodb.log` |
| Flapdoodle (D-09 driver-coupled half) | `de.flapdoodle.embed.mongo:2.0.3`; `embed.process:2.0.2` | `embed.mongo:2.2.0`; `embed.process:2.1.2` | `pilot-account-deps-before/after-flapdoodle.log` |
| JUnit Jupiter BOM | `5.1.1` | `5.6.3` | `pilot-account-deps-before/after-junit-mockito.log` |
| Mockito BOM | `2.15.0` | `3.3.3` | `pilot-account-deps-before/after-junit-mockito.log` |
| Embedded Mongo binary | existing baseline cache | MongoDB `3.5.5` downloaded to `~/.embedmongo` | `pilot-account-verify.log`; `~/.embedmongo/linux/mongodb-linux-x86_64-3.5.5.tgz` |

The flapdoodle change is the driver-coupled half of D-09 that Stage 1
deliberately deferred. The deferral and its reason are recorded in
`docs/as-is/05-dependency-and-eol-register.md:312` and
`docs/modernization/UPLIFT-NOTES.md` §4.

## 1. Shared-file rule

The root `pom.xml` is the only shared edit. Change exactly these two values:

```xml
<version>2.0.3.RELEASE</version>
```

to:

```xml
<version>2.3.12.RELEASE</version>
```

and:

```xml
<spring-cloud.version>Finchley.RELEASE</spring-cloud.version>
```

to:

```xml
<spring-cloud.version>Hoxton.SR12</spring-cloud.version>
```

Do not change `java.version`, Surefire, the enforcer, repositories, or Maven
settings. No other shared file is part of this fan-out.

## 2. Ordered fan-out procedure

1. Finish and commit this playbook before touching a module.
2. Verify each remaining unit in this order:
   `auth-service`, `statistics-service`, `notification-service`, `gateway`,
   `registry`, `config`, `monitoring`, `turbine-stream-service`.
3. Do not move to the next unit until the current unit has the expected
   baseline test count and zero failures, errors, and skips.
4. `monitoring` and `turbine-stream-service` use the `full` profile.
5. Do not modify tests or change production behavior to make a test pass.

The pilot showed that Boot 2.3's `spring-boot-starter-web` no longer supplies
the validation starter by itself. `account-service` nevertheless compiled
without a direct validation dependency because its
`spring-cloud-netflix-hystrix-stream -> spring-cloud-stream ->
spring-boot-starter-validation:2.3.12.RELEASE` path supplied it transitively.
The current `auth-service` and `gateway` POMs have no Hystrix-stream path, so
they must be checked independently; do not infer their validation classpath
from the account pilot. This transitive supply disappears when Netflix and
Hystrix are retired in a later stage.

Under Boot 2.3, `EmbeddedMongoAutoConfiguration` still drives Flapdoodle. The
pilot log showed `o.s.b.a.mongo.embedded.EmbeddedMongo` starting MongoDB 3.5.5
and `d.f.embed.mongo.MongodExecutable` starting the process. This is not the
Boot 3 removal yet.

## 3. Allowed module changes

Only make a change when the current build or behavior proves it necessary:

* Add an unversioned `org.springframework.boot:spring-boot-starter-validation`
  dependency only after a module fails to compile on `javax.validation`.
* If `CustomConversions` no longer resolves or is silently ignored, replace
  it in both `statistics-service` and `notification-service` with
  `MongoCustomConversions` consistently: import, bean return type, and `new`
  expression. The bean must be typed as `MongoCustomConversions`, because
  `MongoDataAutoConfiguration` backs off on a bean of that type.
* If compilation or a behavior test proves the old converters are no longer
  applied, migrate the statistics converters from `DBObject`/
  `BasicDBObject` to `org.bson.Document`. Preserve the exact document shape:
  put `date` first as `java.util.Date`, then `account` as `String`; read those
  same fields with the same casts.

The pilot identified these fan-out hazards but did not change them:

* `statistics-service` and `notification-service` define custom Mongo
  conversion beans.
* `statistics-service` has `DataPointIdReaderConverter` and
  `DataPointIdWriterConverter` using `com.mongodb.DBObject` and
  `com.mongodb.BasicDBObject`.
* The post-pilot driver tree no longer contains the legacy
  `org.mongodb:mongodb-driver` artifact, so these must be proven during the
  statistics-service build rather than assumed safe.

## 4. Commands

Use Temurin 8 on every Maven invocation:

```bash
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -pl <unit> verify
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -pl monitoring -Pfull verify
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -pl turbine-stream-service -Pfull verify
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -fae verify
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -fae -Pfull verify
```

The pilot dependency checks were:

```bash
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -pl account-service dependency:tree -Dincludes=de.flapdoodle.embed:*
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -pl account-service dependency:tree -Dincludes=org.mongodb:*
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -pl account-service dependency:tree -Dincludes=org.springframework.data:spring-data-mongodb
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -pl account-service dependency:tree -Dincludes=org.junit.jupiter:*,org.mockito:*
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -pl account-service dependency:tree -Dincludes=org.springframework.boot:spring-boot-starter-validation
```

Save each unit's complete output under
`/home/ubuntu/stage2/artifacts/fanout-<unit>.log`. Compare both module and
class counts to `docs/modernization/BASELINE.md` §2.

## 5. Observed pilot errors and resolutions

| Observed first line | Resolution |
| --- | --- |
| `WARNING: TestEngine with ID 'junit-vintage' failed to discover tests` | Red-herring discovery warning caused by the intentionally excluded JUnit 4 class; the Jupiter suite still ran. No code change. |
| `Could not locate PropertySource: 401 ... "Unauthorized"` | Non-fatal test-context warning; no change. |
| `Broker not available; cannot force queue declarations during start: java.net.ConnectException: Connection refused (Connection refused)` | RabbitMQ was not part of the narrow unit run; no change. |
| `Error during update statistics for account: test` | Expected output asserted by `StatisticsServiceClientFallbackTest`; no change. |
| `Resolved [org.springframework.web.bind.MethodArgumentNotValidException: Validation failed for argument ...]` | Expected controller validation output; no change. |
| `Registering converter from class java.time.LocalDateTime to class org.joda.time.LocalDateTime as reading converter ...` | Non-fatal Spring Data warning; no change. |

The pilot had no compilation errors, test failures, dependency failures, or
forced production API migrations. The final result was 14 tests,
0 failures/errors/skips.

## 6. Remaining gaps

The remaining fan-out work must establish whether the two custom-conversion
configurations and the statistics `DBObject` converters continue to register
and apply under Spring Data MongoDB 3.0.9 and MongoDB driver 4.0.6. Do not
accept a green test count if a converter is silently ignored. The full-reactor
T0 and any T1 re-run remain stage gates after the ordered fan-out.
