---
name: modernization-preflight
description: Invoke before discovery or uplift to prove repository scope, toolchains, run tiers, and analysis coverage; write a complete readiness record.
---

## Named inputs

- `SYSTEM`: `piggymetrics`
- `SOURCE_VERSION`: `Java 8`
- `TARGET_VERSION`: the pinned modern-Java target
- `PREFLIGHT_PATH`: `docs/modernization/PREFLIGHT.md`
- `RUNBOOK_PATH`: `docs/RUNBOOK.md`
- `DOTNET_SCOPE_PATH`: `docs/OUT-OF-SCOPE-DOTNET.md`

Write `PREFLIGHT_PATH`. Do not parse positional arguments or read argv.

## Check 0 — recorded answers, not an interview

The readiness answers must already be recorded in repository artifacts:

1. Scope boundary: `DOTNET_SCOPE_PATH`.
2. Build, test, run recipes and tiers: `RUNBOOK_PATH`.
3. Bespoke infrastructure: the Maven Central mirror requirement in
   `RUNBOOK_PATH`.
4. Prior attempts and known gaps: the existing `docs/as-is/` records and
   open items they cite.
5. Off-limits files or components: `DOTNET_SCOPE_PATH` and the brief's scope.

Record which artifact supports each answer. If no artifact supports an answer,
record an explicit open item; never infer one.

## Check 1 — repository and stack

Confirm the nine Java/Maven reactor units and the three .NET services. Record
the default seven-module reactor and the `full` profile's
`monitoring`/`turbine-stream-service` units. Confirm the root POM, module POMs,
Java sources, tests, Docker/Compose descriptors, and run scripts.

## Check 2 — analysis and migration tools

For each tool, record `present`, `runnable-here`, or `actually-ran`, version
when available, purpose, and coverage lost when unavailable:

| Tool | Use |
|---|---|
| `scc` or `cloc` | quantitative inventory |
| `lizard` | complexity sampling |
| `glow` | artifact rendering |
| `delta` | reviewable side-by-side diffs |
| OpenRewrite | Java migration analysis via `mvn rewrite:dryRun` |

Do not claim OpenRewrite findings unless `mvn rewrite:dryRun` actually ran.
The provisioned `central` mirror in `~/.m2/settings.xml` is required for
dependency resolution; HTTP 429 or an unresolved artifact means the tool did
not run.

## Check 3 — toolchains

Check and record:

- `/usr/lib/jvm/temurin-8-jdk-amd64` exists and runs `java -version`.
- A JDK 17 installation exists and runs `java -version`.
- `mvn -version` reports the selected `JAVA_HOME`.
- `~/.m2/settings.xml` contains a `central` mirror.
- The root enforcer requires `[1.8,1.9)` before the target-stage change.

Run no Maven build here unless the readiness report explicitly calls for the
smallest T0 proof and the environment is ready.

## Check 4 — run tiers

Check command availability without starting a long-running tier:

- T0: `mvn -B -fae test`.
- T1: `mongod` and `mongo`, plus the local rates stub.
- T2/T3: `docker compose`.

Record the exact recipe from `RUNBOOK_PATH`, the missing prerequisite, and the
tier that degrades when it is unavailable.

## Check 5 — optional context

Record optional production or deployment context only when it is available and
authorized: representative traffic, deployment manifests, runtime metrics,
and external integration contracts. Mark absent context as a gap; do not infer
production behavior from source names.

## Check 6 — source completeness and scope boundary

Confirm the source tree is complete for the Java scope, the `.NET` directories
remain frozen, and no generated or external dependency source is being treated
as application source. Record any missing source, generated artifact, or
runtime dependency as a gap. Confirm that the requested system is the full
Java scope rather than an undocumented slice. If it is a slice, record its
entry points, excluded modules, and downstream consumers explicitly.

## Verdict

End with exactly one verdict:

- **Ready** — all required checks pass and no open item blocks discovery.
- **Ready-with-gaps** — discovery can proceed, but list every gap and its
  downstream consequence.
- **Not-ready** — a missing source, toolchain, run dependency, or scope answer
  prevents reliable discovery.

The artifact must include every check, evidence path, command result, open item,
and verdict. Downstream skills read this artifact rather than repeating the
checks.
