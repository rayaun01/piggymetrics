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
