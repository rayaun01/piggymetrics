# Modernization brief — PiggyMetrics Java uplift

Skill: `.agents/skills/modernization-brief/SKILL.md`.

| Input | Value |
| --- | --- |
| `SYSTEM` | `piggymetrics` (nine Java/Maven units) |
| `SOURCE_VERSION` | Java 8 / Spring Boot `2.0.3.RELEASE` / Spring Cloud `Finchley.RELEASE` (`pom.xml:11-22`) |
| `TARGET_VERSION` | Java 17 / Spring Boot `3.0.13` / Spring Cloud `2022.0.x` |
| `BASELINE_REVISION` | tag `stage-0-baseline` (`6d42cc7`) |
| `AS_IS_DIR` | `docs/as-is` (01, 03, 04, 05) |
| `DELTA_CATALOG_PATH` | `docs/modernization/DELTA-CATALOG.md` |
| `PREFLIGHT_PATH` | `docs/modernization/PREFLIGHT.md` |

## Gate record

| Gate | State | Evidence |
| --- | --- | --- |
| 1. EOSL documentation discovery gate | **passed with one accepted gap** | `docs/as-is/01-system-context-and-topology.md` (337 lines), `03-api-contract-inventory.md` (334), `04-data-model-and-ownership.md` (595), `05-dependency-and-eol-register.md` (633) all exist and are cited throughout this brief and the catalog. Each carries its own "Open items for runtime verification" list and none of those items is a completeness blocker. **Accepted gap:** there is no `docs/as-is/02-*` document; the discovery set is bound by name to exactly these four artifacts (`.agents/skills/modernization-assess-map/SKILL.md:15-25`) and no artifact, skill or workflow in the repository cites an `02`. Resolved as a numbering artefact — no `02` document is authored (`docs/modernization/PREFLIGHT.md`, Check 6) |
| 2. Uplift gate | **passed** | `docs/modernization/DELTA-CATALOG.md` exists, derived from the exact pins Boot `2.0.3.RELEASE` / Spring Cloud `Finchley.RELEASE` / Java `1.8` (`pom.xml:11-22`) against the per-stage target pins; 25 confirmed deltas, 0 tool-derived (OpenRewrite did not run) |
| 3. Approval gate | **OPEN — blocks all migration** | see [Approval block](#8-approval-block). No stage may execute until this is recorded |
| 4. Pilot migration gate | **defined, not yet satisfied** | pilot is `account-service`; requires its diff, catalog feedback, `docs/modernization/PLAYBOOK.md`, and its target-toolchain proof in a PR before any fan-out |

## 1. Objective

Preserve behaviour while moving the pinned Java/Maven system from **Java 8 /
Spring Boot 2.0.3.RELEASE / Spring Cloud Finchley.RELEASE** to **Java 17 /
Spring Boot 3.0.13 / Spring Cloud 2022.0.x**, in six staged cuts, each with a
branch, an immutable tag, a reviewable PR, and recorded proof.

Behaviour preservation is defined by two oracles and nothing else: the T0 suite
(59 tests in the default reactor, 61 with `-Pfull`) and the recorded T1 wire
capture (§5). Where a delta cannot be applied without changing one of those,
the change is not made silently — it is raised as an explicit decision (D-15,
D-16, D-17, D-24).

The .NET services are a frozen, out-of-scope boundary
(`docs/OUT-OF-SCOPE-DOTNET.md:1-13`); no Java change may depend on them, and
excluding them removes the LATAM currency set from scope — JPY is the complete
currency story here.

## 2. Target architecture

Only the changes the catalog forces. No redesign:

| Concern | Today | Target | Forced by |
| --- | --- | --- | --- |
| Edge routing | Netflix Zuul 1.x, `@EnableZuulProxy`, `zuul.routes.*` | Spring Cloud Gateway, `spring.cloud.gateway.routes[*]` | D-03 — the artifact is not built in 2020.0+ |
| Service discovery | Eureka client/server `2.0.0.RELEASE` | **same components**, version-bumped per train | D-dropped — Eureka is the one Netflix module still shipped in 4.x |
| Resilience | Hystrix + `@EnableCircuitBreaker` | Resilience4j via `spring-cloud-starter-circuitbreaker-resilience4j` | D-05 |
| Load balancing | Ribbon `2.2.5` (transitive) + `ribbon.*` | Spring Cloud LoadBalancer | D-06 |
| Circuit-breaker metrics | Hystrix stream + `monitoring` dashboard + `turbine-stream-service` | **deleted** — no Micrometer replacement is built, because the baseline serves no stream to replace | D-07 |
| Authn/authz | `spring-cloud-starter-oauth2` + `spring-security-oauth2` (opaque tokens, `CustomUserInfoTokenServices`) | Spring Authorization Server + native Spring Security resource server, still opaque tokens | D-04; JWTs are explicitly **not** adopted |
| Tracing | Spring Cloud Sleuth | Micrometer Tracing (`micrometer-tracing-bridge-brave`) | D-14 |
| Persistence | MongoDB driver `3.6.4`, Spring Data Mongo | driver `4.8.2`, unchanged `BigDecimal` representation | D-16 |
| EE APIs | `javax.validation`, `javax.mail` | `jakarta.*` | D-11, D-12 |
| Test stack | JUnit 4.12 / Mockito 2.15.0 / JaCoCo 0.7.6 / flapdoodle 1.50.3 | JUnit 5, Boot-managed Mockito, JaCoCo ≥ 0.8.11, flapdoodle 3.x/4.x or Testcontainers | D-08, D-09, D-10 |
| Runtime image | `eclipse-temurin:8-jre` | `eclipse-temurin:17-jre` | D-20 |
| Reactor shape | 7 default + 2 under `full` | unchanged, minus the two deleted units | `pom.xml:36-54`, D-07 |

Explicitly **not** in the target architecture: JWT tokens, a new observability
platform, the `mongo:3` T3 tier, any package or module reorganization, and any
"cleanup" of the inert Jackson 1.x annotation before stage 3 (D-17).

## 3. Phased sequence — the forced stage ladder

One stage per branch, per tag, per reviewable PR. Stages are not combined. Any
proposed reordering must cite the register fact that forces it.

| Stage | What moves | Forcing fact | Branch / tag | Proof |
| ---: | --- | --- | --- | --- |
| 0 | Stage-0 harness and frozen baseline | The baseline must be recoverable and cited | `stage-0-baseline` (**done**, `6d42cc7`) | T0/T1 records and revision |
| 1 | JaCoCo, flapdoodle, and test-runner infrastructure (D-08, D-09, D-10, D-19) | JaCoCo `0.7.6` fails in the agent before any test on newer class files; flapdoodle `1.50.3` is tied to the Mongo 3.x driver; JUnit 4 / Mockito `2.15.0` blocks later runtime work (`05-…:33-40`) | `stage-1-test-infrastructure` | T0 pass/fail table |
| 2 | Boot `2.3.12` + Spring Cloud `Hoxton.SR12` (D-01, D-16, D-23, and flapdoodle's driver-coupled half) | The last generation where the old Netflix modules and the next supported Boot generation coexist (`05-…:365-368`) | `stage-2-boot-23-hoxton` | T0 plus T1 |
| 3 | Zuul, Hystrix, Ribbon, Turbine, dashboard, OAuth2 retirement/replacement (D-03…D-07, D-18, D-24) | Those Netflix artifacts are not built in 2020.0+; `spring-security-oauth2-autoconfigure` has no GA beyond `2.6.8`, so the rework cannot wait for Boot 2.7 (`05-…:300-308`, `05-…:375-379`) | `stage-3-netflix-oauth` | T0/T1 and route/security proof |
| 4 | Boot `2.7`, Spring Cloud `2021.0`, JDK 17 (D-02, D-13, D-20, D-21, D-22) | Boot 2.3 supports only through Java 15; Boot 2.7 is the compatible bridge to Java 17 (`05-…:350-356`) | `stage-4-boot-27-jdk17` | T0/T1 dual-run |
| 5 | Boot 3, Spring Cloud `2022.0+`, `javax` → `jakarta` (D-11, D-12, D-14) | Boot 3 requires Java 17 and removes the `javax` APIs (`05-…:372-374`) | `stage-5-boot3-jakarta` | full T0–T3 and golden master |

Register-label mapping: stages 1 and 2 here are the register's `S1`, stage 3 is
`S2`, stage 4 is `S3`, and stage 5 is `S4` (`05-…:288-294`). Test
infrastructure is split out of `S1` into its own stage because JaCoCo and
flapdoodle break the build **before any test runs**, which makes them a
prerequisite rather than work that rides along (`05-…:42-43`).

Ownership: the orchestrating session owns coordinated cuts and the shared files
`config/src/main/resources/shared/*.yml` and `pom.xml`. **Delegated units never
edit those files**; a fan-out agent that needs a shared-file change reports the
need instead of making it.

## 4. Behaviour walkthroughs

**T1 wire contract (the primary golden master).** Recorded on a live T1 stack
at commit `d91f384` (`docs/as-is/03-api-contract-inventory.md:225,231`,
`04-data-model-and-ownership.md:395-428`):

```text
GET /accounts/current
{"name":"wire1788889784","lastSeen":"2026-09-08T17:49:44.928+0000",
 "incomes":[{"title":"Salary","amount":10000,"currency":"JPY","period":"MONTH","icon":"wallet"}],
 "expenses":[{"title":"Tokyo","amount":147.85,"currency":"JPY","period":"MONTH","icon":"travel"}],
 "saving":{"amount":100,"currency":"JPY","interest":3.32,"deposit":true,"capitalization":false},
 "note":"wire"}

GET /statistics/current
[{"id":{"account":"wire1788889784","date":"2026-09-08T00:00:00.000+0000"},
  "incomes":[{"title":"Salary","amount":2.2341}],
  "expenses":[{"title":"Tokyo","amount":0.0330}],
  "statistics":{"EXPENSES_AMOUNT":0.0330,"INCOMES_AMOUNT":2.2341,"SAVING_AMOUNT":0.6800},
  "rates":{"EUR":0.92,"JPY":147.85,"RUB":92.5,"USD":1}}]
```

Four properties of that text are the contract, not incidental formatting:

1. **Scale survives onto the wire.** `0.0330` and `0.6800` keep their trailing
   zeros — scale-4 `HALF_UP` arithmetic serialized as a `BigDecimal`, compared
   as **text** (`04-…:408,428`).
2. **Mixed scales in one map.** `"USD":1` comes from `BigDecimal.ONE` (no
   scale) next to `"JPY":147.85` (`03-…:208-209,240`).
3. **Jackson's `+0000` date form** for `java.util.Date` and for the `DataPoint`
   id date (`03-…:231`, `04-…:401`).
4. **Derivation is reproducible.** With the deterministic stub's
   `"JPY": 147.85` (`scripts/demo/rates-stub.py:18-25`) and the `MONTH` base
   ratio `30.4368`
   (`statistics-service/src/main/java/com/piggymetrics/statistics/domain/TimePeriod.java:7`): `10000 × (1/147.85)` at 4 dp,
   then `÷ 30.4368` at 4 dp `HALF_UP` gives `2.2341`; the 147.85 JPY expense
   gives `0.0330` (`04-…:418-421`).

**T0 tests.** Executed on this box at `4bbdc10` with Temurin 8:
`mvn -B -fae verify` → `BUILD SUCCESS`, 59 tests, 0 failures
(`docs/modernization/PREFLIGHT.md`, Check 4, per-module table). The `-Pfull`
variant adds `monitoring` and `turbine-stream-service` for 61.

**Known currency/rates/data boundaries.**

- The external rates provider is replaced by a deterministic local stub in T1
  and T2; the real provider is never called in any measured tier.
- `accounts.expenses.amount` already holds **two BSON types** in the same path
  (Spring Data's `BigDecimal` representation versus seeded doubles —
  `04-…:380`). A driver upgrade must not change the representation (D-16).
- The `DataPoint` id date depends on the container's default time zone, and the
  Dockerfiles set no `TZ` (`05-…:478`); a base-image change that alters the
  default zone shifts historical keys by a day (D-22).
- The Hystrix metrics path is already inert in the measured tiers: no
  `management.endpoints.web.exposure.include` exists, so `/hystrix.stream` is
  404 everywhere, and no broker runs in T1/T2 (`05-…:255-262`, `05-…:562`).
  Nothing there needs preserving.

## 5. Behaviour contract

- **Baseline file:** `docs/modernization/BASELINE.md`, written by the executing
  session **before** any source edit, containing the T0 per-module pass/fail
  table, the recorded T1 wire text with the exact serialized values above
  (`0.0330`, `0.6800`, `"USD":1`, `"JPY":147.85`, `+0000`), the captured
  `BASELINE_REVISION`, and whether the proof is dual-run or target-only
  (`.agents/skills/modernization-uplift/SKILL.md:119-128`).
- **Golden master:** the T1 wire text, compared **as text**, not as parsed
  numbers — parsing destroys the scale that is the contract (`04-…:428`).
- **Baseline revision:** tag `stage-0-baseline` (`6d42cc7`), inspected by
  checkout at that revision, never by copying a directory.
- **Triage rule for every result delta:** compare results, not exit codes.
  Every difference is classified as (a) intended, with the catalog delta and
  the register fact that forces it, (b) a regression, which blocks the stage,
  or (c) unexplained, which also blocks the stage. **An old failing test that
  starts passing is a behaviour change to adjudicate, not a win.** No stage
  merges with an unexplained delta.

## 6. Validation strategy

| Level | Recipe (`docs/RUNBOOK.md`) | When |
| --- | --- | --- |
| T0 | `JAVA_HOME=<jdk> mvn -B -fae test` (`:13`), CI form `mvn -B -fae verify` and `-Pfull verify` (`.github/workflows/build.yml:27,31`) | every stage |
| T1 | `scripts/demo/start-local.sh && scripts/demo/smoke.sh` (`:34-35`) | stages 2–5, plus the golden-master capture |
| T2 | `mvn -B package` then `docker compose -f docker-compose.core.yml up` (`:85-97`) | stage 5 |
| T3 | `mvn -B -Pfull package` then the two-file Compose build/up (`:113-120`) | stage 5; frozen tier, `mongo:3` untouched |
| Per-unit Maven proof | `JAVA_HOME=<jdk> mvn -B -pl <unit> verify`, and the `-Pfull` form for `monitoring` / `turbine-stream-service` | every migrated unit |

**Dual-run availability: yes, through stage 4.** Both toolchains run on this
box — Temurin `1.8.0_504` at `/usr/lib/jvm/temurin-8-jdk-amd64` and OpenJDK
`17.0.19` at `/usr/lib/jvm/java-17-openjdk-amd64` (`docs/modernization/PREFLIGHT.md`,
Check 3) — so the same T0 suite and the same T1 capture can be executed under
both and diffed. Stages 1–3 remain Java 8 on both sides; stage 4 is the first
true dual-JDK comparison; at stage 5 the source toolchain can no longer build
the target tree, so proof there is **target-only against the recorded stage-4
capture**, and `BASELINE.md` must say so with its exact reason.

**Honest degradations to record, not to hide:**

- OpenRewrite did not run (mirror throughput), so no automated recipe may be
  claimed as proof; mechanical deltas are applied by hand unless a later
  session proves `actually-ran` (`docs/modernization/PREFLIGHT.md`, Check 2).
- No production traffic or metrics exist; the oracle is the T0 suite plus the
  single recorded T1 capture. Behaviour outside that corpus is unproven.
- Transitive `[BOM]` versions are unverified against a resolved tree on this
  box; re-run `mvn -B -Pfull dependency:tree` after each stage.

## 7. Open questions

Only cited gaps. None is filled by preference here; each names who must answer
and when it blocks.

1. **Does `statistics-service` gain the Feign fallback?** `feign.hystrix.enabled`
   is set only for `account-service`, so `ExchangeRatesClientFallback` is never
   wired (`05-…:164-171`, D-24). Enabling it during the migration is a
   behaviour change. Blocks stage 3.
2. **`@JsonIgnoreProperties`: delete or port?** Deleting preserves behaviour;
   porting silently changes the four controller tests
   (`05-…:194-210`, D-17). Blocks nothing before stage 3; must be decided
   before it is touched.
3. **flapdoodle 3.x/4.x or Testcontainers?** The register names both and
   prefers neither (`05-…:312`, D-09). Blocks stage 1.
4. **Does the server-side JavaScript `@Query` survive the driver/server move?**
   `RecipientRepository.java:16,20` is evaluated by the MongoDB server and
   support has changed across generations (`05-…:483`, D-23). Needs a runtime
   check; blocks stage 2.
5. **Is the actuator/health surface fixed or documented as-is?**
   `auth-service` declares no actuator at all and the other three sit behind a
   context path (`05-…:563`, open item 7). Not a blocker; needs a plan
   decision.
6. **Is the Hystrix tier "unexposed" or "never worked"?** Open item 8
   (`05-…:622-629`) only changes the wording of the deletion note, not the
   decision.
7. **Do `com.sun.*` APIs remain available on JDK 21?** Verified for 17 only
   (`05-…:597-601`, D-18). Blocks nothing before a JDK 21 stage, which is not
   in this ladder.
8. **Memory headroom under Boot 3 defaults** with `-Xmx200m` /
   `-XX:MaxMetaspaceSize=128m` (`05-…:609-610`, D-21). Measurable only once a
   unit reaches Boot 3; blocks the stage-5 T1 tier if it fails.

## 8. Approval block

**Gate 3 — Approval gate. State: OPEN. Migration is blocked.**

An absent or conditional approval blocks migration. No stage branch, tag, PR,
or source edit may be created until the decision below is recorded in this
file.

| Field | Value |
| --- | --- |
| Decision | ☐ Approved ☐ Approved with conditions ☐ Rejected — **not yet recorded** |
| Approver name | _to be recorded_ |
| Approver role | human approver (repository owner) |
| Date | _to be recorded_ |
| Scope approved | Stages 1–5 as written in §3, one stage per branch/tag/PR, with `account-service` as the pilot and the shared files owned by the orchestrating session |
| Conditions | _to be recorded_ |
| Artifacts reviewed | `docs/modernization/PREFLIGHT.md`, `docs/modernization/DELTA-CATALOG.md`, this brief, and `docs/as-is/01`, `03`, `04`, `05` |

What approval authorizes, and nothing more:

1. Stage 1 (`stage-1-test-infrastructure`) executes first and alone: JaCoCo
   ≥ 0.8.11, the flapdoodle decision from open question 3, JUnit 4/Mockito →
   JUnit 5 across auth/account/statistics/notification, and the D-19 pins.
   Proof is the T0 pass/fail table; it ends with an immutable tag and a
   reviewable PR.
2. Stage 2 (`stage-2-boot-23-hoxton`) begins only after stage 1 is accepted.
   Proof is T0 plus T1.
3. **Pilot migration gate:** before any fan-out, `account-service` is migrated
   in the calling session with its exact target-toolchain proof command, every
   surprise is added to `docs/modernization/DELTA-CATALOG.md`, and
   `docs/modernization/PLAYBOOK.md` is written for an engineer with no prior
   context. The pilot diff and the playbook are shown in a PR before delegation
   begins.
4. Stages are never combined. Any deviation from §3 cites the register fact
   that forces it.

Answers to the blocking open questions in §7 (items 1, 3, 4) may be recorded in
the Conditions row; until they are answered, the stages they block do not start.
