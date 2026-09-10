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
| ~~T3 (Compose/AMQP, D-07)~~ | **No longer deferred.** Run at Ray's instruction at the Stage 1 head; the AMQP path is live and the result is recorded in `BASELINE.md` §5 | done here |
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

T3 (AMQP / D-07): run at `f6ab45d`, full result in `BASELINE.md` §5. The
Hystrix metrics path works end to end under Compose — publishers →
`springCloudHystrixStream` exchange → `turbine-stream-service` →
`monitoring`'s dashboard proxy, carrying named `HystrixCommand` frames for
`AuthServiceClient#createUser` and `StatisticsServiceClient#updateStatistics`.
D-07 therefore deletes **working** behaviour, not dead code; the deletion has
to be argued as an accepted loss. Unrelated pre-existing defect found while
measuring: `docker-compose.yml:179-180` publishes `8989:8989` while the
service listens on `8080` in-container, so the stream is host-unreachable
(fine on the Compose network). Not fixed here — Stage 1 changes no Compose
file.

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
