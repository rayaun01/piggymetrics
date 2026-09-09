# Modernization preflight — readiness record

Skill: `.agents/skills/modernization-preflight/SKILL.md`.

| Input | Value |
| --- | --- |
| `SYSTEM` | `piggymetrics` |
| `SOURCE_VERSION` | Java 8 / Spring Boot `2.0.3.RELEASE` / Spring Cloud `Finchley.RELEASE` (`pom.xml:11-22`) |
| `TARGET_VERSION` | Java 17 / Spring Boot `3.0.13` / Spring Cloud `2022.0.x` — reached through the forced stage ladder, not in one cut |
| `PREFLIGHT_PATH` | `docs/modernization/PREFLIGHT.md` |
| `RUNBOOK_PATH` | `docs/RUNBOOK.md` |
| `DOTNET_SCOPE_PATH` | `docs/OUT-OF-SCOPE-DOTNET.md` |

Recorded on 2026-09-09 at `4bbdc10` (`master`), with the frozen baseline tag
`stage-0-baseline` = `6d42cc7`.

## Check 0 — recorded answers, not an interview

| Readiness answer | Supporting artifact | State |
| --- | --- | --- |
| Scope boundary | `docs/OUT-OF-SCOPE-DOTNET.md:1-13` — the three .NET Core 2.1 services are excluded from the migration and from every run tier; their REST contracts are frozen | recorded |
| Build/test/run recipes and tiers | `docs/RUNBOOK.md:6-126` — T0 tests, T1 bare JVMs, T2 core Compose, T3 full Java Compose | recorded |
| Bespoke infrastructure | `docs/RUNBOOK.md:22-27` — a `central` mirror in `~/.m2/settings.xml` is required on networks that cannot reach Maven Central (HTTP 429); the blueprint writes one | recorded |
| Prior attempts and known gaps | `docs/as-is/01`, `03`, `04`, `05` and their "Open items for runtime verification" sections (`05-dependency-and-eol-register.md:567-633`) | recorded |
| Off-limits files or components | `docs/OUT-OF-SCOPE-DOTNET.md`; `mongo:3` in the frozen T3 tier is deliberately not touched (`05-…:316`); the brief adds the orchestrator-owned shared files | recorded |

No readiness answer was inferred. The `docs/as-is/02-*` numbering gap is
handled in Check 6.

## Check 1 — repository and stack

Nine Java/Maven reactor units, confirmed against the root POM:

- default seven-module reactor — `config`, `registry`, `gateway`,
  `auth-service`, `account-service`, `statistics-service`,
  `notification-service` (`pom.xml:36-44`);
- `full` profile adds `monitoring` and `turbine-stream-service`
  (`pom.xml:46-54`).

Three .NET Core 2.1 services (`compliance-service`,
`currency-exchange-service`, `fraud-detection-service`) exist in the tree as
the frozen out-of-scope boundary.

Present and confirmed: root POM (`pom.xml`), nine module POMs, 98 `.java`
sources outside `target/`, 26 test classes under `src/test`, nine Dockerfiles,
`docker-compose.yml` / `docker-compose.core.yml` / `docker-compose.dev.yml`,
and the T1 harness (`scripts/demo/start-local.sh`, `stop-local.sh`,
`smoke.sh`, `seed-local.sh`, `seed-local.js`, `rates-stub.py`).

## Check 2 — analysis and migration tools

| Tool | State here | Version | Purpose | Coverage lost |
| --- | --- | --- | --- | --- |
| `scc` / `cloc` | **absent** | — | quantitative inventory | LOC/language split not machine-counted; file and test-class counts in Check 1 come from `find`, which counts files, not lines |
| `lizard` | **absent** | — | complexity sampling | no cyclomatic-complexity sample; pilot selection rests on the register's qualitative representativeness argument instead |
| `glow` | **absent** | — | artifact rendering | cosmetic only |
| `delta` | **absent** | — | side-by-side diffs | reviewers use `git diff` / the PR view |
| OpenRewrite | **present, not runnable-here** | plugin `4.46.0` resolves from the mirror | `mvn rewrite:dryRun` migration analysis | **No OpenRewrite finding may enter the delta catalog.** `mvn -pl account-service org.openrewrite.maven:rewrite-maven-plugin:4.46.0:dryRun -Drewrite.activeRecipes=org.openrewrite.java.format.AutoFormat` was launched here and never reached the recipe run: the provisioned `aliyun-central` mirror serves the plugin's own dependency closure at roughly 1–2 kB/s, so the goal was still downloading `micrometer`/`reactor`/`netty` POMs when the 240 s budget expired (`/tmp/rewrite.log`, exit 124). Per the skill, a resolution/throughput failure means the tool did not run |

## Check 3 — toolchains

| Item | Result |
| --- | --- |
| `/usr/lib/jvm/temurin-8-jdk-amd64` | exists; `java -version` → `openjdk version "1.8.0_504"` (Temurin, build `25.504-b01`) |
| JDK 17 | `/usr/lib/jvm/java-17-openjdk-amd64`; `java -version` → `openjdk version "17.0.19" 2026-04-21` |
| `mvn -version` | Apache Maven `3.6.3`, `Maven home: /usr/share/maven`, Java `1.8.0_504` from `/usr/lib/jvm/temurin-8-jdk-amd64/jre` when `JAVA_HOME` is set to Temurin 8 |
| `~/.m2/settings.xml` `central` mirror | present — `aliyun-central` → `https://maven.aliyun.com/repository/central`, `mirrorOf: central` |
| Root enforcer | `maven-enforcer-plugin 3.4.1`, `requireJavaVersion [1.8,1.9)` bound to `validate` (`pom.xml:56-80`) — active and must be re-targeted, never removed, at the JDK stage |

Both JDKs run on this box, so the **dual-run strong-equivalence proof is
available** for every stage up to the JDK-17 cut.

## Check 4 — run tiers

| Tier | Recipe (`docs/RUNBOOK.md`) | Prerequisite state | Degradation |
| --- | --- | --- | --- |
| T0 | `JAVA_HOME=<jdk8> mvn -B -fae test` (`:11-14`); the blueprint's `-fae verify` form is the CI equivalent (`.github/workflows/build.yml:27,30`) | **actually ran** — see below | none |
| T1 | `scripts/demo/start-local.sh && scripts/demo/smoke.sh` (`:33-36`) | `mongod` and `mongo` present at `/usr/local/bin`; rates stub is `scripts/demo/rates-stub.py`, `python3` present | none; not started in this session |
| T2 | `mvn -B package` then `docker compose -f docker-compose.core.yml up` (`:82-98`) | `docker` present; `docker compose version` → `v5.4.0` | none; not started |
| T3 | `mvn -B -Pfull package` then the two-file Compose build/up (`:110-121`) | same as T2 | frozen tier, deliberately unchanged (`mongo:3`) |

T0 was executed as the smallest proof that the baseline is green before any
plan is written — `JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -fae verify`,
`BUILD SUCCESS`, total time 04:26, finished 2026-09-09T18:16:01Z:

| Module | Tests run | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| config | 0 | 0 | 0 | 0 |
| registry | 0 | 0 | 0 | 0 |
| gateway | 2 | 0 | 0 | 0 |
| auth-service | 9 | 0 | 0 | 0 |
| account-service | 14 | 0 | 0 | 0 |
| statistics-service | 16 | 0 | 0 | 0 |
| notification-service | 18 | 0 | 0 | 0 |
| **total (default reactor)** | **59** | **0** | **0** | **0** |

The `-Pfull` variant (61 tests including `monitoring` and
`turbine-stream-service`) was not run here; it is part of the Stage-0 baseline
record, not of this readiness check.

## Check 5 — optional context

| Context | State |
| --- | --- |
| Representative production traffic | **gap** — none available; the T1 smoke flow and the recorded wire captures in `docs/as-is/03`/`04` are the only request corpus |
| Deployment manifests | available for the demo tiers only (`docker-compose*.yml`); no production manifests, no Kubernetes descriptors |
| Runtime metrics | **gap** — only the T1 measurements in `docs/RUNBOOK.md:44-58` (launch ≈55.24 s, seven-JVM peak RSS ≈2,939.7 MiB); no production metrics |
| External integration contracts | the real rates provider is replaced by a deterministic local stub (`scripts/demo/rates-stub.py`); the .NET REST contracts are frozen and out of scope |

No production behaviour is inferred from source names.

## Check 6 — source completeness and scope boundary

- The Java scope is complete: every module in `pom.xml:36-54` has a POM, sources
  and (where applicable) tests in the tree; no module is referenced without
  source.
- The .NET directories remain frozen and are referenced only as the scope
  boundary (`docs/OUT-OF-SCOPE-DOTNET.md`).
- No generated or vendored dependency source is treated as application source;
  `target/` is untracked (`docs/RUNBOOK.md:88-89`).
- The requested system is the **full Java scope** (all nine reactor units), not
  an undocumented slice.

Gaps recorded here:

1. **`docs/as-is/02-*` does not exist — accepted, resolved.** The discovery set
   is bound by name to exactly four artifacts (01, 03, 04, 05) in
   `.agents/skills/modernization-assess-map/SKILL.md:15-24`, and no artifact,
   skill or workflow in the repository cites an `02` document. The numbering
   gap is therefore a naming artefact, not a missing deliverable, and no `02`
   document is authored. Recorded again in the brief's gate record.
2. **OpenRewrite did not run** (Check 2). Every delta in the catalog is
   hand-derived from the register and cited; no tool finding is claimed.
3. **`[BOM]`-only transitive versions** in `docs/as-is/05` §6 remain unverified
   against a resolved tree on this box; the `[RESOLVED]` figures in §1.2 come
   from an external JDK-8 run (`05-…:56-68`). Re-run
   `mvn -B -Pfull dependency:tree` after each stage.
4. **Runtime open items** in `docs/as-is/01`, `03`, `04`, `05` are carried
   forward unchanged; none blocks discovery, and the brief's open-questions
   section lists the ones that block a *stage*.

## Verdict

**Ready-with-gaps.**

Discovery and planning proceed. The gaps and their downstream consequences:

| Gap | Consequence |
| --- | --- |
| OpenRewrite not runnable at usable throughput | Mechanical deltas must be applied by hand or re-attempted with a faster mirror; the uplift skill forbids a recipe run whose `actually-ran` state is unproven |
| `scc`/`cloc`/`lizard` absent | No quantitative LOC or complexity index; pilot choice and effort statements rest on cited structure, not measurement |
| No production traffic or metrics | The behaviour oracle is the T0 suite plus the recorded T1 wire capture only; anything outside that corpus is unproven after a stage |
| Transitive `[BOM]` versions unresolved here | Post-stage classpath-divergence claims need a `dependency:tree` re-run before they can be called closed |
