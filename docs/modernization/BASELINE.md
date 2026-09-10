# Stage 1 baseline — recorded before any source edit

This file is the `BASELINE_PATH` required by
`.agents/skills/modernization-uplift/SKILL.md:119-128`. It records the T0 and
T1 oracles as they were observed on this box, at one revision, before the
Stage 1 (`test-infrastructure`) edits.

## 1. Captured baseline revision

| Item | Value |
| --- | --- |
| `BASELINE_REVISION` (captured) | `b52b806641b0bbdedc78dc8deb9bba6b3ff137be` (`master`, branch point of `stage-1-test-infrastructure`) |
| Frozen baseline tag | `stage-0-baseline` → `6d42cc7831e11909e2a11f2ab0c9f4949eada1a9` |
| Working tree at capture | clean (`git status --porcelain` empty) |
| Toolchain | Temurin 8 (`/usr/lib/jvm/temurin-8-jdk-amd64`), Maven 3.6.3, mirror `aliyun-central` |
| Capture date | 2026-09-10 |

Both oracles below were captured at `b52b806`, the single revision required by
`docs/modernization/BRIEF.md` §5. The tag `stage-0-baseline` (`6d42cc7`) is an
ancestor of `b52b806`; the later commits are documentation-only (Phase 1 gate
renames, delta catalog, brief, issue forms), so `b52b806` is the correct
source-behaviour baseline for this stage.

## 2. T0 — Maven test oracle, before Stage 1 edits

Command A (default reactor, seven modules):

```bash
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -fae verify
```

Command B (full reactor, adds `monitoring` and `turbine-stream-service`):

```bash
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -fae -Pfull verify
```

| Module | Default `verify` | Tests | `-Pfull verify` | Tests |
| --- | --- | ---: | --- | ---: |
| piggymetrics (pom) | SUCCESS | — | SUCCESS | — |
| config | SUCCESS | 0 | SUCCESS | 0 |
| registry | SUCCESS | 0 | SUCCESS | 0 |
| gateway | SUCCESS | 2 | SUCCESS | 2 |
| auth-service | SUCCESS | 9 | SUCCESS | 9 |
| account-service | SUCCESS | 14 | SUCCESS | 14 |
| statistics-service | SUCCESS | 16 | SUCCESS | 16 |
| notification-service | SUCCESS | 18 | SUCCESS | 18 |
| monitoring | not in reactor | — | SUCCESS | 1 |
| turbine-stream-service | not in reactor | — | SUCCESS | 1 |
| **Total** | **BUILD SUCCESS** | **59** | **BUILD SUCCESS** | **61** |

Failures 0, errors 0, skipped 0 in every module in both runs. The counts match
the 59/61 expectation in `docs/modernization/BRIEF.md` §6.

## 3. T1 — HTTP wire oracle, before Stage 1 edits

Started per `docs/RUNBOOK.md` T1 and
`.agents/skills/testing-piggymetrics-demo/SKILL.md`:

```bash
JAVA_BIN=/usr/lib/jvm/temurin-8-jdk-amd64/bin/java MONGO_BIN=mongod \
  scripts/demo/start-local.sh
scripts/demo/smoke.sh
```

`start-local.sh` printed `T1 core stack is ready`; `smoke.sh` printed
`Smoke test passed` (exit 0). `JAVA_BIN` is overridden because the harness
default `$HOME/.local/jdks/jdk8u504-b01/bin/java` does not exist on this box;
the JDK is still Temurin 8.

The golden-master capture drives the account whose arithmetic reproduces the
recorded oracle in `docs/as-is/04-data-model-and-ownership.md:399-403`: a
`10000 JPY / MONTH` income, a `147.85 JPY / MONTH` expense, and a `100 JPY`
saving. Volatile fields (generated account name, `lastSeen`, the stub's
`date`) are normalized; **no JSON parsing is performed** — the bodies are
compared as text, because parsing destroys the scale-4 `BigDecimal` contract
(`docs/as-is/04-data-model-and-ownership.md:408-428`).

Recorded wire text:

```text
=== GET /rates (stub)
{"base": "USD", "date": "NORMALIZED", "rates": {"USD": 1, "EUR": 0.92, "RUB": 92.5, "JPY": 147.85}}
=== GET /accounts/current
{"name":"GOLDENUSER","lastSeen":"NORMALIZED","incomes":[{"title":"Salary","amount":10000,"currency":"JPY","period":"MONTH","icon":"salary"}],"expenses":[{"title":"Tokyo","amount":147.85,"currency":"JPY","period":"MONTH","icon":"travel"}],"saving":{"amount":100,"currency":"JPY","interest":1,"deposit":false,"capitalization":false},"note":"T1 golden"}
=== GET /statistics/current
[{"id":{"account":"GOLDENUSER","date":"2026-09-10T00:00:00.000+0000"},"incomes":[{"title":"Salary","amount":2.2341}],"expenses":[{"title":"Tokyo","amount":0.0330}],"statistics":{"EXPENSES_AMOUNT":0.0330,"INCOMES_AMOUNT":2.2341,"SAVING_AMOUNT":0.6800},"rates":{"EUR":0.92,"JPY":147.85,"RUB":92.5,"USD":1}}]
=== GET /statistics/demo
[]
=== GET /accounts/demo
{"name":"demo","lastSeen":"NORMALIZED","incomes":[{"title":"Salary","amount":42000.0,"currency":"USD","period":"YEAR","icon":"wallet"},{"title":"Scholarship","amount":500.0,"currency":"USD","period":"MONTH","icon":"edu"}],"expenses":[{"title":"Rent","amount":1300.0,"currency":"USD","period":"MONTH","icon":"home"},{"title":"Utilities","amount":120.0,"currency":"USD","period":"MONTH","icon":"utilities"},{"title":"Meal","amount":20.0,"currency":"USD","period":"DAY","icon":"meal"},{"title":"Gas","amount":240.0,"currency":"USD","period":"MONTH","icon":"gas"},{"title":"Vacation","amount":3500.0,"currency":"EUR","period":"YEAR","icon":"island"},{"title":"Phone","amount":30.0,"currency":"EUR","period":"MONTH","icon":"phone"},{"title":"Tokyo","amount":147.85,"currency":"JPY","period":"MONTH","icon":"travel"},{"title":"Gym","amount":700.0,"currency":"USD","period":"YEAR","icon":"sport"}],"saving":{"amount":5900.0,"currency":"USD","interest":3.32,"deposit":true,"capitalization":false},"note":"demo note"}
```

The four contract properties from `docs/modernization/BRIEF.md` §5 are all
present in that text:

| Contract property | Observed |
| --- | --- |
| Scale-4 trailing zeros survive | `0.0330`, `0.6800` |
| Mixed scales in one map | `"USD":1` next to `"JPY":147.85` |
| Reproducible arithmetic | `INCOMES_AMOUNT` `2.2341` |
| Jackson date form | `"date":"2026-09-10T00:00:00.000+0000"` (`+0000`) |

`GET /statistics/demo` is `[]` at baseline: the seeded `demo` account is
written straight into Mongo by `scripts/demo/seed-local.sh` and never goes
through `PUT /accounts/current`, so no datapoint is ever computed for it. That
empty body is part of the baseline, not a defect introduced by this stage.

## 4. Proof mode

**Target-only.** Stage 1 changes test-scope build inputs (JaCoCo, embedded
Mongo, JUnit/Mockito, dependency pins) while the runtime stack stays on the
source pins Java 8 / Boot `2.0.3.RELEASE` / Spring Cloud `Finchley.RELEASE`.
Source and target toolchains are therefore the same JDK, and a dual-run over
two JDKs would compare a run against itself. The comparison that carries the
proof is before/after at one toolchain: the T0 tables in §2 versus the
post-edit tables, and the §3 wire text versus the post-edit capture, compared
as text. The dual-run gate becomes meaningful in Stage 4, where the JDK
changes.

## 5. T3 (AMQP / D-07) — not run for this stage

`docs/modernization/BRIEF.md` §6 lists a T3 AMQP result for D-07 in the
baseline file. D-07 is a Spring AMQP / RabbitMQ delta owned by a later stage;
Stage 1 touches only test-scope build inputs and no messaging code, no
`docker-compose*.yml`, and no shared configuration, so a T3 Compose run has no
Stage 1 oracle value and is deliberately deferred to the stage that carries
D-07. This is a scope deferral, recorded here rather than silently skipped.
