# As-is: API contract inventory

Reverse-engineered from the branch `devin/1788845133-stage-0-demo-harness` by
reading every `@RestController` in the Java modules, the `@PreAuthorize`
annotations, the OAuth2 configuration in `auth-service`, the shared config YAML
and the Zuul route table. Every claim is derived from the code and cited inline
as `path:line`, **except** the endpoint and payload observations explicitly
marked as such in §1.1, §3 and §4.1–§4.3: those were observed on a running T1
stack on 2026-09-08 at commit `d91f384`, and each carries that attribution
locally. T2 and T3 were never observed; what remains unchecked is listed in
[Open items for runtime verification](#open-items-for-runtime-verification).

## 0. How a browser path becomes a service path

The gateway declares `zuul.ignoredServices: '*'` and four explicit routes, all
with `stripPrefix: false` (`config/src/main/resources/shared/gateway.yml:13-42`).
Each downstream service also sets a servlet context path equal to its route
prefix (`shared/auth-service.yml:10-13`, `shared/account-service.yml:19-22`,
`shared/statistics-service.yml:19-22`, `shared/notification-service.yml:10-13`).
So the browser-visible prefix survives the hop and is then consumed by the
service's context path, and the controller mappings are written **without** the
prefix.

| Route | Target | Resolution |
| --- | --- | --- |
| `/uaa/**` | `http://auth-service:5000` | static URL (`shared/gateway.yml:20-24`); under `local`, `http://${AUTH_HOST:localhost}:5000` (`shared/gateway-local.yml:1-4`) |
| `/accounts/**` | `serviceId: account-service` | Eureka + Ribbon (`shared/gateway.yml:26-30`) |
| `/statistics/**` | `serviceId: statistics-service` | Eureka + Ribbon (`shared/gateway.yml:32-36`) |
| `/notifications/**` | `serviceId: notification-service` | Eureka + Ribbon (`shared/gateway.yml:38-42`) |

`sensitiveHeaders:` is set empty on all four routes, i.e. `Authorization` is
forwarded (`shared/gateway.yml:24,30,36,42`).

## 1. Endpoint inventory

"Service path" is the path relative to the service's own context path (the
context path itself is shown in the header of each block). Authorization is
quoted verbatim from the code.

| Method | Path through the gateway | Path inside the service | Module | Request body | Response body | Authorization |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/uaa/oauth/token` | `/oauth/token` (ctx `/uaa`) | auth-service | `application/x-www-form-urlencoded` (grant params) | OAuth2 token JSON | HTTP Basic client authentication; see §2 (`auth-service/src/main/java/com/piggymetrics/auth/config/OAuth2AuthorizationConfig.java:39-63`) |
| GET/POST | `/uaa/oauth/token_key` | `/oauth/token_key` | auth-service | — | key JSON | `"permitAll()"` (`OAuth2AuthorizationConfig.java:74-79`) |
| POST | `/uaa/oauth/check_token` | `/oauth/check_token` | auth-service | form `token` | token introspection JSON | `"isAuthenticated()"` (`OAuth2AuthorizationConfig.java:74-79`) |
| GET | `/uaa/users/current` | `/users/current` (ctx `/uaa`) | auth-service | — | `java.security.Principal` serialised by Jackson (see §4.5) | any authenticated request — `.authorizeRequests().anyRequest().authenticated()` (`auth-service/src/main/java/com/piggymetrics/auth/config/WebSecurityConfig.java:22-29`); this is the resource servers' `user-info-uri` (`shared/application.yml:20-23`) |
| POST | `/uaa/users` | `/users` (ctx `/uaa`) | auth-service | `com.piggymetrics.auth.domain.User` (`@Valid`) | empty (`void`) | `@PreAuthorize("#oauth2.hasScope('server')")` (`auth-service/src/main/java/com/piggymetrics/auth/controller/UserController.java:27-31`) |
| POST | `/accounts/` | `/` (ctx `/accounts`) | account-service | `com.piggymetrics.account.domain.User` (`@Valid`) | `Account` | **anonymous** — `.antMatchers("/" , "/demo").permitAll()` (`account-service/src/main/java/com/piggymetrics/account/config/ResourceServerConfig.java:55-59`); no `@PreAuthorize` on the method (`account-service/src/main/java/com/piggymetrics/account/controller/AccountController.java:35-38`) |
| GET | `/accounts/{name}` | `/{name}` (ctx `/accounts`) | account-service | — | `Account` | `@PreAuthorize("#oauth2.hasScope('server') or #name.equals('demo')")` (`AccountController.java:19-23`); `/accounts/demo` is additionally permitted anonymously (`ResourceServerConfig.java:55-59`) |
| GET | `/accounts/current` | `/current` (ctx `/accounts`) | account-service | — | `Account` | authenticated (`.anyRequest().authenticated()`, `ResourceServerConfig.java:55-59`); principal-scoped (`AccountController.java:25-28`) |
| PUT | `/accounts/current` | `/current` (ctx `/accounts`) | account-service | `Account` (`@Valid`) | empty (`void`) | authenticated; principal-scoped (`AccountController.java:30-33`) |
| GET | `/statistics/current` | `/current` (ctx `/statistics`) | statistics-service | — | `List<DataPoint>` | authenticated — default `ResourceServerConfigurerAdapter` behaviour; the module overrides no `HttpSecurity` (`statistics-service/src/main/java/com/piggymetrics/statistics/config/ResourceServerConfig.java:15-25`). No `@PreAuthorize` on the method (`statistics-service/src/main/java/com/piggymetrics/statistics/controller/StatisticsController.java:20-23`) |
| GET | `/statistics/{accountName}` | `/{accountName}` (ctx `/statistics`) | statistics-service | — | `List<DataPoint>` | `@PreAuthorize("#oauth2.hasScope('server') or #accountName.equals('demo')")` (`StatisticsController.java:25-29`) — **on top of** the resource server's authenticated-by-default rule, unlike account-service there is no `permitAll()` for `/demo` |
| PUT | `/statistics/{accountName}` | `/{accountName}` (ctx `/statistics`) | statistics-service | `com.piggymetrics.statistics.domain.Account` (`@Valid`) | empty (`void`) | `@PreAuthorize("#oauth2.hasScope('server')")` (`StatisticsController.java:31-35`) |
| GET | `/notifications/recipients/current` | `/recipients/current` (ctx `/notifications`) | notification-service | — | `Recipient` (declared `Object`) | authenticated — no `HttpSecurity` override (`notification-service/src/main/java/com/piggymetrics/notification/config/ResourceServerConfig.java:17-34`); principal-scoped (`notification-service/src/main/java/com/piggymetrics/notification/controller/RecipientController.java:21-24`) |
| PUT | `/notifications/recipients/current` | `/recipients/current` (ctx `/notifications`) | notification-service | `Recipient` (`@Valid`) | `Recipient` (declared `Object`) | authenticated; principal-scoped (`RecipientController.java:26-29`) |
| GET | `/`, `/index.html`, `/css/**`, `/js/**`, `/images/**`, `/fonts/**` | served by the gateway itself | gateway | — | static assets (`gateway/src/main/resources/static/`) | **anonymous** — the gateway has no security dependency (`gateway/pom.xml:19-43`); exercised by `scripts/demo/smoke.sh:64-66` |

### 1.1 Internal (service-to-service) calls, not reachable through the gateway as such

| Caller | Call | Declared in | Auth |
| --- | --- | --- | --- |
| account-service | `POST /uaa/users` (Feign, `name = "auth-service"`) | `account-service/src/main/java/com/piggymetrics/account/client/AuthServiceClient.java:9-14` | `client_credentials`, scope `server`, injected by `OAuth2FeignRequestInterceptor` (`account-service/.../config/ResourceServerConfig.java:39-42`) |
| account-service | `PUT /statistics/{accountName}` (Feign, `name = "statistics-service"`, `fallback = StatisticsServiceClientFallback.class`) | `account-service/src/main/java/com/piggymetrics/account/client/StatisticsServiceClient.java:10-16` | same; `feign.hystrix.enabled: true` is set for this module (`shared/account-service.yml:24-26`) |
| notification-service | `GET /accounts/{accountName}` (Feign, `name = "account-service"`) | `notification-service/src/main/java/com/piggymetrics/notification/client/AccountServiceClient.java:9-14` | same pattern (`notification-service/.../config/ResourceServerConfig.java:20-28`) |
| statistics-service | `GET /latest?base=USD` against `${rates.url}` (Feign, `name = "rates-client"`, `fallback = ExchangeRatesClientFallback.class`) | `statistics-service/src/main/java/com/piggymetrics/statistics/client/ExchangeRatesClient.java:10-14` | none (external API / local stub). **`feign.hystrix.enabled` is never set for this module** (absent from `shared/statistics-service.yml` and `shared/statistics-service-local.yml`), and the module declares `spring-cloud-netflix-hystrix-stream` but not `spring-cloud-starter-netflix-hystrix` (`statistics-service/pom.xml:53-63`), so the declared fallback is inert: `/hystrix.stream` returned 404 on every T1 port and no Hystrix stream exists — see §3; the remaining open question is item 1 |

The `/hystrix.stream` result above was observed on the T1 tier, 2026-09-08,
commit `d91f384`; T2/T3 were not covered.

## 2. OAuth2 token endpoints, clients and grants

Authorization server: `auth-service`, `@EnableAuthorizationServer`
(`auth-service/src/main/java/com/piggymetrics/auth/config/OAuth2AuthorizationConfig.java:21-23`),
in-memory client registry and in-memory token store
(`OAuth2AuthorizationConfig.java:25,44`). Tokens are opaque (no JWT converter
is configured), which is why every resource server has to call
`/uaa/users/current` to validate them.

| Client id | Grant types | Scopes | Secret |
| --- | --- | --- | --- |
| `browser` | `refresh_token`, `password` | `ui` | none configured at all (`OAuth2AuthorizationConfig.java:45-47`); `scripts/demo/smoke.sh:34-40` authenticates with `-u 'browser:'`, i.e. an empty secret |
| `account-service` | `client_credentials`, `refresh_token` | `server` | `env.getProperty("ACCOUNT_SERVICE_PASSWORD")` — `<redacted, see .env>` (`OAuth2AuthorizationConfig.java:49-52`) |
| `statistics-service` | `client_credentials`, `refresh_token` | `server` | `env.getProperty("STATISTICS_SERVICE_PASSWORD")` — `<redacted, see .env>` (`OAuth2AuthorizationConfig.java:54-57`) |
| `notification-service` | `client_credentials`, `refresh_token` | `server` | `env.getProperty("NOTIFICATION_SERVICE_PASSWORD")` — `<redacted, see .env>` (`OAuth2AuthorizationConfig.java:59-62`) |

Client secrets are compared with `NoOpPasswordEncoder`
(`OAuth2AuthorizationConfig.java:74-79`), i.e. stored and matched in plain
text; the class also declares an unused `NOOP_PASSWORD_ENCODE = "{noop}"`
constant (`OAuth2AuthorizationConfig.java:26`). End-user passwords, by
contrast, are BCrypt-hashed
(`auth-service/src/main/java/com/piggymetrics/auth/config/WebSecurityConfig.java:32-36`).

Password-grant call as used by the UI and the smoke test
(`gateway/src/main/resources/static/js/launch.js:17`, `scripts/demo/smoke.sh:34-40`):

```
POST /uaa/oauth/token
Authorization: Basic base64("browser:")
grant_type=password&scope=ui&username=<user>&password=<redacted, see .env>
```

Client-credentials call made by each service through
`ClientCredentialsResourceDetails` bound to `security.oauth2.client`
(`shared/account-service.yml:1-8`), against
`accessTokenUri: http://auth-service:5000/uaa/oauth/token`
(`http://${AUTH_HOST:localhost}:5000/uaa/oauth/token` under `local`,
`shared/account-service-local.yml:1-4`), with
`clientSecret: ${ACCOUNT_SERVICE_PASSWORD}` = `<redacted, see .env>`.

Only `password`, `refresh_token` and `client_credentials` are configured; no
client declares `authorization_code` or `implicit`, so the
`/uaa/oauth/authorize` endpoint that Spring Security OAuth2 registers by
default cannot be completed by any configured client.

Secret **names** (values intentionally omitted, see `.env`):
`CONFIG_SERVICE_PASSWORD`, `NOTIFICATION_SERVICE_PASSWORD`,
`STATISTICS_SERVICE_PASSWORD`, `ACCOUNT_SERVICE_PASSWORD`, `MONGODB_PASSWORD`.
The one credential pair that is **not** externalised is the SMTP login in
`shared/notification-service.yml:36-40`.

## 3. Actuator, Hystrix-stream and Eureka endpoints

| Endpoint | Where | Exposure claim | Authentication |
| --- | --- | --- | --- |
| `/actuator/**` on `config` (`:8888`) | config server | explicitly permitted (`config/src/main/java/com/piggymetrics/config/SecurityConfig.java:14-22`); `config/Dockerfile:7` health-checks `/actuator/health`; observed **200** for `/actuator/health` in T1 | **unauthenticated** — `GET /health` returned **401** in T1; everything else on 8888 requires HTTP Basic |
| `/<application>/<profile>` on `config` (`:8888`) | config server | e.g. `/account-service/local`, polled by `scripts/demo/start-local.sh:80-84` | HTTP Basic `user` / `<redacted, see .env>` |
| Eureka dashboard `/` and REST `/eureka/apps/**` (`:8761`) | registry | published to the host in T3 (`docker-compose.yml:31-32`) and T2 (`docker-compose.core.yml:41-42`); queried without credentials by `scripts/demo/start-local.sh:90-97`; observed **200 unauthenticated** for `/` in T1 | **unauthenticated** — `registry/pom.xml:19-31` declares no security starter |
| Hystrix dashboard `/hystrix` (`monitoring`, host `:9000`) | monitoring | `@EnableHystrixDashboard` (`monitoring/src/main/java/com/piggymetrics/monitoring/MonitoringApplication.java:7-9`) | **unauthenticated** (`monitoring/pom.xml:19-30`) |
| Turbine stream `/turbine.stream` (`turbine-stream-service`, host `:8989`) | turbine | `@EnableTurbineStream` (`turbine-stream-service/src/main/java/com/piggymetrics/turbine/TurbineStreamServiceApplication.java:8-10`) | **unauthenticated** (`turbine-stream-service/pom.xml:20-38`) |
| RabbitMQ management UI (host `:15672`) | rabbitmq | `docker-compose.yml:3-11` | broker defaults; not configured in this repo |
| `spring-boot-starter-actuator` on `account-service`, `statistics-service`, `notification-service` | those modules | declared (`account-service/pom.xml:53`, `statistics-service/pom.xml:53`, `notification-service/pom.xml:53`) but **no `management.endpoints.web.exposure.include` anywhere in the repository** (verified by searching all YAML), although Spring Boot 2.0's default web exposure would ordinarily be (`health`, `info`); `/actuator/health` returned **404 on 5000, 6000, 7000 and 8000** in T1, so the declared actuator starter exposes nothing reachable there | these modules are resource servers whose default rule is "authenticated" |
| Hystrix metrics stream on `account/statistics/notification-service` | those modules | `spring-cloud-netflix-hystrix-stream` is on the classpath (e.g. `account-service/pom.xml:65`), which publishes metrics over **RabbitMQ**, not over an HTTP `/hystrix.stream` endpoint; `/hystrix.stream` returned **404 on 4000, 5000, 6000, 7000 and 8000** in T1, confirming there is no HTTP Hystrix stream at all | n/a — no broker exists in T1 or T2 |

`gateway` and `registry` declare no actuator starter
(`gateway/pom.xml:19-43`, `registry/pom.xml:19-31`), but `/actuator/health`
returned **200 unauthenticated** on both 4000 and 8761 in T1. Actuator therefore
reaches both services transitively and is additional unauthenticated surface on
two published ports; `scripts/demo/start-local.sh:110-118` still uses
`GET /accounts/demo` plus Eureka status as its readiness probe rather than an
actuator endpoint.

The endpoint observations in this table and paragraph were observed on the T1
tier, 2026-09-08, commit `d91f384`; T2/T3 were not covered.

## 4. Response payload types

All money amounts are `java.math.BigDecimal`. Jackson serialises `BigDecimal`
as an unquoted JSON number preserving the value's scale, so scale drift on
migration is directly wire-visible.

The runtime payload observations in §4.1–§4.3 were observed on the T1 tier,
2026-09-08, commit `d91f384`; T2/T3 were not covered.

### 4.1 `Account` (account-service) — `account-service/src/main/java/com/piggymetrics/account/domain/Account.java:15-33`

| Field | Java type | Notes |
| --- | --- | --- |
| `name` | `String` | `@Id`, the account/username |
| `lastSeen` | `java.util.Date` | observed as `"2026-09-08T17:49:44.928+0000"` in T1, not epoch millis |
| `incomes` | `List<Item>` | |
| `expenses` | `List<Item>` | |
| `saving` | `Saving` | `@NotNull` |
| `note` | `String` | `@Length(min = 0, max = 20_000)` |

`Item` (`account-service/.../domain/Item.java:8-24`): `title` `String`,
**`amount` `BigDecimal`**, `currency` `Currency`, `period` `TimePeriod`,
`icon` `String`.

`Saving` (`account-service/.../domain/Saving.java:6-21`): **`amount`
`BigDecimal`**, `currency` `Currency`, **`interest` `BigDecimal`**, `deposit`
`Boolean`, `capitalization` `Boolean`.

`Currency` = `USD, EUR, RUB, JPY`, default `USD`
(`account-service/.../domain/Currency.java:3-9`).
`TimePeriod` = `YEAR, QUARTER, MONTH, DAY, HOUR`
(`account-service/.../domain/TimePeriod.java:3-7`).

### 4.2 `DataPoint` (statistics-service) — `statistics-service/src/main/java/com/piggymetrics/statistics/domain/timeseries/DataPoint.java:16-27`

| Field | Java type | Notes |
| --- | --- | --- |
| `id` | `DataPointId` | `{ account: String, date: java.util.Date }` (`.../timeseries/DataPointId.java:6-25`); observed serialising as a nested JSON object, with `date` as `"2026-09-08T00:00:00.000+0000"` |
| `incomes` | `Set<ItemMetric>` | |
| `expenses` | `Set<ItemMetric>` | |
| `statistics` | `Map<StatisticMetric, BigDecimal>` | keys `INCOMES_AMOUNT`, `EXPENSES_AMOUNT`, `SAVING_AMOUNT` (`.../timeseries/StatisticMetric.java:3-6`) |
| `rates` | `Map<Currency, BigDecimal>` | keys `USD`, `EUR`, `RUB`, `JPY` |

`ItemMetric` (`.../timeseries/ItemMetric.java:12-29`): `title` `String`,
**`amount` `BigDecimal`** (getter-only, so serialised but not deserialised via
setters).

### 4.3 BigDecimal scale: arithmetic derivation and observed wire format

* `ItemMetric.amount` = `convert(...) / period.baseRatio` with
  `divide(..., 4, RoundingMode.HALF_UP)`
  (`statistics-service/src/main/java/com/piggymetrics/statistics/service/StatisticsServiceImpl.java:103-109`)
  → **scale 4**.
* `convert()` computes `rates.get(to).divide(rates.get(from), 4, HALF_UP)` and
  then `amount.multiply(ratio)`
  (`statistics-service/.../service/ExchangeRatesServiceImpl.java:51-59`), so the
  product's scale is `amount.scale() + 4` — i.e. `SAVING_AMOUNT` inherits the
  caller's input scale plus 4, unlike the income/expense metrics which are
  re-divided to exactly 4.
* `INCOMES_AMOUNT` / `EXPENSES_AMOUNT` are sums of scale-4 values
  (`StatisticsServiceImpl.java:84-90`) → scale 4.
* `rates.USD` is `BigDecimal.ONE` → **scale 0**, while the other rates keep
  whatever scale the upstream JSON carried (the stub emits `0.92`, `92.5`,
  `147.85`, `1` — `scripts/demo/rates-stub.py:17-25`), so a single response
  mixes scales, e.g. `"USD":1` next to `"JPY":147.85`.
* `TimePeriod.getBaseRatio()` returns `new BigDecimal(double)`
  (`statistics-service/.../domain/TimePeriod.java:7-17`), i.e. the exact binary
  expansion of e.g. `30.4368`, not `BigDecimal.valueOf`. Any change of Java
  version or of this constructor changes the divisor and therefore the last
  digits of every metric.

These are the *derived* scales. The following is one observed T1 sample, not an
exhaustive contract; it is the baseline any Boot 3 / Jackson upgrade must be
diffed against.

#### Actual wire text

`GET /statistics/current` (T1):

```json
[{"id":{"account":"wire1788889784","date":"2026-09-08T00:00:00.000+0000"},"incomes":[{"title":"Salary","amount":2.2341}],"expenses":[{"title":"Tokyo","amount":0.0330}],"statistics":{"EXPENSES_AMOUNT":0.0330,"INCOMES_AMOUNT":2.2341,"SAVING_AMOUNT":0.6800},"rates":{"EUR":0.92,"JPY":147.85,"RUB":92.5,"USD":1}}]
```

`GET /accounts/current` (T1):

```json
{"name":"wire1788889784","lastSeen":"2026-09-08T17:49:44.928+0000","incomes":[{"title":"Salary","amount":10000,"currency":"JPY","period":"MONTH","icon":"wallet"}],"expenses":[{"title":"Tokyo","amount":147.85,"currency":"JPY","period":"MONTH","icon":"travel"}],"saving":{"amount":100,"currency":"JPY","interest":3.32,"deposit":true,"capitalization":false},"note":"wire"}
```

These samples confirm four wire-format facts:

1. `BigDecimal` scale is preserved literally including trailing zeros —
   `0.0330` and `0.6800` are emitted with scale 4, matching the derived scale
   above, and `SAVING_AMOUNT` came back as `0.6800` (scale 4) for this input.
2. A single `rates` map mixes scales: `"USD":1` (scale 0, from
   `BigDecimal.ONE`) alongside `"JPY":147.85` — so any consumer doing exact
   string or scale comparison sees heterogeneous output within one object.
3. Amounts are unquoted JSON numbers, so a client parsing into a binary float
   loses the exact decimal (`147.85`, `2.2341`).
4. `Date` uses the `+0000` offset form; the composite `id` is a nested object.

This is one observed sample on T1, covering only these two endpoints and this
one input, not an exhaustive contract.

### 4.4 `Recipient` (notification-service) — `notification-service/src/main/java/com/piggymetrics/notification/domain/Recipient.java:12-22`

| Field | Java type | Notes |
| --- | --- | --- |
| `accountName` | `String` | `@Id` |
| `email` | `String` | `@NotNull @Email` |
| `scheduledNotifications` | `Map<NotificationType, NotificationSettings>` | keys `BACKUP`, `REMIND` (`.../domain/NotificationType.java:3-6`) |

`NotificationSettings` (`.../domain/NotificationSettings.java:6-14`): `active`
`Boolean`, `frequency` `Frequency` (`WEEKLY`, `MONTHLY`, `QUARTERLY` —
`.../domain/Frequency.java:5-7`), `lastNotified` `java.util.Date`. `Frequency`
is persisted as its `days` int via custom converters
(`.../repository/converter/FrequencyWriterConverter.java`) but serialised to
JSON as the enum name.

### 4.5 `Principal` (auth-service `GET /uaa/users/current`)

The controller returns the raw `java.security.Principal`
(`auth-service/.../controller/UserController.java:22-25`), i.e. whatever
`OAuth2Authentication` object Jackson can walk — there is no DTO. The resource
servers consume it through `CustomUserInfoTokenServices`, which reads the
`user_name`/principal key out of the returned map (e.g.
`account-service/src/main/java/com/piggymetrics/account/service/security/CustomUserInfoTokenServices.java`).
The concrete JSON shape is version-dependent on `spring-security-oauth2` and is
therefore a migration risk in its own right; its exact fields remain unchecked
and are listed as open item 2.

### 4.6 Request bodies that are not response types

* `com.piggymetrics.account.domain.User` (`POST /accounts/`):
  `username` `String` `@Length(min = 3, max = 20)`, `password` `String`
  `@Length(min = 6, max = 40)` (`account-service/.../domain/User.java:7-15`).
* `com.piggymetrics.auth.domain.User` (`POST /uaa/users`): `username`,
  `password` (`auth-service/.../domain/User.java:11-16`); it implements
  `UserDetails` and `getAuthorities()` returns `null`
  (`auth-service/.../domain/User.java:28-31`).
* `com.piggymetrics.statistics.domain.Account` (`PUT /statistics/{accountName}`)
  is a *different* class from the account-service `Account`: only `incomes`,
  `expenses`, `saving`, all `@NotNull`
  (`statistics-service/.../domain/Account.java:12-24`), and its `Item` has no
  `icon` field (`statistics-service/.../domain/Item.java:8-21`). Both `Account`
  classes are annotated with the **Codehaus** `@JsonIgnoreProperties`
  (`org.codehaus.jackson.annotate`), not the Jackson 2 annotation
  (`account-service/.../domain/Account.java:3`,
  `statistics-service/.../domain/Account.java:3`), so the annotation is inert
  for Jackson 2 databind.

## Open items for runtime verification

The T1 observations in §1.1, §3, and §4.3 now cover actuator reachability, the
Hystrix stream, the Eureka dashboard, the observed wire formats, and the
fallback wiring. The remainder is still open; T2/T3 were not covered. Each
item below is a concrete check.

1. **Statistics behavior when the rates stub is unreachable.** The T1
   observation in §1.1 confirms that `ExchangeRatesClientFallback` is inert.
   Stop the
   rates stub (`kill` the PID in `.demo-runtime/pids/rates-stub.pid`), then
   `PUT /accounts/current` and determine whether `statistics-service` returns
   HTTP 500 or throws a `NullPointerException` from
   `container.getRates().get("EUR")` in `ExchangeRatesServiceImpl.java:39-44`,
   given that no circuit breaker is enabled for this module.
2. **Exact JSON of `GET /uaa/users/current`.** `curl -s -H "Authorization: Bearer $TOKEN" http://localhost:4000/uaa/users/current | jq .`
   and record the field names; this payload is the contract every resource
   server depends on and it is produced by library code, not by this repo.
3. **`/statistics/demo` reachability.** `scripts/demo/smoke.sh:63` calls it
   *with* a bearer token; confirm that an anonymous
   `curl -i http://localhost:4000/statistics/demo` is rejected (401), which
   would confirm the asymmetry with `/accounts/demo` described in §1.
4. **`POST /accounts/` duplicate-name behaviour.** `AccountServiceImpl.create`
   raises `IllegalArgumentException` via `Assert.isNull`
   (`account-service/.../service/AccountServiceImpl.java:46-51`) and
   `ErrorHandler` maps that to HTTP 400
   (`account-service/.../controller/ErrorHandler.java:17-21`); the UI relies on
   the 400 (`gateway/src/main/resources/static/js/login.js`). Verify the status
   code is still 400 and not 500 after the migration.
5. **Whether `/uaa/oauth/authorize`, `/oauth/error`, `/oauth/confirm_access`
   are actually registered.** `curl -i http://localhost:4000/uaa/oauth/authorize`
   in T1 and record the status; §2 infers their existence from
   `@EnableAuthorizationServer` defaults, not from observation.
6. **Refresh-token grant.** Every service client declares `refresh_token`
   alongside `client_credentials`, which the spec does not allow to issue
   refresh tokens. Verify with
   `curl -u account-service:<redacted, see .env> -d grant_type=client_credentials http://localhost:4000/uaa/oauth/token`
   whether a `refresh_token` is returned; the answer determines whether the
   migration target must reproduce that behaviour.
