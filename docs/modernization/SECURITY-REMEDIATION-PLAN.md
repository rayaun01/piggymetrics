# Security remediation plan — sequencing the scan findings against the stage ladder

Source of truth for the findings: code scan
`scan-abc9de1997d146da80002131c5d569bc` ("Piggymetrics EOL security audit — Java
+ .NET, egress attack surface"), completed 2026-09-10. 53 findings: 36 open, 17
dismissed; 3 critical and 6 high, all open. This document is the **approved amendment**
to `BRIEF.md` §3 (Gate 3, amendment 1, approved 2026-09-10; see §6). Stage 1b
still executes only after stage 1 is accepted — stages are not combined.

## 1. The question this answers

Two options were on the table: **solve then migrate**, or **fold hardening into
the migration**. Neither is correct for all 36 open findings, because they
divide cleanly by one property: *does the code that carries the vulnerability
survive the migration?*

| Class | Property | Correct sequencing |
| --- | --- | --- |
| **A — Deployment/config** | No Java, Boot, or Spring Cloud coupling. Same fix on Boot 2.0 and Boot 3. | **Fix first**, in its own stage, before Boot moves. |
| **B — Migration-coupled** | The vulnerable construct is *deleted or rewritten* by a stage anyway; the API needed to express the fix does not exist on both sides. | **Fix inside that stage**, as a named exit criterion. |
| **C — Closed by the migration** | The vulnerability *is* the EOL pin. Upgrading is the fix. | **No separate work**; assert closure as stage proof. |
| **D — Out of scope** | The three .NET services, frozen by `BRIEF.md` §2. | **Separate track**, does not gate the Java ladder. |

The trap in "solve then migrate" is class B: writing an authorization fix in
`#oauth2.hasScope(...)` SpEL means writing it in an API that stage 3 removes,
then writing it a second time. The trap in "fold everything into the migration"
is class A: the three criticals are reachable **today**, and the ladder's last
stage is four stages away.

## 2. Class A — fix before Boot moves (new stage 1b)

All of these are Compose, `.env`, and Spring Security config. They carry no
version coupling, and they are where the unauthenticated chain actually starts.

| Finding | Sev | What | Why class A |
| --- | --- | --- | --- |
| `sfind-46e861b0` | **critical** | Mongo published `27017:27017` with root/`password` from the committed `.env` | `docker-compose.core.yml:2-14`; pure deployment |
| `sfind-4ff04a82` | **critical** | Service client secrets are all `password`, compared with `NoOpPasswordEncoder`, and `/uaa/oauth/token` is public via Zuul | The *secret* half is `.env` + `${VAR:-password}`; only the encoder half touches OAuth2 code |
| `sfind-1473febf` | high | Committed `.env` is the single source of every credential | Removing it is version-neutral |
| `sfind-8e8a5e51` | medium | Config server (8888), Eureka (8761), and all four services host-published, bypassing the gateway edge | `ports:` mappings only |
| `sfind-385e17fa` | high | Eureka registry unauthenticated (topology disclosure, registration poisoning) | Eureka survives to the target stack, so this fix is **not** superseded — it must be made once, early, and carried |
| `sfind-e4317da5`, `sfind-a3b1b005`, `sfind-f7f94836`, `sfind-88e7edca`, `sfind-3c2d0757`, `sfind-a10fb0646`, `sfind-bb92d4cb`, `sfind-5310195c` | med/low | The same committed-default credential, seen from each file that reads it | One change closes the set |
| `sfind-9fe70461`, `sfind-1d36b8dd`, `sfind-7de286ab` | low | T3/dev overlays publish datastore, registry, and Turbine `8989` to the host | `ports:` mappings only |
| `sfind-205edeb3` | low | Config server `permitAll()` on `/actuator/**` | Config-server security config, unchanged by the Boot move |
| `sfind-555c1446`, `sfind-4b9bc057`, `sfind-839afeac`, `sfind-7ac317a1`, `sfind-30d1ce69` | low | Account name / e-mail PII at info level | Log statements; version-neutral |

**Ordering inside 1b matters.** `sfind-4ff04a82` is the *upstream link* of the
chain whose sinks are the two BOLA criticals. Replacing the committed default
secrets with injected high-entropy values removes the unauthenticated path to
those sinks **without touching the authorization code that stage 3 rewrites**.
That is the whole reason class B can safely wait.

### 2.1 The one real conflict: 1b changes the migration's oracle

This is the "check that the vulns do not break the migration path" part, and it
is not hypothetical:

- `scripts/demo/start-local.sh:71,77,81,101-107` and `seed-local.sh:6` all
  default to `${…:-password}`. Dropping the defaults **breaks T1 and T2**, and
  T1 is the golden master for stages 2–5 (`BRIEF.md` §5).
- Requiring auth on Eureka changes every client's `defaultZone` in
  `config/src/main/resources/shared/*.yml` — orchestrator-owned files.
- Un-publishing ports changes what the smoke script can reach directly.
- Removing the `demo` anonymous read (class B) changes T1 **response codes**.

Consequences, binding on stage 1b:

1. Stage 1b must update the harness in the same commit as the hardening: the
   scripts generate a per-run secret and export it, rather than falling back to
   a literal. A harness that still works only with `password` is a failed 1b.
2. Stage 1b is a **deliberate, documented behaviour change**, so it re-captures
   the T1 golden master and rewrites `BASELINE.md` with a new
   `BASELINE_REVISION`. Every diff is adjudicated under §5's triage rule as
   *intended*, citing the finding ID as the forcing fact. Stages 2–5 then
   compare against the **1b** capture, not the stage-0 one.
3. Because 1b moves the oracle, it must land **before** stage 2 and **after**
   stage 1 (which is behaviour-neutral, test-scope only) — hence 1b, not 0b.

## 3. Class B — fix inside the stage that rewrites the code

| Finding | Sev | Stage that owns it | Why it cannot land earlier |
| --- | --- | --- | --- |
| `sfind-5a35809a` | **critical** | 3 | `GET /accounts/{name}` is guarded by `@PreAuthorize("#oauth2.hasScope('server') or #name.equals('demo')")`. The `#oauth2` SpEL root object comes from `spring-security-oauth2`, which stage 3 retires (D-06, `05-…:375-379`). The ownership check must be written in the replacement expression language, once. |
| `sfind-005d4b09` | high | 3 | Same construct on `GET`/`PUT /statistics/{accountName}`, plus the coarse `server` scope minted in `OAuth2AuthorizationConfig:49-62`, which stage 3 replaces. Per-service least-privilege scopes are a stage-3 design decision, not a patch. |
| `sfind-31cdf4c1` | low | 3 | The hardcoded `#accountName.equals('demo')` bypass lives in the same annotation. |
| `sfind-dcd648d7` | low | 3 | `InMemoryTokenStore`, `tokenKeyAccess("permitAll()")`, ROPC on the secretless `browser` client — all `@EnableAuthorizationServer` config, which does not exist in the target stack. |
| `sfind-4ff04a82` (encoder half) | **critical** | 3 | `NoOpPasswordEncoder` → BCrypt with hashed secrets belongs with the client-registry rewrite. The exploitable half is closed in 1b. |
| `sfind-7e00f3b5` | low | 3 | Plaintext `http://` service-to-service token traffic with broad `server` scope; resolved by the stage-3 scope redesign or accepted with a cited reason. |
| `sfind-2c1d858f`, `sfind-b2d72d64` | low | 3 (Java), D (.NET) | Exception messages to clients; the Java half rides the security rework. |

**Stage 3 gains a security exit criterion**, alongside its existing route/security
proof: no endpoint may authorize an object read on scope alone. A stage-3 PR that
ports `hasScope('server')` verbatim into the new expression language has
preserved the critical and must be rejected.

## 4. Class C — closed by the migration, asserted not patched

| Finding | Sev | Closed by | Assertion required |
| --- | --- | --- | --- |
| `sfind-b54f1427` | high | **Stage 2.** The Hystrix Dashboard `/proxy.stream` SSRF is CVE-2020-5412, unfixed in `Finchley.RELEASE`; the fix shipped in Spring Cloud Netflix ≥ 2.1.6 / 2.2.4, and stage 2's `Hoxton.SR12` is past that. **Stage 3** then deletes the tier outright (D-07). | Stage 2 proves the origin allowlist is active; stage 3 proves the module is gone. Do **not** hand-patch Finchley. |
| `sfind-482acc68` | medium | Stages 2–5, by definition — it *is* the Boot 2.0/Finchley/Java 8 pin | Final stage-5 proof |
| `sfind-c502527a`, `sfind-58fcfef6`, `sfind-63d44500`, `sfind-6cee5540`, `sfind-6680d15f`, `sfind-d598d8ac`, `sfind-3df93047`, `sfind-253886e7`, `sfind-59ac6d4a` (all dismissed) | low | **Stage 4** (JDK 17) | The base images move with `java.version`; already dismissed as "intentional baseline", so no separate work |

Interim mitigation for `sfind-b54f1427` belongs in 1b (un-publish `9000`), which
is why the SSRF is listed as class C but its port mapping as class A.

## 5. Class D — the frozen .NET services

16 findings, including two highs (`sfind-391d3acb` compliance KYC/AML,
`sfind-b77d4137` fraud detection — both fully unauthenticated REST APIs) and the
EOL .NET Core 2.1 / Newtonsoft 11.0.2 set. `BRIEF.md` §2 freezes these services
and no run tier deploys them, which is why the scan rated the EOL items low.
They are **not** blocked on the Java ladder and should not be folded into it:
a separate track can run in parallel. Flagging one asymmetry for the record —
"unauthenticated KYC/AML audit trail" is a high finding sitting in a service
nobody is migrating; freezing the code does not freeze the risk if it is ever
deployed.

## 6. Amended ladder and the approval this needs

| Stage | Change |
| --- | --- |
| 1 | Unchanged (in flight) |
| **1b — `stage-1b-security-baseline`** | **New.** Class A. Exit: no committed credential, gateway is the only published edge, Eureka authenticated, PII out of logs, harness works on generated secrets, T1 golden master re-captured, `BASELINE.md` rewritten with the new revision and the intended-diff adjudication |
| 2 | Unchanged, plus: assert CVE-2020-5412 closure (`sfind-b54f1427`) |
| 3 | Unchanged, plus security exit criterion: object-level authorization replaces scope-only checks (`sfind-5a35809a`, `sfind-005d4b09`, `sfind-31cdf4c1`, `sfind-dcd648d7`, encoder half of `sfind-4ff04a82`) |
| 4 | Unchanged, plus: assert EOL base-image closure |
| 5 | Unchanged, plus: final scan re-run as stage proof |

`BRIEF.md` §3 was approved unconditionally on 2026-09-09 as a five-stage ladder.
Inserting stage 1b and adding exit criteria to stages 2–5 changed the approved
scope, so Gate 3 was re-opened and **approved unconditionally on 2026-09-10** by
Ray (`rayaun`), recorded in `BRIEF.md` §8 as amendment 1. Verbatim: "Stage 1b
insertion. Okay with generated per run secret".

Stage 1 is unaffected and continues; stage 1b begins only once stage 1 is
accepted.

`BRIEF.md` §7 open question 9 — **what replaces the committed `.env` for local
development?** — is answered by the same approval.

### 6.1 Decided: generated per run

Approved 2026-09-10 alongside the stage insertion.

The apparent trade-off ("reproducible T1" versus "stricter") mostly dissolves on
inspection. The golden master is the T1 **response text** — credentials never
appear in it — and `smoke.sh` already generates the account name per run
(`wire1788889784`). Generating the password and the three service secrets
alongside it therefore leaves the oracle bit-identical. The
developer-supplied variant is the one that makes T1 environment-dependent, and
it reliably degrades back to someone typing `password`.

Stage 1b therefore:

- removes every `${…:-password}` fallback; `start-local.sh` mints high-entropy
  values per run and exports them;
- **fails closed** — an unset secret aborts the run; there is no default;
- keeps the injection surface **environment-variable-only, with no secrets
  file**, so a real deployment swaps the provider (Vault / SSM / Kubernetes
  secret) without an application-code change;
- adds `.env` to `.gitignore` and treats the present values as permanently
  compromised — they are in git history, so removal is not rotation.

### 6.2 Consequences of the "eventually real data" trajectory

The current corpus is mock data. Two items change character once it is not:

1. **The `demo` account must stop being an authorization exception.**
   `#name.equals('demo')` plus `permitAll()` on `/demo` makes one account
   world-readable — defensible for a sandbox, not for real data. The fix is
   stage-3 work (`sfind-31cdf4c1`, same annotation as the BOLA fix) and its
   shape is fixed here: an explicit public endpoint over non-sensitive sample
   data, never a bypass inside the ownership rule.
2. **The frozen .NET services become a deployment blocker, not a low finding.**
   `sfind-391d3acb` (KYC/AML/audit) and `sfind-b77d4137` (fraud detection) are
   fully unauthenticated APIs, rated against a corpus nobody deploys. Against
   real data they need their own remediation track before any deployment. They
   remain outside the Java ladder (`BRIEF.md` §2) and outside this plan's
   authority to schedule.
