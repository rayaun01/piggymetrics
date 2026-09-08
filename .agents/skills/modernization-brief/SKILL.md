---
name: modernization-brief
description: Invoke after discovery and delta analysis to bind an approved, staged uplift plan with entry/exit gates, a representative pilot, and proof obligations.
---

## Named inputs

- `SYSTEM`: `piggymetrics`
- `SOURCE_VERSION`: `Java 8`
- `TARGET_VERSION`: the exact pinned modern-Java target
- `BRIEF_PATH`: `docs/modernization/BRIEF.md`
- `AS_IS_DIR`: `docs/as-is`
- `DELTA_CATALOG_PATH`: `docs/modernization/DELTA-CATALOG.md`
- `PREFLIGHT_PATH`: `docs/modernization/PREFLIGHT.md`

No positional arguments or argv parsing. Do not write the brief until the
discovery artifacts and delta catalog exist.

## Gates

1. **Discovery gate:** all four `docs/as-is/` artifacts exist, are cited, and
   have no unresolved completeness blocker.
2. **Uplift gate:** `DELTA_CATALOG_PATH` exists and was derived from the exact
   source/target pins. A same-stack uplift has no brief without its catalog.
3. **Approval gate:** record the approval decision, approver role, date, scope,
   and any conditions. An absent or conditional approval blocks migration.
4. **Pilot gate:** the staged plan names one representative mid-complexity
   pilot and requires its diff, catalog feedback, playbook, and proof before
   fan-out.

## The Brief

Write `BRIEF_PATH` with these sections:

1. **Objective** — preserve behaviour while moving the pinned Java/Maven
   system from `SOURCE_VERSION` to `TARGET_VERSION`.
2. **Target architecture** — only changes forced by the catalog; no redesign.
3. **Phased sequence** — the stage ladder below, one stage per branch, tag, and
   reviewable PR.
4. **Behaviour walkthroughs** — the T1 wire contract, T0 tests, and known
   currency/rates/data boundaries.
5. **Behaviour contract** — the baseline file, golden master, and triage rule
   for every result delta.
6. **Validation strategy** — T0, T1, T2, T3, per-unit Maven proof, and
   dual-run availability or an honest target-only degradation.
7. **Open questions** — only cited gaps; never fill them with preference.
8. **Approval block** — the gate record and conditions.

## Forced stage ladder

| Stage | What moves | What forces the position | Branch/tag | Proof |
|---|---|---|---|---|
| 0 | Stage-0 harness and frozen baseline | The baseline must be recoverable and cited | `stage-0-baseline` | T0/T1 records and revision |
| 1 | JaCoCo, flapdoodle, and test-runner infrastructure | JaCoCo `0.7.6` fails before tests on newer class files; flapdoodle `1.50.3` is tied to Mongo 3.x; JUnit 4/Mockito 2.15.0 blocks later runtime work | `stage-1-test-infrastructure` | T0 pass/fail table |
| 2 | Boot 2.3.12 and Cloud Hoxton | This is the last generation where the old Netflix modules and the next supported Boot generation coexist | `stage-2-boot-23-hoxton` | T0 plus T1 |
| 3 | Zuul, Hystrix, Ribbon, Turbine, dashboard, and OAuth2 retirement/replacement | Those Netflix artifacts are not built in 2020.0+; OAuth2 autoconfigure has no GA beyond 2.6.8, so its rework cannot wait for Boot 2.7 | `stage-3-netflix-oauth` | T0/T1 and route/security proof |
| 4 | Boot 2.7, Cloud 2021.0, JDK 17 | Boot 2.3 supports only through Java 15; Boot 2.7 is the compatible bridge to Java 17 | `stage-4-boot-27-jdk17` | T0/T1 dual-run |
| 5 | Boot 3, Cloud 2022.0+, `javax` to `jakarta` | Boot 3 requires Java 17 and removes the `javax` APIs | `stage-5-boot3-jakarta` | full T0–T3 and golden master |

Every stage has a branch, an immutable tag, and a reviewable PR. Do not
combine stages because a different order feels cleaner; cite the register
fact that forces any proposed change.

## Required pilot

The pilot is `account-service`: it is representative rather than easiest,
because it exercises Mongo repositories, controller/wire behavior, security,
tests, and shared configuration. Migrate it in-session, run its exact
target-toolchain build, add every surprise to the catalog, and write
`docs/modernization/PLAYBOOK.md` for an engineer with no prior context.
Show the pilot diff and playbook in a PR before any fan-out.

The brief must state that the orchestrating session owns coordinated cuts and
the shared files `config/src/main/resources/shared/*.yml` and `pom.xml`.
Delegated units never edit those files.
