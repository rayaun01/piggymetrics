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
| D-15 | Boot 2.3.12 manages Jackson `2.11.4` where Boot 2.0.3 managed `2.9.6`. The adjudicated intended wire difference is Jackson's `java.util.Date` offset form: `+0000` → `+00:00` in `/statistics/current` `id.date`, and the same unnormalized change was confirmed for `Account.lastSeen` on `/accounts/current` and `/accounts/demo`. No application code, annotation, or `spring.jackson.*` setting changed. This is the D-15 risk recorded at `docs/modernization/DELTA-CATALOG.md:316-334` and `docs/as-is/05-dependency-and-eol-register.md:513`. | REST wire contract; account and statistics consumers. `/home/ubuntu/stage2/artifacts/gm-before-vs-after.diff` | hand |
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
| Restoring the `+0000` Jackson date form | Not done. It is restorable with one `spring.jackson.date-format` setting, but this stage records the adjudicated intended D-15 drift rather than suppressing it. The decision remains with Ray because D-15's recorded oracle explicitly lists `+0000`. |

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
`/home/ubuntu/stage2/artifacts/t1-after-smoke.log`. The post-bump golden
master was byte-identical except for the adjudicated Jackson date-offset line
(`+0000` → `+00:00`):
`/home/ubuntu/stage2/artifacts/gm-before-vs-after.diff`.

## 6. Playbook pointer and remaining gaps

The Stage 2 delegated procedure is
`docs/modernization/PLAYBOOK.md` §0–§7, headed
`# Stage 2 playbook — Boot 2.3.12 / Hoxton.SR12 (D-01, D-09 driver-coupled half, D-16, D-23)`.

Remaining gaps after Stage 2 are:

1. D-23 evidence (c), the scheduled application execution path, was not
   exercised because both daily cron windows had elapsed before
   notification-service started.
2. Ray's decision on whether the D-15 `+00:00` date-offset drift should be
   restored to the documented `+0000` oracle.
3. The validation-starter transitivity hazard described in §3.
4. Browser-test evidence, which lives in the PR rather than this repository
   note.
