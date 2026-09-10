# Delta catalog — Boot 2.0.3 / Finchley / Java 8 → Boot 3.0.13 / 2022.0 / Java 17

Skill: `.agents/skills/modernization-brief/SKILL.md` (Uplift gate) and
`.agents/skills/modernization-uplift/SKILL.md` (Step 3).

## Pins this catalog was derived from

| Side | Pin | Evidence |
| --- | --- | --- |
| Source — Spring Boot | `2.0.3.RELEASE` | `pom.xml:11-16` |
| Source — Spring Cloud | `Finchley.RELEASE` (component trains `2.0.0.RELEASE`) | `pom.xml:18-22`, `pom.xml:24-34`, `docs/as-is/05-dependency-and-eol-register.md:75-79` |
| Source — Java | `1.8`, enforced `[1.8,1.9)` | `pom.xml:21`, `pom.xml:56-80` |
| Source — reactor | 7 default modules + 2 under `full` | `pom.xml:36-54` |
| Target — stage 1 | JaCoCo ≥ `0.8.11`, flapdoodle 3.x/4.x, JUnit 5 (Jupiter), Boot-managed Mockito; **still Boot 2.0.3 / Java 8** | `05-…:33-40`, `05-…:270`, `05-…:310-312` |
| Target — stage 2 | Boot `2.3.12.RELEASE` + Spring Cloud `Hoxton.SR12`, Java 8 | `05-…:290`, `05-…:333-350` |
| Target — stage 3 | Netflix retirement + OAuth2 rework on Boot 2.3/Hoxton | `05-…:291`, `05-…:360-379` |
| Target — stage 4 | Boot `2.7.18` + Spring Cloud `2021.0.x`, JDK 17 | `05-…:292`, `05-…:350-356` |
| Target — stage 5 | Boot `3.0.13` + Spring Cloud `2022.0.x`, `javax` → `jakarta` | `05-…:293`, `05-…:372-374` |

Baseline revision: tag `stage-0-baseline` (`6d42cc7`). This catalog is derived
from the register `docs/as-is/05-dependency-and-eol-register.md`, which is the
discovery source and is not rebuilt here.

## Tool state

**OpenRewrite did not run.** `mvn rewrite:dryRun` was launched with plugin
`4.46.0` and never reached the recipe: the provisioned mirror served the
plugin's dependency closure at ≈1–2 kB/s and the goal was still resolving
dependencies when the budget expired (`docs/modernization/PREFLIGHT.md`,
Check 2). Per the uplift skill, a resolution failure means the tool did not
run, so **no delta below is a tool finding** — every one is hand-derived from a
cited register row or a repository citation. `.agents/workflows/uplift_deltas.py`
is the delta-discovery workflow for the execution session; its output extends
this catalog rather than replacing it.

## How to read the classification

- **Mechanical** — the edit is determined by the coordinate or the package
  name; a reviewer checks that nothing else changed.
- **Judgment** — the edit requires a decision that the diff cannot justify by
  itself (behaviour, replacement design, or a semantics change).

Blast radius is stated in units, not files: `cross-unit` means more than one
Maven unit must change together; `shared-file` means the change touches
`pom.xml` or `config/src/main/resources/shared/*.yml`, which only the
orchestrating session may edit.

## Rank — shared-file and cross-unit effects first

| Rank | Delta | Stage | Class | Blast radius |
| ---: | --- | ---: | --- | --- |
| 1 | D-01 Boot parent + Spring Cloud train bump | 2, 4, 5 | Judgment | shared-file, all 9 units |
| 2 | D-02 root enforcer re-target | 4 | Mechanical | shared-file, all 9 units |
| 3 | D-03 Zuul → Spring Cloud Gateway | 3 | Judgment | shared-file, `gateway` |
| 4 | D-04 OAuth2 stack rework | 3 | Judgment | shared-file, 4 secured units |
| 5 | D-05 Hystrix → Resilience4j | 3 | Judgment | shared-file, 4 units |
| 6 | D-06 Ribbon → Spring Cloud LoadBalancer | 3 | Judgment | shared-file, `gateway` |
| 7 | D-07 Delete the Hystrix metrics tier | 3 | Judgment | 2 units + 3 publishers |
| 8 | D-08 JaCoCo bump | 1 | Mechanical | 4 units |
| 9 | D-09 flapdoodle replacement | 1/2 | Judgment | 4 units |
| 10 | D-10 JUnit 4 + Mockito → JUnit 5 | 1 | Judgment | 5 units (6 with `full`) |
| 11 | D-11 `javax.validation` → `jakarta.validation` | 5 | Mechanical | 4 units |
| 12 | D-12 `javax.mail` → `jakarta.mail` | 5 | Judgment | `notification-service` |
| 13 | D-13 `WebSecurityConfigurerAdapter` removal | 4/5 | Judgment | 2 units |
| 14 | D-14 Sleuth → Micrometer Tracing | 5 | Judgment | shared-file, 5 units |
| 15 | D-15 Jackson 2.9 → 2.14 serialization drift | 2, 4, 5 | Judgment | wire contract, 4 units |
| 16 | D-16 MongoDB driver 3.6.4 → 4.x | 2, 4, 5 | Judgment | 4 units |
| 17 | D-17 Jackson 1.x `@JsonIgnoreProperties` | after 3 | Judgment | 2 units, test-visible |
| 18 | D-18 `com.sun.security.auth.UserPrincipal` in tests | 3 | Judgment | 4 units |
| 19 | D-19 Local version pins (`json-path`, `guava`) | 1 | Mechanical | 4 units |
| 20 | D-20 JDK path pins in scripts, CI, Dockerfiles | 4 | Mechanical | 11 files, 3 systems |
| 21 | D-21 Container heap/metaspace flags | 4/5 | Judgment | 9 Dockerfiles + harness |
| 22 | D-22 Default time zone / CLDR locale data | 4 | Judgment | `statistics-service`, stored keys |
| 23 | D-23 Server-side JavaScript `@Query` | 2 | Judgment | `notification-service` |
| 24 | D-24 Inert Feign fallback config | 3 | Judgment | shared-file, `statistics-service` |
| 25 | D-25 Prebuilt upstream images | 1 | Mechanical | `docker-compose.yml` |

---

## D-01 — Boot parent and Spring Cloud train bump

**Class:** Judgment. **Stage:** 2 (Boot 2.3.12 / Hoxton.SR12), 4 (2.7.18 /
2021.0.x), 5 (3.0.13 / 2022.0.x).
**Sites:** `pom.xml:11-16` (parent), `pom.xml:18-22`
(`spring-cloud.version`), `pom.xml:24-34` (BOM import). **Site count:** 3
declarations in 1 shared file; the resolved effect reaches all 9 units and 254
artifacts (`05-…:128`).
**Blast radius:** shared-file, whole reactor. **Orchestrator-owned.**
**Forcing fact:** Boot 2.0.3 OSS support ended 2019-03-01 and Finchley only
ever supported Boot 2.0.x (`05-…:298-299`); Hoxton is the only generation where
the Netflix modules and a supported Boot coexist (`05-…:365-368`).
**Judgment content:** the train↔Boot pairing per stage and the decision not to
skip a generation; each bump changes managed versions of Jackson, Tomcat, the
Mongo driver, Spring Security and Mockito simultaneously (D-15, D-16, D-10).

## D-02 — Root enforcer re-target

**Class:** Mechanical. **Stage:** 4.
**Sites:** `pom.xml:56-80` (`requireJavaVersion [1.8,1.9)`). **Site count:** 1.
**Blast radius:** shared-file, every lifecycle build. **Orchestrator-owned.**
**Forcing fact:** Boot 2.3.12 is compatible only up to Java 15, Boot 2.7.18 up
to Java 21, Boot 3 requires 17 (`05-…:350-356`). The enforcer must be
re-targeted at the JDK move, **not removed** (`05-…:268`).

## D-03 — Zuul → Spring Cloud Gateway

**Class:** Judgment. **Stage:** 3.
**Sites:** `gateway/pom.xml:19-22`,
`gateway/src/main/java/com/piggymetrics/gateway/GatewayApplication.java:6,10`
(`@EnableZuulProxy`), `config/src/main/resources/shared/gateway.yml:13-42`
(`zuul.routes.*`, `stripPrefix: false`, `zuul.ignoredServices: '*'`),
`config/src/main/resources/shared/gateway-local.yml` (`zuul.*`).
**Site count:** 1 POM + 2 source lines + 2 shared YAML files.
**Blast radius:** shared-file + `gateway`; the browser-visible prefix contract
survives the hop and is consumed downstream (`docs/as-is/03-api-contract-inventory.md:14-20`).
**Forcing fact:** `spring-cloud-starter-netflix-zuul` is not built in Spring
Cloud 2020.0+ — the artifact does not exist at any version compatible with Boot
2.7 or 3 (`05-…:300`, `05-…:329-331`).
**Judgment content:** `zuul.routes.*` (path → service map) becomes
`spring.cloud.gateway.routes[*]` predicates/filters — a different model, not a
key rename (`05-…:549-551`). The four routes and their `stripPrefix: false`
behaviour are the contract to preserve.

## D-04 — OAuth2 stack rework

**Class:** Judgment. **Stage:** 3 — the earliest hard wall in the repository.
**Sites:** `auth-service/pom.xml:31-34`, `account-service/pom.xml:19-22`,
`statistics-service/pom.xml:27-30`, `notification-service/pom.xml:19-22`;
`auth-service/.../config/OAuth2AuthorizationConfig.java`,
`auth-service/.../config/WebSecurityConfig.java`,
`account-service/.../config/ResourceServerConfig.java`,
`statistics-service/.../config/ResourceServerConfig.java`,
`notification-service/.../config/ResourceServerConfig.java`,
`account-service/.../service/security/CustomUserInfoTokenServices.java`,
`statistics-service/.../service/security/CustomUserInfoTokenServices.java`.
**Site count:** 4 POMs + 9 source files across 4 units.
**Blast radius:** cross-unit + shared-file (client/secret and `security.*`
keys). **Orchestrator-owned cut.**
**Forcing fact:** `spring-cloud-starter-oauth2` ceases to exist after Hoxton
and `spring-security-oauth2-autoconfigure` has **no GA past `2.6.8`** (Boot
2.6), the 2.7 line stopping at `2.7.0-M3` (`05-…:306-308`, `05-…:375-379`).
**Judgment content:** a behavioural rewrite — custom authorization server →
Spring Authorization Server, `CustomUserInfoTokenServices` → native resource
server. The baseline uses **opaque** tokens checked against `auth-service`; no
JWT library is on the resolved graph, so "introduce JWTs" is a new capability
needing its own decision (`05-…:173-176`).

## D-05 — Hystrix → Resilience4j

**Class:** Judgment. **Stage:** 3.
**Sites:** `account-service/pom.xml:59-66`,
`statistics-service/pom.xml:59-62`, `notification-service/pom.xml:59-62`;
`account-service/.../AccountApplication.java:5,15` (`@EnableCircuitBreaker`);
`config/src/main/resources/shared/account-service.yml` (`hystrix.*`),
`config/src/main/resources/shared/gateway.yml:1-8` (`hystrix.*`).
**Site count:** 3 POMs + 2 source lines + 2 shared YAML files.
**Blast radius:** cross-unit + shared-file.
**Forcing fact:** removed from the train in 2020.0; Netflix Hystrix's last
release is `1.5.18` (2018-11-16) and the project is not in active development
(`05-…:301`).
**Judgment content:** `hystrix.command.*` → `resilience4j.circuitbreaker.instances.*`
(`05-…:549-551`) and the fallback behaviour of
`account-service/.../client/StatisticsServiceClient.java:10`, which is the one
fallback actually wired (`feign.hystrix.enabled: true`,
`shared/account-service.yml:24-26`).

## D-06 — Ribbon → Spring Cloud LoadBalancer

**Class:** Judgment. **Stage:** 3.
**Sites:** transitive via the Eureka/Zuul/Feign starters (resolved `2.2.5`,
7 units — `05-…:225`); configured in
`config/src/main/resources/shared/gateway.yml:9-12` (`ribbon.*`).
**Site count:** 1 shared YAML surface; 0 declared coordinates.
**Blast radius:** shared-file; latency/retry behaviour of every routed call.
**Forcing fact:** `spring-cloud-netflix-ribbon` last shipped in 2.2.9.RELEASE
and is absent from 3.0.0 (`05-…:304`, `05-…:329-331`).
**Judgment content:** `ribbon.*` → `spring.cloud.loadbalancer.*` is not a
one-to-one key map; timeout and retry semantics differ.

## D-07 — Delete the Hystrix metrics tier

**Class:** Judgment (a deletion decision). **Stage:** 3, scheduled **first** —
the cheapest item.
**Sites:** `monitoring/pom.xml:23-26`,
`monitoring/.../MonitoringApplication.java:5,8` (`@EnableHystrixDashboard`);
`turbine-stream-service/pom.xml:28-31`,
`turbine-stream-service/.../TurbineStreamServiceApplication.java:6,9`
(`@EnableTurbineStream`); `spring-cloud-netflix-hystrix-stream` in
account/statistics/notification (`05-…:107`).
**Site count:** 2 whole units + 3 publisher declarations; both units live only
under `-Pfull` (`pom.xml:46-54`), so a default `mvn verify` never compiles them.
**Blast radius:** 2 units, plus the `full` profile and `docker-compose.yml`
service entries.
**Forcing fact (two, independent):** the artifacts are removed from the train
in 2020.0, and `turbine-core` already resolves to the non-GA `2.0.0-DP.2` with
**no GA 2.x to upgrade to** (`05-…:131-136`, `05-…:303`). Those two facts alone
force the deletion: there is no artifact to upgrade to at any Boot 2.7/3
compatible version.
**What the HTTP evidence does and does not show:** `/hystrix.stream` returned
404 on every port in T1 (`05-…:562`), but that does **not** prove the tier is
inert. `spring-cloud-netflix-hystrix-stream` publishes over **RabbitMQ**, not
over an HTTP endpoint, and no broker runs in T1 or T2
(`docs/as-is/03-api-contract-inventory.md:132`). Only T3 starts `rabbitmq:3-management`
(`docker-compose.yml:3-11`) together with the publishers, Turbine, and the
dashboard, so the AMQP path is **unmeasured**, not disproved.
**Judgment content:** whether any dashboard behaviour exists to preserve. The
executing session must exercise the T3 AMQP pipeline (publisher →
`/turbine.stream` → `/hystrix`) and record the result in `BASELINE.md` before
the deletion lands; the deletion proceeds either way, but the PR must state
whether it removes working monitoring behaviour or an already-dead tier. Open
item 8 in `05-…:622-629` is that check.

## D-08 — JaCoCo bump

**Class:** Mechanical. **Stage:** 1.
**Sites:** `auth-service/pom.xml:75-93`, `account-service/pom.xml:95-113`,
`statistics-service/pom.xml:97-115`, `notification-service/pom.xml:96-114`
(`jacoco-maven-plugin 0.7.6.201602180812`).
**Site count:** 4 plugin declarations in 4 units.
**Blast radius:** 4 units; blocks the build before any test runs.
**Forcing fact:** 0.7.6 predates class-file major 53+; JaCoCo added Java 17
support in 0.8.7 and Java 21 in 0.8.9/0.8.11, so `mvn verify` dies in the agent
the moment the build JDK moves (`05-…:270`, `05-…:33-35`). Target ≥ `0.8.11`.
**Verification:** open item 3 (`05-…:590-595`) reproduces the failure on JDK 17
with `-Denforcer.skip=true` in a scratch clone.

## D-09 — flapdoodle replacement

**Class:** Judgment. **Stage:** 1 for the version decision, landing **in the
same commit as the Boot 2.3 bump** for the driver-coupled part.
**Sites:** `auth-service/pom.xml:52-57`, `account-service/pom.xml:72-77`,
`statistics-service/pom.xml:74-79`, `notification-service/pom.xml:68-73`
(`de.flapdoodle.embed.mongo 1.50.3`, a local pin over the BOM's `2.0.3`).
**Site count:** 4 pins; consumers are the `*RepositoryTest` classes in the same
4 units (`AccountRepositoryTest`, `UserRepositoryTest`, `DataPointRepositoryTest`,
`RecipientRepositoryTest`).
**Blast radius:** 4 units; every repository test.
**Forcing fact:** 1.50.3 is built against the Mongo 3.x driver API, the
resolved baseline driver is exactly `3.6.4`, and Boot 2.3.12 moves it to
`4.0.6` (`05-…:36-38`, `05-…:239,245-247`, `05-…:312`).
**Judgment content:** flapdoodle 3.x/4.x versus Testcontainers MongoDB — a
harness choice with different startup cost and CI requirements; the register
names both and prefers neither.

## D-10 — JUnit 4 + Mockito 2.15.0 → JUnit 5

**Class:** Judgment. **Stage:** 1.
**Sites:** 26 test classes import `org.junit.*` across `gateway`, `auth-service`,
`account-service`, `statistics-service`, `notification-service` and (under
`full`) `monitoring` and `turbine-stream-service`; 17 `@RunWith` annotations;
12 classes import `org.mockito.*`.
**Site count:** 26 test classes, 17 runner annotations, 5 units by default (7
with `-Pfull`).
**Blast radius:** cross-unit, test-only source; no production code.
**Forcing fact:** Boot 2.4 removed the JUnit 5 vintage engine from
`spring-boot-starter-test`, so JUnit 4 tests stop running unless the engine is
added back explicitly, and Mockito 2's Byte Buddy cannot instrument JDK 17
class files (`05-…:310-311`, `05-…:39-40`).
**Judgment content:** the four controller tests construct their own
`ObjectMapper` (`account-service/.../AccountControllerTest.java:32` and the
same line in auth/statistics/notification), which interacts with D-17; and
`ExchangeRatesTestServer` is a hand-rolled `com.sun.net.httpserver` stub that a
runner migration invites replacing (D-18). Move the runner, do not rewrite the
assertions.

## D-11 — `javax.validation` → `jakarta.validation`

**Class:** Mechanical. **Stage:** 5.
**Sites:** 13 files / 19 imports, enumerated in `05-…:399-413`
(`account-service` 5, `statistics-service` 4, `notification-service` 3,
`auth-service` 1).
**Site count:** 13 files across 4 units.
**Blast radius:** 4 units; annotation semantics unchanged after the rename.
**Forcing fact:** Boot 3.0.13 manages only `Jakarta Validation 3.0.2`;
`Javax Validation` is absent from its BOM (`05-…:348`, `05-…:388-392`).

## D-12 — `javax.mail` → `jakarta.mail`

**Class:** Judgment. **Stage:** 5.
**Sites:** 4 files / 6 imports (`05-…:421-426`):
`notification-service/.../service/EmailService.java:6`,
`EmailServiceImpl.java:16-17`, `EmailServiceImplTest.java:14-16`,
`NotificationServiceImplTest.java:12`.
**Site count:** 4 files, 1 unit.
**Blast radius:** `notification-service` only.
**Forcing fact:** Boot 3 manages Jakarta Mail `2.1.2` only (`05-…:349`).
**Judgment content:** **not a pure rename** — Jakarta Mail 2.1 changed
`Session`/`Transport` factory behaviour (`05-…:417-419`), and
`EmailServiceImplTest` constructs a `Session` directly.

## D-13 — `WebSecurityConfigurerAdapter` removal

**Class:** Judgment. **Stage:** 4 (deprecation) / 5 (removal).
**Sites:** `auth-service/src/main/java/com/piggymetrics/auth/config/WebSecurityConfig.java`,
`config/src/main/java/com/piggymetrics/config/SecurityConfig.java`.
**Site count:** 2 files, 2 units.
**Blast radius:** 2 units; `config`'s rule set is what makes `/health` return
401 today (`05-…:560`).
**Forcing fact:** deprecated in Spring Security 5.7 and removed in 6.0
(`05-…:515-516`).
**Judgment content:** the lambda-DSL `SecurityFilterChain` rewrite must
reproduce the existing permit/authenticate matrix exactly; it overlaps D-04 in
`auth-service` and should land with it there.

## D-14 — Sleuth → Micrometer Tracing

**Class:** Judgment. **Stage:** 5.
**Sites:** `spring-cloud-starter-sleuth` in `gateway`, `auth-service`,
`account-service`, `statistics-service`, `notification-service` (`05-…:99`).
**Site count:** 5 declarations in 5 units.
**Blast radius:** cross-unit; log-correlation output format.
**Forcing fact:** Sleuth was removed from the 2022.0 train (`05-…:309,344`).
**Judgment content:** MDC field names and log format change, so any log-based
assertion or dashboard changes with it; there is no like-for-like port.

## D-15 — Jackson 2.9.6 → 2.11.4 → 2.13.5 → 2.14.3

**Class:** Judgment. **Stage:** every Boot bump (2, 4, 5).
**Sites:** every REST payload and Mongo mapping; the concrete risk sites are
`statistics-service/.../domain/ExchangeRatesContainer.java:6,12` (a `LocalDate`
field) and `account-service/.../domain/Account.java` `lastSeen`
(`java.util.Date`) (`05-…:513`).
**Site count:** 4 serialization-bearing units; the observed wire strings are
the oracle.
**Blast radius:** the wire contract itself — highest triage priority after the
shared files.
**Forcing fact:** 2.9 → 2.10+ tightens polymorphic-type handling and changes
`java.time`/`Date` serialization defaults (`05-…:513`).
**Recorded oracle (must not drift):** `docs/as-is/03-api-contract-inventory.md:225,231`
and `04-data-model-and-ownership.md:401,403` —
`{"statistics":{"EXPENSES_AMOUNT":0.0330,"INCOMES_AMOUNT":2.2341,"SAVING_AMOUNT":0.6800},"rates":{"EUR":0.92,"JPY":147.85,"RUB":92.5,"USD":1}}`,
scale-4 trailing zeros preserved, `"USD":1` rendered from `BigDecimal.ONE`
without scale, and Jackson's `+0000` date form
(`"lastSeen":"2026-09-08T17:49:44.928+0000"`).

## D-16 — MongoDB driver 3.6.4 → 4.0.6 → 4.6.1 → 4.8.2

**Class:** Judgment. **Stage:** 2 (driver generation), re-verified at 4 and 5.
**Sites:** `spring-boot-starter-data-mongodb` in `auth-service`,
`account-service`, `statistics-service`, `notification-service` (`05-…:100`);
`MongoTemplate`/repository usage in the same units;
`notification-service/.../repository/RecipientRepository.java:16,20`.
**Site count:** 4 units.
**Blast radius:** 4 units, plus stored-document compatibility.
**Forcing fact:** "the single most disruptive transitive change before Boot 3"
— it breaks flapdoodle 1.50.3 and changes `MongoTemplate` behaviour
(`05-…:519`).
**Judgment content:** `BigDecimal` representation. The same
`accounts.expenses.amount` path already holds two BSON types (String from
Spring Data versus seeded doubles — delta D-2 in
`04-data-model-and-ownership.md:380`); changing the representation to
`Decimal128` on upgrade breaks reads of already-stored documents. Do not change
the representation as part of a version bump.

## D-17 — Inert Jackson 1.x `@JsonIgnoreProperties`

**Class:** Judgment. **Stage:** after stage 3, as its own change — explicitly
**not** during stages 1–3.
**Sites:** `account-service/src/main/java/com/piggymetrics/account/domain/Account.java:3`
and `statistics-service/src/main/java/com/piggymetrics/statistics/domain/Account.java:3`
(`import org.codehaus.jackson.annotate.JsonIgnoreProperties`).
**Site count:** 2 imports, 2 units; 4 controller tests are the affected
consumers.
**Blast radius:** 2 units, and the effect is **test-visible only**.
**Forcing fact / trap:** Jackson 2.x does the serialization and never sees the
Jackson 1.x annotation, so it does nothing today. Porting the import is *not*
behaviour-preserving: Boot disables `FAIL_ON_UNKNOWN_PROPERTIES` in the running
services (no change), but the four controller tests build their own
`ObjectMapper` where the flag is enabled, so payloads with unknown properties
that throw today would start deserializing silently (`05-…:194-210`).
**Judgment content:** deleting the import and the annotation preserves
behaviour; porting it is a behaviour change needing its own decision. Confirm
the Codehaus artifacts are gone from the graph after stage 3 first.

## D-18 — `com.sun.security.auth.UserPrincipal` in controller tests

**Class:** Judgment. **Stage:** 3, with the security rework.
**Sites:** `account-service/.../AccountControllerTest.java:7`,
`auth-service/.../UserControllerTest.java:6`,
`notification-service/.../RecipientControllerTest.java:10`,
`statistics-service/.../StatisticsControllerTest.java:13`; plus
`statistics-service/.../client/ExchangeRatesTestServer.java:3-5`
(`com.sun.net.httpserver`, added by stage 0).
**Site count:** 5 files, 4 units.
**Blast radius:** test-only, 4 units.
**Forcing fact:** **not a JDK blocker** — both APIs still exist on JDK 17
(`jdk.security.auth@17.0.19`, `jdk.httpserver@17.0.19`, verified with `javap` —
`05-…:452-458`), and no `sun.*` internal import exists (`05-…:460-461`). The
cost is non-portability and warnings.
**Judgment content:** replace with `spring-security-test`
(`@WithMockUser` / `SecurityMockMvcRequestPostProcessors.user(...)`) during the
security rework, and `ExchangeRatesTestServer` with WireMock/MockWebServer.
JDK 21 availability is still open item 4 (`05-…:597-601`).

## D-19 — Local version pins that defeat BOM management

**Class:** Mechanical. **Stage:** 1.
**Sites:** `com.jayway.jsonpath:json-path 2.2.0` at `auth-service/pom.xml:60`,
`account-service/pom.xml:80`, `statistics-service/pom.xml:82`,
`notification-service/pom.xml:76`; `com.google.guava:guava 19.0` at
`statistics-service/pom.xml:65`.
**Site count:** 5 pins across 4 units.
**Blast radius:** 4 units, test scope for json-path; guava is compile scope in
one unit.
**Forcing fact:** both pins sit below the Boot-managed versions and are already
present at multiple versions in the resolved graph (`05-…:146`, `05-…:145`).
Guava 19.0 is in the affected range of CVE-2018-10237 and CVE-2020-8908
(`05-…:314`).
**Preferred edit:** delete the `<version>` and inherit from the BOM rather than
bumping the pin.

## D-20 — JDK path pins in scripts, CI, and images

**Class:** Mechanical. **Stage:** 4 — all three must change together.
**Sites:** `scripts/demo/start-local.sh:11`, `docs/RUNBOOK.md:12`,
`.github/workflows/build.yml:18-23` (Temurin 8 pin), and the nine Dockerfiles'
base image (`eclipse-temurin:8-jre` → `17-jre`).
**Site count:** 3 toolchain pins + 9 Dockerfiles.
**Blast radius:** every tier; a partial change silently keeps a tier on Java 8.
**Forcing fact:** Boot 3 requires Java 17 (`05-…:315`, `05-…:500-501`). Java 8
EOL is *not* the driver — Temurin 8 is supported to at least Dec 2030; the
deadline comes from the Spring generation.

## D-21 — Container heap and metaspace flags

**Class:** Judgment. **Stage:** 4/5, measured not guessed.
**Sites:** nine Dockerfiles line 5 (`java -Xmx200m -jar …`),
`scripts/demo/start-local.sh:64` (`-Xmx192m -XX:MaxMetaspaceSize=128m`).
**Site count:** 10 files.
**Blast radius:** T1/T2/T3 startup success.
**Forcing fact:** no removed flags are used, but Spring 6/Boot 3 loads more
classes and metaspace exhaustion presents as `OutOfMemoryError: Metaspace` at
startup, not as a heap error (`05-…:495-499`). Open item 6 (`05-…:609-610`) is
measurable only after the first unit reaches Boot 3.

## D-22 — Default time zone and CLDR locale data

**Class:** Judgment. **Stage:** 4 (the 8 → 17 step).
**Sites:** `statistics-service/.../service/StatisticsServiceImpl.java:53-54`
(`LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault())` — the data
point key), `.../domain/ExchangeRatesContainer.java:6,12`,
`.../service/ExchangeRatesServiceImpl.java:15,34` (day-boundary comparison, no
injected `Clock`).
**Site count:** 3 files, 1 unit; the stored `DataPoint` id is the artefact.
**Blast radius:** historical data keys shift by a day if the container default
zone changes; the Dockerfiles set no `TZ` and inherit the image default (UTC).
**Forcing fact:** CLDR locale data became the default in Java 9 (JEP 252),
affecting default-locale date/number formatting including the email templates
(`05-…:478-489`). Open item 5 (`05-…:603-607`) is the `TZ` confirmation
command.

## D-23 — Server-side JavaScript `@Query`

**Class:** Judgment. **Stage:** 2 (driver/server generation), not a JDK item.
**Sites:** `notification-service/.../repository/RecipientRepository.java:16,20`
— `@Query` with `new Date(new Date().setDate(new Date().getDate() - …))`.
**Site count:** 2 queries, 1 unit.
**Blast radius:** the notification scheduling behaviour; evaluated by the
MongoDB **server**, not the JVM.
**Forcing fact:** server-side JavaScript can be disabled and its support has
changed across server generations (mongo 3 → 7); T2 already runs `mongo:7.0`
while T3 is frozen on `mongo:3` (`05-…:483`, `05-…:316`). Needs a runtime check
before the stage that moves the driver.

## D-24 — Inert Feign fallback configuration

**Class:** Judgment. **Stage:** 3, with D-05.
**Sites:** `statistics-service/.../client/ExchangeRatesClient.java:10`
(declares `ExchangeRatesClientFallback`) with **no** `feign.hystrix.enabled`
key for `statistics-service`; the key exists only for `account-service`
(`config/src/main/resources/shared/account-service.yml:24-26`).
**Site count:** 1 client + 1 missing shared-file key.
**Blast radius:** shared-file; `statistics-service` resilience.
**Forcing fact:** `feign-hystrix 9.5.1` *is* on the classpath, so the fallback
is unwired by configuration, not by a missing dependency — the resilience it
appears to provide does not exist (`05-…:164-171`).
**Judgment content:** enabling it during the migration is a **behaviour
change**; the circuit-breaker rework must decide explicitly whether
`statistics-service` gains the fallback or keeps the current behaviour.

## D-25 — Prebuilt upstream images

**Class:** Mechanical. **Stage:** 1.
**Sites:** `docker-compose.yml:16,26,41,60,85,111,136,159,174`
(`sqshq/piggymetrics-*`).
**Site count:** 9 service entries, 1 file.
**Blast radius:** T3 only.
**Forcing fact:** these are upstream images of the original project, not
rebuilt from this fork, so migration work is invisible through them
(`05-…:317`). Build locally or retag.

---

## Dropped candidates

| Candidate | Why it is not a delta |
| --- | --- |
| Eureka client/server replacement | Not EOL — the Eureka modules are the only Netflix components still shipped in Spring Cloud 4.x; only the version moves (`05-…:305`, `05-…:335-336`) |
| `mongo:3` base image | Deliberately frozen: it is built only for the T3 "original" tier, which is the thing the migration is measured against (`05-…:316`) |
| RxJava upgrade | Single resolved version `1.3.8` and it disappears entirely with the Hystrix retirement (`05-…:522`) |
| Reactor upgrade | Not used by application code; moves with Boot (`05-…:523`) |
| Reflective-access remediation (JEP 396/403) | No application-level reflection exists — the scan returns no matches; exposure is entirely in libraries already covered by D-08 and D-10 (`05-…:465-469`) |
| `sun.*` internal API removal | No `sun.*` import exists (`05-…:460-461`) |
| .NET services | Frozen out-of-scope boundary (`docs/OUT-OF-SCOPE-DOTNET.md`) |
| Actuator/health-surface cleanup | Real inconsistency (`auth-service` has no actuator dependency; the other three sit behind a context path) but not forced by any pin; a plan decision, not a delta (`05-…:563`) |

## Statistics

| Measure | Value |
| --- | --- |
| Confirmed deltas | 25 |
| Mechanical / Judgment | 7 / 18 |
| Deltas touching orchestrator-owned shared files | 9 (D-01, D-02, D-03, D-04, D-05, D-06, D-14, D-24, and D-19's BOM inheritance) |
| Units affected by at least one delta | 9 of 9 |
| Deltas whose proof is the T1 wire capture | 3 (D-15, D-16, D-22) |
| Tool-derived deltas | 0 — OpenRewrite did not run |
| Deltas that are deletions rather than migrations | 2 (D-07, and D-17's preferred resolution) |

Most of the tree changes only at stage 5 (`javax` → `jakarta`, 17 files); the
earlier stages are concentrated in POMs, shared configuration, and two units.
That is why this remains an **uplift** and not a cross-stack rewrite
(`.agents/skills/modernization-uplift/SKILL.md:198-200`).
