# Uplift notes — Stage 1 `test-infrastructure`

`UPLIFT_NOTES_PATH` per `.agents/skills/modernization-uplift/SKILL.md:16,187-196`.
Scope of this record is Stage 1 only (`docs/modernization/BRIEF.md` §3, stage 1:
D-08, D-09, D-10, D-19). Stages 2–5 are untouched.

| Item | Value |
| --- | --- |
| Stage branch | `stage-1-test-infrastructure` (off `master`) |
| Baseline revision | `b52b806641b0bbdedc78dc8deb9bba6b3ff137be` (`docs/modernization/BASELINE.md` §1) |
| Toolchain | Temurin 8 `/usr/lib/jvm/temurin-8-jdk-amd64`, Maven 3.6.3, mirror `aliyun-central` |
| Playbook | `docs/modernization/PLAYBOOK.md` (written from the `account-service` pilot) |

## 1. Delta → fix mapping, and how each fix was produced

Every edit in this stage was made **by hand**. No automated recipe was run:
OpenRewrite could not execute in Phase 1 (`docs/modernization/PREFLIGHT.md`
Check 2) and nothing here is claimed as tool output.

| Delta | Register fact | Fix applied | Units | Tool / hand |
| --- | --- | --- | --- | --- |
| D-08 | JaCoCo `0.7.6.201602180812` predates Java 8 class-file support in the current line (`docs/as-is/05-dependency-and-eol-register.md:308`) | `jacoco-maven-plugin` → `0.8.11` (4 declarations) | auth, account, statistics, notification | hand |
| D-09 | flapdoodle `1.50.3` local pin, target left open between 3.x/4.x and Testcontainers (`05-…:312`, BRIEF §7 q3) | local `<version>1.50.3</version>` deleted; resolves to the Boot-managed `2.0.3`. Driver-coupled move to 3.x/4.x deferred — see §4 | auth, account, statistics, notification | hand |
| D-10 | JUnit `4.12` + Mockito `2.15.0` (`05-…:300-306`) | `junit:junit` excluded from `spring-boot-starter-test`; BOM-managed `junit-jupiter-api` / `junit-jupiter-engine` added; 25 test classes migrated to Jupiter; Mockito left BOM-managed at `2.15.0` | all 7 test-bearing units + `registry` (POM only) | hand |
| D-19 | local `json-path` / `guava` pins (`05-…:314`) | `json-path` `2.2.0` pin deleted → BOM `2.4.0`. **`guava 19.0` pin left in place** — see §3 | account, auth, statistics, notification | hand |

One shared-file change was required and is disclosed in the PR: the root
`pom.xml` gains `<maven-surefire-plugin.version>2.22.2</maven-surefire-plugin.version>`,
because Boot-managed Surefire `2.21.0` cannot discover the JUnit Platform
without a separate provider artifact (`docs/modernization/PLAYBOOK.md` §1).
`config/src/main/resources/shared/*.yml` was not touched.

## 2. Dual-run diff / target-only statement

**Target-only, by construction.** Stage 1 changes test-scope build inputs only;
the source and target toolchains are the same JDK (Temurin 8), so a dual-run
across two JDKs would compare a run against itself (`BASELINE.md` §4). The
proof that carries this stage is before/after at one toolchain and one
revision. Stage 4 is the first stage where the JDK actually differs and the
dual-run gate becomes meaningful (`BRIEF.md` §6).

Diff result: **no result deltas at all.** Per-module and per-class test counts
are identical before and after in both profiles, so §5's triage rule has
nothing to classify — there is no intended change, no regression, no
unexplained delta.

## 3. Residual manual deltas (in Stage 1's catalogue, not done here)

1. **`guava 19.0` pin (the second half of D-19).** Guava is managed by neither
   the Boot `2.0.3` nor the Finchley BOM, is `compile` scope in
   `statistics-service` main source, and its only transitive supply is Netflix
   Ribbon's `runtime`-scope `16.x`. Deleting the pin breaks compilation;
   raising it moves a compile-scope library out from under Eureka/Ribbon,
   which is a runtime change inside a test-scope stage. Deferred deliberately,
   flagged to the approver for a separate focused change with its own T1
   re-run, and repeated in `PLAYBOOK.md` §7.
2. **flapdoodle 3.x/4.x (the driver-coupled half of D-09).** See §4.

## 4. Deferred modernization, explicitly not done

| Deferred | Why | Lands in |
| --- | --- | --- |
| flapdoodle `3.x`/`4.x` or Testcontainers | `3.x` renames `IMongodConfig` → `MongodConfig` and targets the Mongo `4.x` driver; Boot `2.0.3`'s `EmbeddedMongoAutoConfiguration` cannot drive it, and the driver only moves `3.6.4` → `4.0.6` with D-16. Boot-managed `2.0.3` is the least-behaviour-change option that keeps the four repository tests on Java 8 / Boot 2.0.3 today (`05-…:312`, BRIEF §7 q3) | Stage 2, with D-16 |
| `guava 19.0` | §3.1 | Stage 3 (Netflix retirement) or the Boot generation that manages Guava |
| `mockito-junit-jupiter` | Not published until Mockito `2.17`; Boot `2.0.3` manages `2.15.0` and pinning it is out of scope | a later Boot generation |
| T3 (Compose/AMQP, D-07) | `BRIEF.md` §6 schedules T3 at stage 5 and §4 assigns the AMQP-path measurement to the stage-3 session. Stage 1 touches no messaging code, no `docker-compose*.yml`, and no shared YAML, so a T3 run has no Stage 1 oracle value | stage 3 records the AMQP path result; T3 tier runs at stage 5 |
| Boot / Spring Cloud / Java version moves | stage ladder — explicitly forbidden in this stage | Stages 2–5 |

## 5. Per-unit results

Target build = `mvn -B -fae verify` on the stage branch. Baseline reproduction
= the same unit's counts in `BASELINE.md` §2 at `b52b806`.

| Unit | Target build | Tests (target) | Tests (baseline) | Reproduces baseline |
| --- | --- | ---: | ---: | --- |
| config | SUCCESS | 0 | 0 | yes |
| registry | SUCCESS | 0 | 0 | yes (no test sources; POM aligned only) |
| gateway | SUCCESS | 2 | 2 | yes |
| auth-service | SUCCESS | 9 | 9 | yes |
| account-service | SUCCESS | 14 | 14 | yes |
| statistics-service | SUCCESS | 16 | 16 | yes |
| notification-service | SUCCESS | 18 | 18 | yes |
| monitoring (`-Pfull`) | SUCCESS | 1 | 1 | yes |
| turbine-stream-service (`-Pfull`) | SUCCESS | 1 | 1 | yes |
| **Total** | **BUILD SUCCESS** | **59 / 61 (`-Pfull`)** | **59 / 61** | **identical** |

Per-class counts were compared as well, not only per-module totals: a test that
silently stops being discovered is the failure mode a JUnit-runner move is most
exposed to, and it hides inside a matching module total only if two classes
move in opposite directions. None did.

T1 golden master: the raw wire text captured after the stage is byte-identical
to `BASELINE.md` §3 (`diff -u` over the raw text, no JSON parsing), including
`0.0330`, `0.6800`, `"USD":1` beside `"JPY":147.85`, and the `+0000` Jackson
date form. The stack was browser-tested as well; screenshots and a recording
are attached to the stage PR.

## 6. Playbook pointer and remaining gaps

`docs/modernization/PLAYBOOK.md` is the fan-out procedure written from the
`account-service` pilot: shared-POM change (§1), per-module POM edits (§2), the
mechanical JUnit 4 → 5 table (§3), the single non-mechanical case —
`OutputCapture` has no Jupiter form in Boot 2.0.x, so a local extension
reproduces it (§4) — proof commands (§5), observed errors (§6), and the gap
list (§7).

Remaining gaps after Stage 1: the two residual deltas in §3, the deferrals in
§4, and — noted from review of the stage PR — that `OutputCaptureExtension`
mutates process-global `System.out`/`System.err` and is therefore safe only
under serial execution. Jupiter parallel execution is not enabled anywhere in
this repository (no `junit-platform.properties`, no Surefire `parallel`
configuration), and enabling it is not part of any stage in the ladder; anyone
who enables it must revisit that extension first.

# Uplift notes — Stage 2 `boot-23-hoxton`

This section records the completed Stage 2 uplift on
`stage-2-boot-23-hoxton`: Spring Boot `2.0.3.RELEASE` →
`2.3.12.RELEASE`, Spring Cloud `Finchley.RELEASE` → `Hoxton.SR12`, with Java
remaining Temurin 8. Stage 1 above is unchanged.

## 1. Delta → fix mapping, and how each fix was produced

No OpenRewrite recipe or other automated migration recipe was run. The
catalog records zero tool-derived deltas
(`docs/modernization/DELTA-CATALOG.md:515-517`); all Stage 2 edits were made
by hand.

| Delta | Observed Stage 2 resolution | Units / blast radius | Tool / hand |
| --- | --- | --- | --- |
| D-01 | The root `pom.xml` changed exactly two values: the Boot parent `2.0.3.RELEASE` → `2.3.12.RELEASE` and `spring-cloud.version` `Finchley.RELEASE` → `Hoxton.SR12`. No other shared POM or configuration edit was part of the fan-out. | Shared file; all 9 units. `docs/modernization/DELTA-CATALOG.md:80-94` | hand |
| D-16 | The resolved MongoDB driver moved from `org.mongodb:mongodb-driver` plus `bson`/`mongodb-driver-core` `3.6.4` to `org.mongodb:mongodb-driver-sync` plus `bson`/`mongodb-driver-core` `4.0.6`. This was transitive from the BOM; no application code change was required. The `com.mongodb.DBObject`-typed statistics converters and the `CustomConversions` beans in statistics-service and notification-service still work. Spring Data MongoDB `3.0.9.RELEASE` declares `CustomConversions` as extending `MongoCustomConversions`; `javap` verification of the resolved jar therefore shows why Boot's `@ConditionalOnMissingBean(MongoCustomConversions.class)` back-off still sees the bean. The user converters were registered in `/home/ubuntu/stage2/artifacts/fanout-statistics-service-converter-trace-argline.log:247-248,508-509` and `/home/ubuntu/stage2/artifacts/fanout-notification-service-converter-trace.log:247-248,498-499`. | auth, account, statistics, notification; stored-document compatibility. `docs/modernization/DELTA-CATALOG.md:336-353` | hand |
| D-09 driver-coupled half | Flapdoodle moved from `de.flapdoodle.embed.mongo:2.0.3` and `embed.process:2.0.2` to `embed.mongo:2.2.0` and `embed.process:2.1.2`. Boot 2.3 still ships `EmbeddedMongoAutoConfiguration`, which continued to drive Flapdoodle and started the embedded MongoDB `3.5.5` binary. This is the half Stage 1 explicitly deferred; the deferral is recorded at `docs/as-is/05-dependency-and-eol-register.md:312` and in Stage 1 §4 above. | auth, account, statistics, notification repository tests. `/home/ubuntu/stage2/artifacts/pilot-account-deps-before-flapdoodle.log`; `/home/ubuntu/stage2/artifacts/pilot-account-deps-after-flapdoodle.log`; `/home/ubuntu/stage2/artifacts/pilot-account-verify.log` | hand |
| D-15 | Boot 2.3.12 manages Jackson `2.11.4` where Boot 2.0.3 managed `2.9.6`. Left at its defaults the move drifts Jackson's `java.util.Date` offset form `+0000` → `+00:00` in `/statistics/current` `id.date`, and the same unnormalized drift was confirmed for `Account.lastSeen` on `/accounts/current` and `/accounts/demo` (`/home/ubuntu/stage2/artifacts/gm-before-vs-after.diff`). Jackson 2.11's `StdDateFormat` default *is* `+00:00`; the drift is the documented D-15 risk at `docs/modernization/DELTA-CATALOG.md:316-334` and `docs/as-is/05-dependency-and-eol-register.md:513` and was adjudicated intended rather than a regression. Stage 2 nevertheless **pins the format to hold the recorded oracle** instead of accepting the drift: `spring.jackson.date-format: yyyy-MM-dd'T'HH:mm:ss.SSSZ` with `spring.jackson.time-zone: UTC` in `config/src/main/resources/shared/application.yml`. The explicit zone is required because a pattern-based `SimpleDateFormat` would otherwise follow the JVM default zone, whereas `StdDateFormat` defaulted to UTC. With the pin the wire text is byte-identical to the pre-bump and Stage 1 captures with zero adjudicated differences. The oracle was held, not re-recorded, so the Boot 3 stage re-raises the choice deliberately. | REST wire contract; account and statistics consumers. `/home/ubuntu/stage2/artifacts/gm-before-vs-after-r2.daynorm.diff` and `/home/ubuntu/stage2/artifacts/gm-stage1-vs-after-r2.daynorm.diff`, both empty | hand |
| D-23 | The two server-side JavaScript repository predicates remained unchanged. Evidence (a) passed on embedded MongoDB `3.5.5` with driver `4.0.6`: `RecipientRepositoryTest` ran 5/5, including `shouldFindReadyForRemindWhenFrequencyIsWeeklyAndLastNotifiedWas8DaysAgo` and `shouldNotFindReadyForBackupWhenFrequencyIsQuaterly`, whose assertions require non-empty results. Evidence (b) passed against the live T1 MongoDB `3.2.2`: both verbatim `$where` predicates matched the seeded recipient once, with no JavaScript-disabled error. Evidence (c), the scheduled application path, was not exercised because the served daily windows `0 0 0 * * *` and `0 0 12 * * *` had elapsed before notification-service started. | notification-service; scheduling behavior. `/home/ubuntu/stage2/artifacts/fanout-notification-service.log`, `/home/ubuntu/stage2/artifacts/d23-mongo-shell.txt`, `/home/ubuntu/stage2/artifacts/d23-notification-config.txt` | hand |
| OAuth2 bean-definition override | Boot 2.3 startup exposed a duplicate `oauth2ClientContext` definition. `@EnableOAuth2Client` is present at `account-service/.../AccountApplication.java:13`, `statistics-service/.../StatisticsApplication.java:23`, and `notification-service/.../NotificationServiceApplication.java:20`; the three services also receive `security.oauth2.client.*` from `config/src/main/resources/shared/account-service.yml:1-8`, `shared/statistics-service.yml:1-8`, and `shared/notification-service.yml:1-8`. The Boot 2.1+ default `spring.main.allow-bean-definition-overriding=false` made the collision fatal. The register records the OAuth starter at `docs/as-is/05-dependency-and-eol-register.md:101`, resolved OAuth artifacts at `:242-243`, the Hoxton removal boundary at `:342-343`, and the early hard wall at `:375-379`. The fix is narrowly scoped to `spring.main.allow-bean-definition-overriding: true` in each affected service's own `bootstrap.yml`; the shared Config Server delivery was packaged and served but empirically did not take effect early enough, so the fallback was required. T0 did not expose this because its test contexts never received the secured `security.oauth2.client.*` properties after the Config Server 401, so the OAuth autoconfiguration path backed off. | account-service, statistics-service, notification-service. `/home/ubuntu/stage2/artifacts/config-verify-oauth-override.log`, `/home/ubuntu/stage2/artifacts/t1-after-notification-service.log`, `/home/ubuntu/stage2/artifacts/t1-after-start-2.log` | hand |

## 2. Dual-run diff / target-only statement

**Target-only.** Stage 2 keeps Java 8 on both sides: the source and target
toolchain are the same JDK, as stated in `docs/modernization/BRIEF.md` §6.
Therefore the proof that carries this stage is before/after at one toolchain
and one revision: the T0 tables versus the post-edit tables, and the T1 wire
text versus the post-edit capture, compared as text. The first true dual-JDK
comparison is Stage 4.

## 3. Residual manual deltas

1. **Validation-starter transitivity.** Boot 2.3's
   `spring-boot-starter-web` no longer supplies the validation starter by
   itself. `account-service`, `statistics-service`, and
   `notification-service` still receive `spring-boot-starter-validation:
   2.3.12.RELEASE` only through
   `spring-cloud-netflix-hystrix-stream -> spring-cloud-stream`; none of the
   three declares it directly. `auth-service` and `gateway` have no such
   Hystrix-stream path today and neither declares the starter directly. This
   transitive supply disappears when Netflix and Hystrix are retired.
2. **Guava `19.0`.** The Stage 1 open pin remains open. It was not changed in
   Stage 2 because deleting it breaks statistics-service compilation and
   raising it moves a compile-scope library outside the narrowly scoped
   uplift; it needs its own focused decision and T1 run, as recorded in Stage
   1 §3.

## 4. Deferred modernization, explicitly not done

| Deferred | Stage 2 disposition |
| --- | --- |
| Java 17 | Not done; Java remains 8. |
| `javax` → `jakarta` | Not done. |
| Boot 3 / Spring Cloud 2022 | Not done. |
| Netflix retirement (Zuul, Hystrix, Ribbon, Turbine) | Not done. |
| OAuth2 rework | Not done. The register calls this an early hard wall at `docs/as-is/05-dependency-and-eol-register.md:375-379`. |
| Removing `@EnableOAuth2Client` | Not done. It remains the deferred alternative to the narrowly scoped bean-overriding flag. |
| D-07 / T3 AMQP verification | Not done; D-07 and T3 remain later-stage work. |
| Revisiting the pinned Jackson date form | Deferred. Stage 2 pins `spring.jackson.date-format` so the `+0000` oracle holds (§1, D-15). The pin is a deliberate hold, not a permanent decision: the Boot 3 stage should re-raise whether to keep the basic-offset form or adopt the library default. |

## 5. Per-unit target build results and baseline reproduction

The target per-unit counts reproduce `docs/modernization/BASELINE.md` §2:

| Unit | Target result | Tests | Failures | Errors | Skipped | Artifact |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| auth-service | SUCCESS | 9 | 0 | 0 | 0 | `/home/ubuntu/stage2/artifacts/fanout-auth-service.log` |
| account-service | SUCCESS | 14 | 0 | 0 | 0 | `/home/ubuntu/stage2/artifacts/pilot-account-verify.log` and `/home/ubuntu/stage2/artifacts/t1-oauth-bootstrap-verify.log` |
| statistics-service | SUCCESS | 16 | 0 | 0 | 0 | `/home/ubuntu/stage2/artifacts/fanout-statistics-service.log` |
| notification-service | SUCCESS | 18 | 0 | 0 | 0 | `/home/ubuntu/stage2/artifacts/fanout-notification-service.log` |
| gateway | SUCCESS | 2 | 0 | 0 | 0 | `/home/ubuntu/stage2/artifacts/fanout-gateway.log` |
| registry | SUCCESS | 0 | 0 | 0 | 0 | `/home/ubuntu/stage2/artifacts/fanout-registry.log` |
| config | SUCCESS | 0 | 0 | 0 | 0 | `/home/ubuntu/stage2/artifacts/fanout-config.log` |
| monitoring (`-Pfull`) | SUCCESS | 1 | 0 | 0 | 0 | `/home/ubuntu/stage2/artifacts/fanout-monitoring.log` |
| turbine-stream-service (`-Pfull`) | SUCCESS | 1 | 0 | 0 | 0 | `/home/ubuntu/stage2/artifacts/fanout-turbine-stream-service.log` |

The exact per-unit commands were:

```bash
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -pl <unit> verify
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -pl monitoring -Pfull verify
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -pl turbine-stream-service -Pfull verify
```

The whole-reactor commands and final totals were:

```bash
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -fae verify
```

`59 tests, 0 failures, 0 errors, 0 skipped`; artifact:
`/home/ubuntu/stage2/artifacts/t0-after-default-2.log`.

```bash
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -fae -Pfull verify
```

`61 tests, 0 failures, 0 errors, 0 skipped`; artifact:
`/home/ubuntu/stage2/artifacts/t0-after-full-2.log`.

The regenerated tables are:

- `/home/ubuntu/stage2/artifacts/t0-after-permodule-default.txt`
- `/home/ubuntu/stage2/artifacts/t0-after-perclass-default.txt`
- `/home/ubuntu/stage2/artifacts/t0-after-permodule-full.txt`
- `/home/ubuntu/stage2/artifacts/t0-after-perclass-full.txt`

T1 reached `T1 core stack is ready`; the smoke artifact ended with
`Smoke test passed` at
`/home/ubuntu/stage2/artifacts/t1-after-smoke.log`. Before the D-15 pin the
post-bump golden master was byte-identical except for the Jackson date-offset
line (`+0000` → `+00:00`):
`/home/ubuntu/stage2/artifacts/gm-before-vs-after.diff`. After the pin the
capture at `/home/ubuntu/stage2/artifacts/gm-after-r2.txt` has **zero**
adjudicated differences against both the pre-bump capture and Stage 1's
recorded oracle:
`/home/ubuntu/stage2/artifacts/gm-before-vs-after-r2.daynorm.diff` and
`/home/ubuntu/stage2/artifacts/gm-stage1-vs-after-r2.daynorm.diff` are both
empty. Those two comparisons additionally normalize the `DataPointId` day,
which is the capture day and the only field left volatile by the Stage 1
capture filter; the day is rewritten while the time and offset text stay under
comparison, so the `+0000` form the pin exists to hold remains proven by the
raw capture and by raw `lastSeen=2026-09-11T03:14:24.546+0000` in
`/home/ubuntu/stage2/artifacts/lastseen-r2.txt`. Because a
`spring.jackson.date-format` pattern also governs deserialization, a
server-rendered date was echoed back verbatim through
`PUT /notifications/recipients/current` and accepted with HTTP 200
(`/home/ubuntu/stage2/artifacts/date-roundtrip-r2.txt`). T0 after the pin is
unchanged at 59 / 61:
`/home/ubuntu/stage2/artifacts/t0-after-r2-default.log`,
`/home/ubuntu/stage2/artifacts/t0-after-r2-full.log`.

## 6. Playbook pointer and remaining gaps

The Stage 2 delegated procedure is
`docs/modernization/PLAYBOOK.md` §0–§7, headed
`# Stage 2 playbook — Boot 2.3.12 / Hoxton.SR12 (D-01, D-09 driver-coupled half, D-16, D-23)`.

Remaining gaps after Stage 2 are:

1. D-23 evidence (c), the scheduled application execution path, was not
   exercised because both daily cron windows had elapsed before
   notification-service started.
2. Whether the pinned Jackson date format should survive the Boot 3 stage, or
   the library default be adopted there with a deliberate oracle change.
3. The validation-starter transitivity hazard described in §3.
4. Browser-test evidence, which lives in the PR rather than this repository
   note.

---

# Uplift notes — Stage 3 `netflix-oauth`

Stages 1 and 2 above are unchanged. This section records the completed Stage 3
uplift on `stage-3-netflix-oauth`: the Netflix edge (Zuul, Ribbon, Hystrix,
Turbine, the dashboard) and the legacy OAuth2 stack leave the graph, with Java
8, Boot `2.3.12.RELEASE` and Spring Cloud `Hoxton.SR12` deliberately frozen.
This is the first stage that changes request-path behaviour rather than
versions, so most entries below are adjudications, not bumps.

## 1. Delta → fix mapping, and how each fix was produced

No OpenRewrite or other automated recipe was run; `PREFLIGHT.md` Check 2 still
holds. Every change is hand-written and reviewed.

| Delta | Fix | Produced by |
| --- | --- | --- |
| D-07 Hystrix metrics tier | `monitoring/` and `turbine-stream-service/` deleted, `spring-cloud-netflix-hystrix-stream` dropped from the three publishers, both Compose services removed | S3-A (`4d6680c`) |
| D-05 Hystrix → Resilience4j | `account-service` moves to `spring-cloud-starter-circuitbreaker-resilience4j`, `@EnableCircuitBreaker` retired, `shared/account-service.yml` moves from `feign.hystrix.enabled` to `feign.circuitbreaker.enabled` + `resilience4j.*` | S3-A (`02e2c80`, `0cb2049`) |
| D-03 Zuul → Spring Cloud Gateway | `gateway` swaps the Zuul starter for `spring-cloud-starter-gateway`, `zuul.routes.*` becomes `spring.cloud.gateway.routes[*]` predicates and filters | S3-B (`b33134f`) |
| D-06 Ribbon → Spring Cloud LoadBalancer | `lb://` route URIs plus `spring.cloud.gateway.httpclient` timeouts replacing `ribbon.*` | S3-B (`b33134f`) |
| D-04 opaque tokens → RS256 JWT | `auth-service` signs RS256 and publishes `/uaa/.well-known/jwks.json`; the three resource servers use `NimbusJwtDecoder` + `oauth2ResourceServer().jwt()`; `CustomUserInfoTokenServices` deleted in all three | S3-C (`2c5c306`) |
| D-18 internal JDK test principal | `com.sun.security.auth.UserPrincipal` replaced with a supported principal in the controller tests | S3-C (`2c5c306`) |
| Parent shared cuts | root reactor and `-Pfull` removal, dead `hystrix.*` blocks, `security.jwt.jwk-set-uri` for both profiles, `monitoring.yml`/`turbine-stream-service.yml` removal, CI step removal | this session (`d70a432`, `685eb55`) |

Two implementation details are load-bearing and would be silently lost in a
re-derivation:

1. **The Feign circuit-breaker builder adapter** (`0cb2049`). With
   `feign.hystrix.enabled` gone, Sleuth `2.2.8.RELEASE` contributes a plain
   `Feign.Builder`, which suppresses Spring Cloud's circuit-breaker builder;
   `FeignCircuitBreakerTargeter` then sees a plain builder and never wraps the
   client. The application starts, every call succeeds, and the fallback is
   simply never invoked — a silent loss of the one piece of resilience
   behaviour D-05 exists to preserve.
   `account-service` therefore declares `FeignCircuitBreakerBuilderConfiguration`
   as `@EnableFeignClients(defaultConfiguration = …)`. **Remove it when Sleuth
   is replaced by Micrometer Tracing** (Stage 4/5); it is a workaround for a
   Sleuth interaction, not a design choice.
2. **The `sub` claim** (`2c5c306`). `JwtAccessTokenConverter` emits `user_name`,
   while `JwtAuthenticationToken` derives the principal name from `sub`. Without
   a `TokenEnhancer` setting `sub`, every `principal.getName()` in
   `/accounts/current`, `/statistics/current` and
   `/notifications/recipients/current` resolves to `null` — again a silent
   behaviour change that no test would catch. Relatedly, `#oauth2.hasScope(...)`
   does not resolve under a `JwtAuthenticationToken`; `hasAuthority('SCOPE_server')`
   is the preserving equivalent, and the scope gate was re-proved at runtime.

## 2. Dual-run diff / target-only statement

Target-only, per `BASELINE.md` §4 of the Stage 3 section: Java 8 on both sides.
The proof that carries the stage is before/after at one toolchain, extended
with the route and security matrices Stage 3 needs.

## 3. Adjudicated behaviour differences

Per `BRIEF.md` §5 every difference is intended, regression, or unexplained.
There are no regressions and no unexplained differences.

| # | Difference | Verdict |
| --- | --- | --- |
| 1 | Access tokens change from opaque UUIDs to RS256 JWTs; `/uaa/.well-known/jwks.json` is a new endpoint | **Intended, and a new capability.** Ray's explicit decision, recorded in §5 below. Register: `05-…:306-307`, `:375-379` |
| 2 | The `-Pfull` profile and the 61-test full-profile total no longer exist; both profiles now report 59 | **Intended.** The profile existed only to add the two modules D-07 deletes, and each carried exactly one Boot context test. No test was weakened, renamed or removed: 59 = 59 across all seven surviving modules, per-class counts unchanged |
| 3 | The Hystrix stream, Turbine aggregation and dashboard proxy are gone | **Intended, and an accepted loss of working behaviour** — see §4 |
| 4 | Scope-gated endpoints reject with 403 via `SCOPE_server` authority instead of `#oauth2.hasScope` | **Intended.** Same status code on the same endpoints; the SpEL helper does not exist under JWT authentication |
| 5 | `GET /statistics/demo` without a token returns 401 | **Not a difference.** The baseline `ResourceServerConfigurerAdapter` in `statistics-service` overrode no `configure(HttpSecurity)`, so every path was already authenticated; the T1 oracle calls it with a token and still gets `[]` |
| 6 | `GET /ACCOUNT-SERVICE/accounts/current` returns 404 | **Not a difference.** Discovery-locator auto-routes were off under Zuul and are explicitly off under Gateway |
| 7 | `/favicon.ico` 404 | **Pre-existing.** No favicon exists in the gateway's static resources on either side |

## 4. D-07 as an accepted loss, argued rather than assumed

`05-dependency-and-eol-register.md:562` records the Hystrix dashboard as
returning 404 in T1, which reads as dead code. The Stage 1 T3 run disproves
that: under Compose the tier was fully live (`BASELINE.md` §5 of the Stage 3
section). D-07 therefore deletes **working observability behaviour**, and the
justification is not "it was already broken" but:

- `spring-cloud-netflix-hystrix-stream`, `-hystrix-dashboard` and Turbine are
  not built for Spring Cloud 2020.0+ (`05-…:300-308`), so the tier cannot cross
  the Stage 4 wall in any form;
- it is a metrics/observability tier, not a functional path: no user-visible
  feature and no persisted data depends on it, which is why its removal leaves
  the T1 wire text byte-identical;
- the replacement is a later, separate decision (Micrometer/Actuator plus a
  real metrics backend), and pretending Resilience4j's own metrics are an
  equivalent replacement would overstate what this stage delivers.

Also carried forward from the Stage 1 T3 run: `docker-compose.yml` published
`8989:8989` for a container listening on `8080`. That defect disappears with the
service rather than being fixed.

## 5. Ray's Stage 3 decisions, recorded

| Gate | Decision | Consequence in this stage |
| --- | --- | --- |
| D-04 token strategy | **Move to JWTs**, accepted as a new capability with its own decision record | `BRIEF.md` §2 excluded JWT. This overrides that line; the override is recorded here rather than applied silently. Signing is RS256 with a key from `security.jwt.private-key` |
| D-24 `statistics-service` Feign fallback | **Leave inert** | `ExchangeRatesClientFallback` stays unreferenced; `feign.circuitbreaker.enabled` is **not** set for `statistics-service`, so no fallback behaviour is introduced there |
| D-17 Jackson 1.x `@JsonIgnoreProperties` | **Delete it** | Deliberately *not* in this stage; it lands as a follow-up once the Codehaus artifacts are confirmed off the resolved graph, which is the ordering the catalog requires |

## 6. Deferred modernization, explicitly not done

1. **D-13 `WebSecurityConfigurerAdapter` → `SecurityFilterChain`.** Boot
   `2.3.12` pins Spring Security `5.3.9`; `SecurityFilterChain` beans need
   `5.4+`. The adapter is deprecated in `5.7` and removed in `6.0`, so this is
   forced work — but it is Stage 4 work, and bumping Spring Security here to
   make it fit would violate the stage ladder. The resource servers therefore
   still extend `WebSecurityConfigurerAdapter`, now with the lambda DSL.
2. **Spring Authorization Server.** Requires Spring Security `5.5.2+`; same
   constraint. `auth-service` keeps `spring-security-oauth2` for the
   authorization-server role only, and now issues JWTs from it. The full swap is
   Stage 4/5.
3. **Ribbon inside the services.** D-06 removed Ribbon from the *edge*.
   `account-service` and `notification-service` still resolve Feign clients
   through Ribbon (visible as `DynamicServerListLoadBalancer` in their logs),
   because `spring-cloud-starter-openfeign` on Hoxton defaults to it. Moving
   service-to-service calls onto Spring Cloud LoadBalancer belongs with the
   Spring Cloud 2021.0 bump.
4. **The Feign builder adapter** in §1 must be deleted with Sleuth.
5. **A persistent signing key.** Without `SECURITY_JWT_PRIVATE_KEY`,
   `auth-service` generates an ephemeral RSA pair per boot and logs a warning,
   so every restart invalidates outstanding tokens and multiple instances cannot
   validate each other's tokens. Acceptable for the demo stack and for CI; a
   real deployment needs a provisioned key. No key material is committed.

## 7. Per-unit results

T0 after integration, at `685eb55`, full log `/home/ubuntu/stage3/t0-after.log`:

| Module | `mvn -B -fae verify` | Tests | Failures | Errors | Skipped |
| --- | --- | ---: | ---: | ---: | ---: |
| piggymetrics (pom) | SUCCESS | — | — | — | — |
| config | SUCCESS | 0 | 0 | 0 | 0 |
| registry | SUCCESS | 0 | 0 | 0 | 0 |
| gateway | SUCCESS | 2 | 0 | 0 | 0 |
| auth-service | SUCCESS | 9 | 0 | 0 | 0 |
| account-service | SUCCESS | 14 | 0 | 0 | 0 |
| statistics-service | SUCCESS | 16 | 0 | 0 | 0 |
| notification-service | SUCCESS | 18 | 0 | 0 | 0 |
| **Total** | **BUILD SUCCESS** | **59** | **0** | **0** | **0** |

Per-class counts are unchanged from `BASELINE.md` §2, including
`StatisticsServiceClientFallbackTest=1`, which now exercises the Resilience4j
fallback rather than the Hystrix one.

T1 after integration: `/home/ubuntu/stage3/t1-after.txt` compared as text
against the Stage 1 oracle `/home/ubuntu/stage1/t1-after.txt`. The only
difference is the ambient date in the rates stub (`2026-09-10` → `2026-09-11`);
with that normalized the two files are identical, so `0.0330`, `0.6800`,
`"USD":1`, `"JPY":147.85`, `2.2341` and the `+0000` date form all survive the
JWT, Gateway and Resilience4j rewrites.

The D-05 fallback was proved by killing `statistics-service` and repeating the
account update: three PUTs returned 200 in under 20 ms each, the note persisted,
and `StatisticsServiceClientFallback` logged once per call
(`/home/ubuntu/stage3/fallback-proof.txt`). Without the builder adapter of §1
those calls would have propagated a 500 instead.

Route and security matrix, `/home/ubuntu/stage3/route-security-after.txt`:

```text
GET /            (static UI via gateway)       200
GET /accounts/current                          200
GET /statistics/current                        200
GET /notifications/recipients/current          200
GET /uaa/users/current                         200
GET /rates/latest?base=USD (StripPrefix=1)     200
GET /ACCOUNT-SERVICE/accounts/current          404
GET /accounts/current  no token                401
GET /accounts/current  garbage token           401
GET /statistics/current no token               401
GET /notifications/recipients/current none     401
GET /accounts/demo     permitAll               200
GET /statistics/demo   permitAll               401   (row 5 of §3)
POST /accounts/        permitAll (dup user)     400
PUT  /statistics/{user} with ui-scope token     403
```

The issued token was 578 characters with two dots — a three-part JWT, not the
36-character opaque UUID of the baseline. `smoke.sh` printed
`Smoke test passed` (`/home/ubuntu/stage3/smoke-after.log`).

## 8. The warm-up window, and why the first capture was empty

The first post-integration T1 capture returned `[]` for `/statistics/current`.
The cause is in `/home/ubuntu/stage3/account-debug2.log`:

```text
TimeLimiter 'StatisticsServiceClient#updateStatistics(String,Account)' recorded an error:
'java.lang.RuntimeException: com.netflix.client.ClientException:
 Load balancer does not have available server for client: statistics-service'
```

For the first ~30-60s after boot, account-service's Ribbon server list for
`statistics-service` is still empty, the call fails, and the fallback fires —
which is the fallback working, and is the same window that produced
`/home/ubuntu/stage2/artifacts/gm-before-initial-empty-stats.txt` at the Stage 2
baseline with Hystrix. It is a harness artifact, not a Stage 3 difference.
`/home/ubuntu/stage3/warm-probe.sh` now polls the account → statistics chain
until it is live so captures land outside that window; it warmed on the first
attempt for the recorded run.

## 9. Playbook pointer and remaining gaps

The Stage 3 delegated procedure is `docs/modernization/PLAYBOOK.md`, section
`# Stage 3 playbook — Netflix and OAuth2 retirement (D-03…D-07, D-18, D-24)`.

Remaining gaps after Stage 3:

1. The five deferrals in §6, all of which land in Stage 4/5.
2. D-17, held back deliberately per §5.
3. No test covers the Feign circuit-breaker wiring itself: `T0` passes whether
   or not the builder adapter is present, which is precisely how the silent
   fallback loss could have shipped. The runtime fallback probe is the only
   guard, and it lives in the PR rather than the suite.
4. Browser-test evidence lives in the PR, as in earlier stages. The integrated
   golden path was driven end to end at `685eb55`: signup, two-stage login,
   modal edits (Salary 1000 USD/month, Tokyo 14785 JPY/month), savings cycling
   USD 100 → RUB 9250 → EUR 92 → USD 100, finite charts, persistence across a
   full reload and re-login, and the read-only demo account. No console errors.
