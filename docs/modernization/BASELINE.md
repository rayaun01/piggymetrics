# Stage 2 baseline — recorded before any source edit

This file is the `BASELINE_PATH` required by
`.agents/skills/modernization-uplift/SKILL.md:119-128`. It records the T0 and
T1 oracles observed on this box before the Stage 2 Boot and Spring Cloud edits.

## 1. Captured baseline revision

| Item | Value |
| --- | --- |
| `BASELINE_REVISION` (captured) | `f6ab45d85bca8a2d87f931488195a73f6a784881` (`stage-2-boot-23-hoxton`, branch point off `origin/stage-1-test-infrastructure`) |
| Stage 1 state | unmerged at capture; Stage 1 baseline was `b52b806641b0bbdedc78dc8deb9bba6b3ff137be` |
| Source coordinates | Java 8 / Spring Boot `2.0.3.RELEASE` / Spring Cloud `Finchley.RELEASE` |
| Stage 2 target coordinates | Java 8 / Spring Boot `2.3.12.RELEASE` / Spring Cloud `Hoxton.SR12` |
| Working tree at capture | clean (`git status --porcelain` empty) |
| Toolchain | Temurin 8 (`/usr/lib/jvm/temurin-8-jdk-amd64`), Maven 3.6.3, mirror `aliyun-central` |
| Capture date | 2026-09-10 |

`git rev-parse HEAD` and `git rev-parse origin/stage-1-test-infrastructure`
both returned the captured revision above. The T0 logs and the T1 capture
below were produced from that revision.

## 2. T0 — Maven test oracle, before Stage 2 edits

Command A (default reactor, seven modules):

```bash
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -fae verify
```

Command B (full reactor, adds `monitoring` and `turbine-stream-service`):

```bash
JAVA_HOME=/usr/lib/jvm/temurin-8-jdk-amd64 mvn -B -fae -Pfull verify
```

The full logs are `/home/ubuntu/stage2/artifacts/t0-before-default.log` and
`/home/ubuntu/stage2/artifacts/t0-before-full.log`. The per-module reports
were calculated from the Surefire XML in
`/home/ubuntu/stage2/artifacts/t0-before-permodule-default.txt` and
`/home/ubuntu/stage2/artifacts/t0-before-permodule-full.txt`.

| Module | Default `verify` | Tests | Failures | Errors | Skipped | `-Pfull verify` | Tests | Failures | Errors | Skipped |
| --- | --- | ---: | ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: |
| piggymetrics (pom) | SUCCESS | — | — | — | — | SUCCESS | — | — | — | — |
| config | SUCCESS | 0 | 0 | 0 | 0 | SUCCESS | 0 | 0 | 0 | 0 |
| registry | SUCCESS | 0 | 0 | 0 | 0 | SUCCESS | 0 | 0 | 0 | 0 |
| gateway | SUCCESS | 2 | 0 | 0 | 0 | SUCCESS | 2 | 0 | 0 | 0 |
| auth-service | SUCCESS | 9 | 0 | 0 | 0 | SUCCESS | 9 | 0 | 0 | 0 |
| account-service | SUCCESS | 14 | 0 | 0 | 0 | SUCCESS | 14 | 0 | 0 | 0 |
| statistics-service | SUCCESS | 16 | 0 | 0 | 0 | SUCCESS | 16 | 0 | 0 | 0 |
| notification-service | SUCCESS | 18 | 0 | 0 | 0 | SUCCESS | 18 | 0 | 0 | 0 |
| monitoring | not in reactor | — | — | — | — | SUCCESS | 1 | 0 | 0 | 0 |
| turbine-stream-service | not in reactor | — | — | — | — | SUCCESS | 1 | 0 | 0 | 0 |
| **Total** | **BUILD SUCCESS** | **59** | **0** | **0** | **0** | **BUILD SUCCESS** | **61** | **0** | **0** | **0** |

Failures 0, errors 0, and skipped 0 were observed in every module in both
runs. The totals match the expected 59 default tests and 61 full-profile
tests from `docs/modernization/BRIEF.md` §6.

Per-class counts were captured from Surefire XML before the full-profile run
overwrote the reports. The default table is
`/home/ubuntu/stage2/artifacts/t0-before-perclass-default.txt`; it contains:

```text
account-service: AccountServiceApplicationTests=1, StatisticsServiceClientFallbackTest=1, AccountControllerTest=6, AccountRepositoryTest=1, AccountServiceTest=5
auth-service: AuthServiceApplicationTests=1, UserControllerTest=3, UserRepositoryTest=1, UserServiceTest=2, MongoUserDetailsServiceTest=2
gateway: GatewayApplicationTests=2
notification-service: NotificationServiceApplicationTests=1, RecipientControllerTest=2, RecipientRepositoryTest=5, EmailServiceImplTest=2, NotificationServiceImplTest=2, RecipientServiceImplTest=6
statistics-service: StatisticsServiceApplicationTests=1, ExchangeRatesClientTest=2, StatisticsControllerTest=3, DataPointRepositoryTest=2, ExchangeRatesServiceImplTest=4, StatisticsServiceImplTest=4
```

The full-profile table is
`/home/ubuntu/stage2/artifacts/t0-before-perclass-full.txt`; it contains the
same class counts plus `monitoring: MonitoringApplicationTests=1` and
`turbine-stream-service: TurbineStreamServiceApplicationTests=1`. Thus the
per-class counts match the per-module totals and the expected 59/61 split.

## 3. T1 — HTTP wire oracle, before Stage 2 edits

`command -v mongod` returned `/usr/local/bin/mongod`, with the legacy `mongo`
shell available on PATH. The stack was started with:

```bash
JAVA_BIN=/usr/lib/jvm/temurin-8-jdk-amd64/bin/java scripts/demo/start-local.sh
scripts/demo/smoke.sh
```

`start-local.sh` printed `T1 core stack is ready` and `smoke.sh` printed
`Smoke test passed`. Their full logs are
`/home/ubuntu/stage2/artifacts/t1-before-start.log` and
`/home/ubuntu/stage2/artifacts/t1-before-smoke.log`.

The unmodified capture script was run after the stack settled. The resulting
wire text is `/home/ubuntu/stage2/artifacts/gm-before.txt`; no JSON parsing or
additional normalization was used:

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

The four contract properties from `docs/modernization/BRIEF.md` §5 were
observed in the final capture:

| Contract property | Observed |
| --- | --- |
| Scale-4 trailing zeros survive | `0.0330`, `0.6800` |
| Mixed scales in one map | `"USD":1` beside `"JPY":147.85` |
| Reproducible arithmetic | `INCOMES_AMOUNT` `2.2341` |
| Jackson date form | `"date":"2026-09-10T00:00:00.000+0000"` (`+0000`) |

The text comparison against the extracted Stage 1 capture
`/home/ubuntu/stage2/artifacts/gm-stage1-recorded.txt` was:

```text
diff -u /home/ubuntu/stage2/artifacts/gm-stage1-recorded.txt /home/ubuntu/stage2/artifacts/gm-before.txt
```

It produced no output; the saved diff is
`/home/ubuntu/stage2/artifacts/gm-before-vs-stage1.diff` and is empty. An
initial unmodified capture made immediately after the first smoke run returned
`[]` for `/statistics/current`; that evidence is preserved at
`/home/ubuntu/stage2/artifacts/gm-before-initial-empty-stats.txt`. After the
stack settled, the second unmodified capture produced the final oracle above.

## 4. Proof mode

**Target-only.** Stage 2 keeps Java 8 on both sides: the source and target
toolchain are the same JDK, as stated in `docs/modernization/BRIEF.md` §6.
Therefore the proof that carries this stage is before/after at one toolchain
and one revision: the T0 tables in §2 versus the post-edit tables, and the §3
wire text versus the post-edit capture, compared as text. The first true
dual-JDK comparison is Stage 4.

## 5. T3 (AMQP / D-07) — not run for this stage

D-07 is owned by Stage 3. Stage 2 touches no messaging code and no Compose
files, so a T3/AMQP run is deliberately deferred to Stage 3 rather than
recorded as a Stage 2 result.
