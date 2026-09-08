# As-is: data model, ownership and data-layer migration risks

Reverse-engineered from branch `devin/1788845133-stage-0-demo-harness`. Scope: the
Java services (`auth-service`, `account-service`, `statistics-service`,
`notification-service`). The three .NET Core services are out of scope; they use
their own collections (`compliance_rules`, `audit_logs`, `compliance_reports`,
`fraud_rules`, `fraud_alerts`, `analyzed_transactions` —
`compliance-service/Repository/ComplianceRuleRepository.cs:23`,
`fraud-detection-service/Services/FraudDetectionServiceImpl.cs:27-28`) and are not
covered further.

Every claim below cites the file it was read from. Nothing here was confirmed
against a running MongoDB; anything that needs a live instance is in
[Open items for runtime verification](#open-items-for-runtime-verification).

## 0. Database naming: two coexisting schemes

There are two naming schemes, selected by the active Spring profile.

| Tier | Compose/harness | Profile | Mongo topology | Database names |
| --- | --- | --- | --- | --- |
| T1 bare JVMs | `scripts/demo/start-local.sh:47-54` | `local` (`start-local.sh:72`) | one `mongod` on `127.0.0.1:27017` | `piggymetrics_auth`, `piggymetrics_accounts`, `piggymetrics_statistics`, `piggymetrics_notifications` |
| T2 core Compose | `docker-compose.core.yml` | `local` (e.g. `docker-compose.core.yml:49`, `:66`, `:85`, `:106`) | one `mongo:7.0` container (`docker-compose.core.yml:2-14`) | same four `piggymetrics_*` databases |
| T3 legacy full stack | `docker-compose.yml` | not set → base YAML applies | four containers: `auth-mongodb`, `account-mongodb`, `statistics-mongodb`, `notification-mongodb` (`docker-compose.yml:70`, `:95`, `:121`, `:146`) | all four services use database `piggymetrics` |

Base configuration (T3): `config/src/main/resources/shared/auth-service.yml:3-8`,
`account-service.yml:12-17`, `statistics-service.yml:12-17`,
`notification-service.yml:28-35` — each pins `host: <service>-mongodb`,
`database: piggymetrics`, `username: user`, `password: ${MONGODB_PASSWORD}`
(`<redacted, see .env>`).

Profile overlay (T1/T2): `auth-service-local.yml:1-5`,
`account-service-local.yml:6-10`, `statistics-service-local.yml:6-10`,
`notification-service-local.yml:6-10` — of the Mongo settings each overrides
only `host: ${MONGO_HOST:localhost}` and `database: piggymetrics_<tier>` (the
same files also override unrelated keys: `accessTokenUri` in
`account-service-local.yml:1-4`, `statistics-service-local.yml:1-4`,
`notification-service-local.yml:1-4`, and `rates.url` in
`statistics-service-local.yml:12-13`). **They do not restate
`username`/`password`**, so those values are inherited from the base file of the
same service; the authentication database therefore follows the overridden
`database` value (framework merge behaviour — see runtime item 4). The four per-database `user` accounts are created
by `mongodb/init/01-users.js:1-19` (T2) and `scripts/demo/seed-local.js:1-19`
(T1), each with `readWrite` on its own database only.

**The four-database split is a profile-only property.** Nothing in the Java code
or in the base YAML expresses it: drop `SPRING_PROFILES_ACTIVE=local` while
pointing at the single consolidated MongoDB and all four services fall back to
`database: piggymetrics` on one server — see section 6, risk D-1.

The legacy MongoDB image also contradicts the split: `mongodb/init.sh:12` grants
`readWrite` on `piggymetrics` only, and `mongodb/init.sh:23` loads the dump with
`mongo piggymetrics $auth $INIT_DUMP`, i.e. into `piggymetrics`, never into
`piggymetrics_accounts`.

## 1. Ownership matrix

| Collection | Logical DB (T1/T2) | DB (T3 legacy) | Owning service | `@Document` class | Writers | Readers |
| --- | --- | --- | --- | --- | --- | --- |
| `accounts` | `piggymetrics_accounts` | `piggymetrics` (on `account-mongodb`) | account-service | `com.piggymetrics.account.domain.Account` (`account-service/src/main/java/com/piggymetrics/account/domain/Account.java:13`) | account-service (`AccountServiceImpl.java:65`, `:86`); **seed scripts** (`mongodb/dump/account-service-dump.js:7-96`) | account-service (`AccountRepository.java:10`) |
| `users` | `piggymetrics_auth` | `piggymetrics` (on `auth-mongodb`) | auth-service | `com.piggymetrics.auth.domain.User` (`auth-service/src/main/java/com/piggymetrics/auth/domain/User.java:10`) | auth-service (`UserServiceImpl.java:33`) | auth-service (`UserRepository.java:8`, `MongoUserDetailsService`) |
| `datapoints` | `piggymetrics_statistics` | `piggymetrics` (on `statistics-mongodb`) | statistics-service | `com.piggymetrics.statistics.domain.timeseries.DataPoint` (`statistics-service/src/main/java/com/piggymetrics/statistics/domain/timeseries/DataPoint.java:15`) | statistics-service (`StatisticsServiceImpl.java:77`) | statistics-service (`DataPointRepository.java:13`) |
| `recipients` | `piggymetrics_notifications` | `piggymetrics` (on `notification-mongodb`) | notification-service | `com.piggymetrics.notification.domain.Recipient` (`notification-service/src/main/java/com/piggymetrics/notification/domain/Recipient.java:11`) | notification-service (`RecipientServiceImpl.java:43`, `:71`) | notification-service (`RecipientRepository.java:13-21`) |
| `accounts` (second mapping) | `piggymetrics_statistics` | `piggymetrics` (on `statistics-mongodb`) | — (no repository) | `com.piggymetrics.statistics.domain.Account` (`statistics-service/src/main/java/com/piggymetrics/statistics/domain/Account.java:10`) | none found | none found |

### Migration hazards visible in this matrix

1. **`accounts` has two writers.** account-service writes it through
   `AccountRepository` (`AccountServiceImpl.java:65`, `:86`) and the seed/demo
   path writes the same `_id: "demo"` document directly with a `replaceOne`
   upsert (`mongodb/dump/account-service-dump.js:7-96`). The seed path bypasses
   every Java-side mapping and validation rule, so field types written by the two
   writers need not agree (section 6, risk D-2).

2. **`accounts` is claimed by two `@Document` classes in two services.**
   `account-service/.../domain/Account.java:13` and
   `statistics-service/.../domain/Account.java:10` both declare
   `@Document(collection = "accounts")`. statistics-service has no repository or
   `MongoTemplate` usage for it — it is only a Feign request body
   (`StatisticsController.java:33`) — so today it never touches Mongo. The
   annotation makes it a single `save()` away from writing account-service's
   collection, and if the database split collapses (section 0) both classes
   resolve to the same physical collection while the statistics variant has no
   `@Id`, no `name`, no `lastSeen` and no `note`
   (`statistics-service/.../domain/Account.java:12-24`).

3. The `accounts` collection is also the payload notification-service pulls for
   backup emails, but over HTTP, not from the database (section 4).

## 2. Per-collection schema

MongoDB representations below are derived from the Java types and the default
Spring Data MongoDB 2.0 mapping (`pom.xml` inherits Spring Boot
2.0.3.RELEASE). `BigDecimal` representation in particular is marked for runtime
verification.

### 2.1 `accounts` — `com.piggymetrics.account.domain.Account`

Source: `account-service/src/main/java/com/piggymetrics/account/domain/Account.java:13-33`.

| Field | Java type | MongoDB representation | Nullable/optional | Key / index |
| --- | --- | --- | --- | --- |
| `name` | `String` | `_id` (String) | no — it is the id | `@Id` (`Account.java:17-18`); implicit unique `_id` index |
| `lastSeen` | `java.util.Date` | BSON `date` | optional (no `@NotNull`) | none |
| `incomes` | `List<Item>` | array of embedded docs | optional; `@Valid` only (`Account.java:22-23`) | none |
| `expenses` | `List<Item>` | array of embedded docs | optional; `@Valid` only (`Account.java:25-26`) | none |
| `saving` | `Saving` | embedded doc | `@NotNull` (`Account.java:28-30`) | none |
| `note` | `String` | string | optional; `@Length(min = 0, max = 20_000)` (`Account.java:32-33`) | none |
| `_class` | — | string, written by Spring Data type mapper | — | see risk D-6 |

Nested type `Item` (`account-service/.../domain/Item.java:8-24`):

| Field | Java type | MongoDB representation | Nullable | Notes |
| --- | --- | --- | --- | --- |
| `title` | `String` | string | `@NotNull`, `@Length(min = 1, max = 20)` (`Item.java:10-12`) | |
| `amount` | `BigDecimal` | see risk D-2 (String vs Decimal128 vs the double written by the seed) | `@NotNull` (`Item.java:14-15`) | no scale/rounding applied on write |
| `currency` | `Currency` enum | string enum name | `@NotNull` (`Item.java:17-18`) | values `USD, EUR, RUB, JPY` (`Currency.java:5`) |
| `period` | `TimePeriod` enum | string enum name | `@NotNull` (`Item.java:20-21`) | values `YEAR, QUARTER, MONTH, DAY, HOUR` (`TimePeriod.java:5`) |
| `icon` | `String` | string | `@NotNull` (`Item.java:23-24`) | present in account-service only, absent from the statistics `Item` |

Nested type `Saving` (`account-service/.../domain/Saving.java:6-21`):

| Field | Java type | MongoDB representation | Nullable | Notes |
| --- | --- | --- | --- | --- |
| `amount` | `BigDecimal` | see risk D-2 | `@NotNull` (`Saving.java:8-9`) | defaulted to `new BigDecimal(0)` on account creation (`AccountServiceImpl.java:54`) |
| `currency` | `Currency` | string enum name | `@NotNull` (`Saving.java:11-12`) | defaults to `Currency.getDefault()` = `USD` (`Currency.java:7-9`, `AccountServiceImpl.java:55`) |
| `interest` | `BigDecimal` | see risk D-2 | `@NotNull` (`Saving.java:14-15`) | defaulted to `new BigDecimal(0)` (`AccountServiceImpl.java:56`) |
| `deposit` | `Boolean` | bool | `@NotNull` (`Saving.java:17-18`) | |
| `capitalization` | `Boolean` | bool | `@NotNull` (`Saving.java:20-21`) | |

No monetary rounding happens in account-service: amounts are persisted exactly as
received (`AccountServiceImpl.java:81-86`).

### 2.2 `users` — `com.piggymetrics.auth.domain.User`

Source: `auth-service/src/main/java/com/piggymetrics/auth/domain/User.java:10-16`.

| Field | Java type | MongoDB representation | Nullable | Key / index |
| --- | --- | --- | --- | --- |
| `username` | `String` | `_id` (String) | no | `@Id` (`User.java:13-14`) |
| `password` | `String` | string (BCrypt hash) | no in practice — set from `encoder.encode(...)` (`UserServiceImpl.java:30-31`) | none |
| `_class` | — | string | — | see risk D-6 |

`UserDetails` accessors (`isEnabled`, `isAccountNonExpired`, …) are hard-coded
`true` (`User.java:41-59`) and `getAuthorities()` returns `null`
(`User.java:28-31`); none of them is a persisted field.

The request-side `com.piggymetrics.account.domain.User`
(`account-service/.../domain/User.java:7-15`) is **not** a `@Document`: it is the
Feign payload used to create the auth user, and carries the plaintext password
with `@Length(min = 6, max = 40)`.

### 2.3 `datapoints` — `com.piggymetrics.statistics.domain.timeseries.DataPoint`

Source: `statistics-service/src/main/java/com/piggymetrics/statistics/domain/timeseries/DataPoint.java:15-27`.

| Field | Java type | MongoDB representation | Nullable | Key / index |
| --- | --- | --- | --- | --- |
| `id` | `DataPointId` | embedded doc as `_id` | no | `@Id` (`DataPoint.java:18-19`), compound natural key |
| `incomes` | `Set<ItemMetric>` | array of embedded docs | optional | none |
| `expenses` | `Set<ItemMetric>` | array of embedded docs | optional | none |
| `statistics` | `Map<StatisticMetric, BigDecimal>` | embedded doc, keys = enum names `INCOMES_AMOUNT`, `EXPENSES_AMOUNT`, `SAVING_AMOUNT` (`StatisticMetric.java:5`) | optional | see risk D-4 |
| `rates` | `Map<Currency, BigDecimal>` | embedded doc, keys = enum names `USD`, `EUR`, `RUB`, `JPY` (`statistics-service/.../domain/Currency.java:5`) | optional | see risk D-4 |
| `_class` | — | string | — | see risk D-6 |

Nested `DataPointId` (`.../timeseries/DataPointId.java:6-17`):

| Field | Java type | MongoDB representation | Nullable | Notes |
| --- | --- | --- | --- | --- |
| `account` | `String` | string inside `_id` | set via constructor only (no setter) | queried by `findByIdAccount` (`DataPointRepository.java:13`) → `_id.account` |
| `date` | `java.util.Date` | BSON `date` inside `_id` | set via constructor only | truncated to start of day, see below |

Explicit converters write and read this composite id as a two-field
`BasicDBObject`, with `date` first and `account` second:
`DataPointIdWriterConverter.java:17-22` (`object.put("date", id.getDate()); object.put("account", id.getAccount());`)
and `DataPointIdReaderConverter.java:16-19`. Field order in a BSON subdocument is
significant for `_id` equality, so the writer's ordering is load-bearing.

Nested `ItemMetric` (`.../timeseries/ItemMetric.java:12-20`):

| Field | Java type | MongoDB representation | Nullable | Notes |
| --- | --- | --- | --- | --- |
| `title` | `String` | string | constructor-only | `equals`/`hashCode` use `title` case-insensitively for equality but a case-sensitive `hashCode` (`ItemMetric.java:31-45`) — the `Set` de-duplication is therefore inconsistent for titles differing only in case |
| `amount` | `BigDecimal` | see risk D-2 | constructor-only | normalized, see rounding below |

**Monetary scale/rounding actually present in the code** (the only two places):

- Currency conversion ratio, `ExchangeRatesServiceImpl.java:56`:
  `BigDecimal ratio = rates.get(to).divide(rates.get(from), 4, RoundingMode.HALF_UP);`
  — the ratio is scaled to 4 dp, then `amount.multiply(ratio)` (`:58`) returns an
  unbounded-scale product.
- Period normalization, `StatisticsServiceImpl.java:105-107`:
  `BigDecimal amount = ratesService.convert(item.getCurrency(), Currency.getBase(), item.getAmount()).divide(item.getPeriod().getBaseRatio(), 4, RoundingMode.HALF_UP);`
  — 4 dp, `HALF_UP`.

`TimePeriod.getBaseRatio()` builds its divisor from a `double`
(`statistics-service/.../domain/TimePeriod.java:7-17`:
`YEAR(365.2425), QUARTER(91.3106), MONTH(30.4368), DAY(1), HOUR(0.0416)` and
`return new BigDecimal(baseRatio);`). `new BigDecimal(double)` keeps the exact
binary expansion, so the divisor carries dozens of digits; only the 4 dp result
scale bounds it. The aggregate metrics in `createStatisticMetrics`
(`StatisticsServiceImpl.java:80-96`) apply **no** rounding: `EXPENSES_AMOUNT` and
`INCOMES_AMOUNT` are plain `reduce(BigDecimal.ZERO, BigDecimal::add)` over the
4 dp item amounts, and `SAVING_AMOUNT` is the unrounded `convert(...)` product.

### 2.4 `recipients` — `com.piggymetrics.notification.domain.Recipient`

Source: `notification-service/src/main/java/com/piggymetrics/notification/domain/Recipient.java:11-22`.

| Field | Java type | MongoDB representation | Nullable | Key / index |
| --- | --- | --- | --- | --- |
| `accountName` | `String` | `_id` (String) | no | `@Id` (`Recipient.java:14-15`) |
| `email` | `String` | string | `@NotNull`, `@Email` (`Recipient.java:17-19`) | none |
| `scheduledNotifications` | `Map<NotificationType, NotificationSettings>` | embedded doc keyed by enum name `BACKUP` / `REMIND` (`NotificationType.java:5-6`) | optional; `@Valid` (`Recipient.java:21-22`) | none — but queried, see below |
| `_class` | — | string | — | see risk D-6 |

Nested `NotificationSettings` (`.../domain/NotificationSettings.java:6-14`):

| Field | Java type | MongoDB representation | Nullable | Notes |
| --- | --- | --- | --- | --- |
| `active` | `Boolean` | bool | `@NotNull` (`NotificationSettings.java:8-9`) | |
| `frequency` | `Frequency` | **`int` days**, not the enum name | `@NotNull` (`NotificationSettings.java:11-12`) | custom converters: `FrequencyWriterConverter.java:11-13` writes `frequency.getDays()`, `FrequencyReaderConverter.java:11-13` reads back via `Frequency.withDays(days)`; values `WEEKLY(7), MONTHLY(30), QUARTERLY(90)` (`Frequency.java:7`) |
| `lastNotified` | `java.util.Date` | BSON `date` | optional in the type, defaulted to `new Date()` on save when absent (`RecipientServiceImpl.java:36-41`) | |

The map keys `BACKUP`/`REMIND` are addressed as literal paths in two
server-side-JavaScript queries (`RecipientRepository.java:15-21`), e.g.
`{'scheduledNotifications.BACKUP.active': true }` plus a `$where` clause
comparing `lastNotified` against `frequency` days. These run unindexed
collection scans and depend on `frequency` being a number in the stored
document — i.e. on the converters above being applied. Both services register
their converters through a nested `@Configuration` class that returns a
`CustomConversions` bean: `NotificationServiceApplication.java:30-38` and
`StatisticsApplication.java:32-40`. Note the bean type is the deprecated
`org.springframework.data.mongodb.core.convert.CustomConversions`
(`StatisticsApplication.java:14`, `NotificationServiceApplication.java:12`), not
`MongoCustomConversions` — see risk D-5.

### 2.5 Indexes

No `@Indexed`, `@CompoundIndex`, `ensureIndex` or `createIndex` exists anywhere
in the repository (verified by search across all files). The only indexes are
the implicit `_id` indexes. Query paths that therefore scan:
`AccountRepository.findByName` (`AccountRepository.java:10`, though `name` *is*
`_id`), `DataPointRepository.findByIdAccount` (`DataPointRepository.java:13` →
`_id.account`, a prefix of the `_id` subdocument, not usable as an index prefix),
and both `$where` recipient queries (`RecipientRepository.java:15-21`).

## 3. Identity and key strategy

Every collection is keyed by a **natural key**; no `ObjectId` is generated
anywhere in the Java code.

| Collection | `_id` | Source of the value |
| --- | --- | --- |
| `accounts` | account name (= username) | `Account.name` `@Id` (`Account.java:17-18`), set from `user.getUsername()` at creation (`AccountServiceImpl.java:61`) |
| `users` | username | `User.username` `@Id` (`auth User.java:13-14`) |
| `recipients` | account name | `Recipient.accountName` `@Id` (`Recipient.java:14-15`), overwritten with the principal name on every save (`RecipientServiceImpl.java:35`) |
| `datapoints` | `{ date, account }` subdocument | `DataPointId` (`DataPoint.java:18-19`, `DataPointIdWriterConverter.java:17-22`) |

Consequences relied upon in the code:

- **Idempotent daily rewrite.** `StatisticsServiceImpl.java:53-56` truncates the
  timestamp to start-of-day in the JVM default zone
  (`LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()`),
  so re-saving within a day replaces the same `_id`. The interface documents
  this ("Compound `DataPoint#id` forces to rewrite the object for each account
  within a day", `StatisticsService.java:22-23`) and
  `DataPointRepositoryTest.java:58-86` asserts it.
- **Seed upsert on the natural key.** The demo account is written with
  `db.accounts.replaceOne(`
  (`mongodb/dump/account-service-dump.js:7-96`), which is only re-runnable
  because `_id` is the account name.
- **Uniqueness as a business rule.** Duplicate registration is prevented by a
  read-then-write check, not by a unique index other than `_id`:
  `AccountServiceImpl.java:48-49` (`Assert.isNull(existing, "account already exists: " + user.getUsername());`) and
  `UserServiceImpl.java:27-28`.
- Account name and auth username are the same string in two databases, joined by
  nothing but convention (`AccountServiceImpl.java:51`, `:61`).

## 4. Cross-service data flow

No service reads another service's database. All cross-service data movement is
HTTP via Feign:

| Caller | Callee | Client | Data moved |
| --- | --- | --- | --- |
| account-service | auth-service | `AuthServiceClient.java:9-13` (`POST /uaa/users`) | new `User` (username + plaintext password) → written to `users` by `UserServiceImpl.java:33` |
| account-service | statistics-service | `StatisticsServiceClient.java:10-14` (`PUT /statistics/{accountName}`) | the whole `Account` after every change (`AccountServiceImpl.java:90`) → becomes a `datapoints` row |
| notification-service | account-service | `AccountServiceClient.java:9-13` (`GET /accounts/{accountName}`) | the account JSON, used verbatim as the backup email attachment (`NotificationServiceImpl.java:40-41`) |
| statistics-service | external rates provider | `ExchangeRatesClient.java:10-14` (`GET /latest?base=`) | `Map<String, BigDecimal>` rates, stored into `datapoints.rates` (`StatisticsServiceImpl.java:73`) |

Where the pattern is broken or weakened:

- **The seed path writes account-service's collection directly**, with no
  service in the loop: `mongodb/dump/account-service-dump.js:7-96`, loaded by
  `scripts/demo/seed-local.js:21-22` and mounted straight into the MongoDB
  container's init directory by `docker-compose.core.yml:14`.
- **statistics-service ships a second `@Document("accounts")` class**
  (`statistics-service/.../domain/Account.java:10`) — a persistence annotation on
  what is otherwise a pure HTTP DTO. It is the seam where the "no shared
  collection" rule would break silently (section 1, hazard 2).
- **Failures are absorbed silently, so the two stores diverge.**
  `StatisticsServiceClientFallback.java:15-17` logs and returns when the
  statistics update fails, after the account has already been committed
  (`AccountServiceImpl.java:86` then `:90`); `ExchangeRatesClientFallback.java:12-18`
  returns an **empty** rate map on failure, which makes
  `ExchangeRatesServiceImpl.java:39-44` put `null` values into the rates map for
  `EUR`/`JPY`/`RUB` and, via `convert` (`:56`), fail on `null`. There is no
  compensating action, retry, or outbox anywhere in the code.
- `notification-service` never learns about new accounts: a `recipients`
  document exists only once the user saves notification settings
  (`RecipientController.java:26-29`).

## 5. Seed/demo data

`scripts/demo/seed-local.sh` renders `scripts/demo/seed-local.js` with the
password (`<redacted, see .env>`) and the dump path
(`seed-local.sh:21-24`) and runs it through the Mongo shell (`seed-local.sh:26-31`).
The rendered script creates the `user` account in each of the four databases
(`seed-local.js:3-19`), switches to `piggymetrics_accounts`
(`seed-local.js:21`) and loads `mongodb/dump/account-service-dump.js`
(`seed-local.js:22`).

Which database the demo account lands in, per tier:

| Tier | Mechanism | Target database |
| --- | --- | --- |
| T1 bare JVMs | `start-local.sh:49-51` calls `seed-local.sh`, which switches with `db.getSiblingDB("piggymetrics_accounts")` (`seed-local.js:21`) | `piggymetrics_accounts` |
| T2 core Compose | dump mounted as `/docker-entrypoint-initdb.d/20-account-service-dump.js` (`docker-compose.core.yml:14`), run against `MONGO_INITDB_DATABASE: piggymetrics_accounts` (`docker-compose.core.yml:7`) | `piggymetrics_accounts` |
| T3 legacy full stack | `INIT_DUMP: account-service-dump.js` (`docker-compose.yml:97`) executed by `mongodb/init.sh:23` as `mongo piggymetrics $auth $INIT_DUMP` | `piggymetrics` on the `account-mongodb` container |

The dump itself no longer selects a database — commit `55bac00` removed the
`db = db.getSiblingDB('piggymetrics_accounts')` line — so the target is decided
entirely by the caller (see risk D-7).

Seeded `accounts` document `_id: "demo"`
(`mongodb/dump/account-service-dump.js:7-96`):

- `lastSeen`: `new Date()` at seed time (`:11`); `note`: `"demo note"` (`:12`).
- 8 expenses (`:13-70`): Rent 1300 USD/MONTH, Utilities 120 USD/MONTH, Meal
  20 USD/DAY, Gas 240 USD/MONTH, Vacation 3500 EUR/YEAR, Phone 30 EUR/MONTH,
  **Tokyo 147.85 JPY/MONTH, icon `travel`** (`:56-62`), Gym 700 USD/YEAR.
- 2 incomes (`:71-86`): Salary 42000 USD/YEAR, Scholarship 500 USD/MONTH.
- `saving` (`:87-93`): amount 5900 USD, interest 3.32, `deposit: true`,
  `capitalization: false`.

The `JPY` "Tokyo" item was added with the demo harness (commit `49b8bbe`) and is
the harness's readiness signal: `start-local.sh:111-112` waits for
`GET /accounts/demo` to contain `"currency":"JPY"`, and `smoke.sh:58-62`
fails the smoke run if it is absent. `smoke.sh:44-48` also PUTs the same
147.85 JPY item for a throwaway user, so a JPY amount travels the full
account → statistics path. The matching JPY rate is served by the local stub
(`scripts/demo/rates-stub.py:20-24`: `"JPY": 147.85`) and by the test server
(`ExchangeRatesTestServer.java:51-53`).

Note the shape of the seeded document: no `_class` field, no `icon` validation,
and every amount written as a raw JSON number by the Mongo shell — i.e. a BSON
`double` for `147.85` and `3.32` — whereas the Java writer path persists
`BigDecimal` (risk D-2). `datapoints` and `recipients` are never seeded; the
demo statistics row only exists after some write reaches
`PUT /statistics/demo`.

## 6. Migration risk register for data (Boot 2.0 → Boot 3 / current Spring Data MongoDB)

| # | Risk | Anchored at | Why it bites |
| --- | --- | --- | --- |
| D-1 | The four-database split lives only in the `local` profile; the base config points every service at `database: piggymetrics` | `auth-service.yml:3-8`, `account-service.yml:12-17`, `statistics-service.yml:12-17`, `notification-service.yml:28-35` vs the four `*-local.yml:6-10` | Any re-platforming that changes how profiles are activated (Boot 3 config-data migration, `spring.config.import` for Config Server) silently collapses four logical databases into one on the consolidated MongoDB, where `accounts` then has two mappings (`statistics/.../Account.java:10`) |
| D-2 | `BigDecimal` amounts vs seeded BSON doubles in the same field | `account/.../Item.java:15`, `Saving.java:9,15`, `timeseries/DataPoint.java:25,27`, `ItemMetric.java:16` vs `mongodb/dump/account-service-dump.js:57` (`"amount": 147.85`), `:92` (`"interest": 3.32`) | Spring Data MongoDB's default `BigDecimal` representation (String today, `Decimal128` under the newer `BigDecimalRepresentation` setting) differs from the seeded double. The same `accounts.expenses.amount` path therefore holds two BSON types, and changing the representation on upgrade breaks reads of already-stored documents. Read verification: [runtime item 1](#open-items-for-runtime-verification) |
| D-3 | `java.util.Date` everywhere, with day bucketing in the JVM default zone | `Account.java:20`, `DataPointId.java:12`, `NotificationSettings.java:14`; `StatisticsServiceImpl.java:53-54` (`LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault())`) | Moving to `java.time` changes the mapped BSON type and, more importantly, the `_id` value of `datapoints`: a container timezone change re-buckets the day and creates duplicate rows for the same account/day instead of overwriting |
| D-4 | `Map<Currency, BigDecimal>` and `Map<StatisticMetric, BigDecimal>` keyed by enum name | `DataPoint.java:25`, `:27`; `Recipient.java:22` | Enum-name map keys become literal BSON field names, and the notification queries hard-code them (`RecipientRepository.java:15-21`). Renaming or reordering an enum constant (`Currency.java:5`, `StatisticMetric.java:5`, `NotificationType.java:5-6`) is a data-format change, and any key-mapping change on upgrade invalidates stored documents |
| D-5 | Legacy `com.mongodb.DBObject`-based converters for the composite `_id`, plus enum→`int` converters | `DataPointIdWriterConverter.java:3-22`, `DataPointIdReaderConverter.java:3-19`, `FrequencyWriterConverter.java:8-13`, `FrequencyReaderConverter.java:8-13` | `DBObject`/`BasicDBObject` are legacy driver types (the modern equivalent is `org.bson.Document`); the converters must be ported, and the `CustomConversions` bean type they are registered with (`StatisticsApplication.java:32-40`, `NotificationServiceApplication.java:30-38`) is deprecated in favour of `MongoCustomConversions`. If they silently stop being applied, `datapoints._id` field order/shape changes and `scheduledNotifications.*.frequency` starts persisting as an enum name, which makes the `$where` queries in `RecipientRepository.java:15-21` match nothing |
| D-6 | `_class` type hints on documents written through Spring Data, with two `Account` classes mapped to the same collection name | `account/.../Account.java:15` vs `statistics/.../Account.java:12` (same collection name, different packages); seed path writes no `_class` at all (`mongodb/dump/account-service-dump.js:7-96`) | Default type mapping writes the fully-qualified class name, so any package rename during migration orphans existing documents unless a `TypeMapper`/alias is configured. `accounts` also mixes documents that carry `_class` (Java writes) with the seeded document that does not, and the two `Account` classes disagree about the shape of that collection — reading one another's documents depends on runtime type mapping. Read verification: [runtime item 5](#open-items-for-runtime-verification) |
| D-7 | The dump no longer selects its own database | `mongodb/dump/account-service-dump.js` (the `getSiblingDB` line was removed in commit `55bac00`); target now comes from `seed-local.js:21`, `docker-compose.core.yml:7`, `mongodb/init.sh:23` | Three different callers each decide the target database; a Compose/entrypoint change during migration silently seeds the demo account into the wrong database with no error |
| D-8 | No schema validation and no indexes other than `_id` | no `@Indexed`/`createIndex` anywhere; `RecipientRepository.java:15-21` (`$where` scans), `DataPointRepository.java:13` (`_id.account`) | Nothing at the database level enforces the field types the Java layer assumes, so type drift from D-2/D-5 is invisible until a read fails. `$where` is deprecated in MongoDB and blocked when server-side JavaScript is disabled — a likely default change when the MongoDB version moves with the platform |
| D-9 | Bean-validation annotations move from `javax.*` to `jakarta.*`, and `org.hibernate.validator.constraints.Length`/`Email` are deprecated/removed | `Account.java:8-9,32`, `Item.java:3-5`, `Recipient.java:3-8`, `account/.../User.java:3-5` | These constraints are the only guard on stored field shapes (title ≤ 20 chars, note ≤ 20 000 chars, email format). Dropping one during the namespace migration widens what can be persisted without any test failing |
| D-10 | Deprecated Jackson binding on persisted domain classes | `account/.../Account.java:3` and `statistics/.../Account.java:3` use `org.codehaus.jackson.annotate.JsonIgnoreProperties` (Jackson 1), while `ExchangeRatesContainer.java:3` uses `com.fasterxml.jackson.annotation` (Jackson 2) | Jackson 1 is absent from Boot 3. On removal, the `ignoreUnknown = true` behaviour is lost and account payloads carrying fields the target class lacks (e.g. `name`/`lastSeen`/`note` sent to statistics-service, `statistics/.../Account.java:12-24`) start failing deserialization at the service boundary |
| D-11 | Unrounded aggregates and a `double`-derived divisor | `StatisticsServiceImpl.java:84-90` (no scale on `EXPENSES_AMOUNT`/`INCOMES_AMOUNT`/`SAVING_AMOUNT`), `ExchangeRatesServiceImpl.java:56-58`, `TimePeriod.java:7-16` (`new BigDecimal(baseRatio)` on `365.2425` etc.) | Stored statistic amounts have an unbounded, platform-dependent scale. If the `BigDecimal` representation changes to `Decimal128` (34 significant digits, D-2), values that round-trip today can throw on write or lose precision |

## Open items for runtime verification

None of the following could be established from source alone. Each is a command
to run against a seeded stack (T1 after `scripts/demo/start-local.sh` +
`scripts/demo/seed-local.sh`, or T2 after `docker compose -f
docker-compose.core.yml up`). Use the credentials in `.env`
(`<redacted, see .env>`).

1. **Actual BSON type of every monetary field** (risk D-2) — confirm whether the
   Java writer stores `BigDecimal` as string, double or `Decimal128`, and whether
   it differs from the seeded document:
   ```js
   // after a PUT /accounts/current has written a Java-side document
   use piggymetrics_accounts
   db.accounts.aggregate([
     { $project: { seededType: { $type: { $arrayElemAt: ["$expenses.amount", 0] } },
                   savingType: { $type: "$saving.amount" },
                   interestType: { $type: "$saving.interest" } } }
   ])
   ```
2. **Whether the custom converters actually take effect on the stored shape**
   (risk D-5) — they are `@Component` `Converter` beans
   (`DataPointIdWriterConverter.java:9`) registered via a `CustomConversions`
   bean (`StatisticsApplication.java:32-40`,
   `NotificationServiceApplication.java:30-38`); what they produce on disk still
   needs confirming:
   ```js
   use piggymetrics_notifications
   db.recipients.findOne({}, { "scheduledNotifications.REMIND.frequency": 1 })
   // number (7/30/90) => FrequencyWriterConverter is applied; "WEEKLY" => it is not
   use piggymetrics_statistics
   db.datapoints.findOne({}, { _id: 1 })   // expect { _id: { date: ISODate, account: "..." } }, in that field order
   ```
3. **Index inventory** (risk D-8) — source contains no `@Indexed`,
   `@CompoundIndex`, `createIndex` or `ensureIndex` declaration in any module,
   and no Mongo schema validator; that does not prove the live databases carry
   no additional indexes or validators, so inventory them:
   ```js
   ["piggymetrics_auth","piggymetrics_accounts","piggymetrics_statistics","piggymetrics_notifications"]
     .forEach(d => db.getSiblingDB(d).getCollectionNames()
       .forEach(c => printjson({ db: d, coll: c, idx: db.getSiblingDB(d)[c].getIndexes() })));
   ```
4. **Which databases and collections actually materialize per tier** (risks D-1,
   D-7) — this also settles the profile-merge and authentication-database
   inheritance claims in section 0, which rest on framework behaviour rather
   than on anything stated in this repository. In particular whether an `accounts` collection ever appears in
   `piggymetrics_statistics`, and whether any collection appears in a bare
   `piggymetrics` database on the consolidated instance:
   ```js
   db.adminCommand({ listDatabases: 1 })
   db.getSiblingDB("piggymetrics_statistics").getCollectionNames()
   db.getSiblingDB("piggymetrics").getCollectionNames()
   ```
5. **`_class` values actually stored** (risk D-6):
   ```js
   use piggymetrics_accounts
   db.accounts.distinct("_class")   // expect com.piggymetrics.account.domain.Account, and nothing else
   ```
6. **Document counts and the demo row after seeding** (sanity baseline for the
   migration diff):
   ```js
   use piggymetrics_accounts
   db.accounts.countDocuments({})
   db.accounts.findOne({ _id: "demo" }).expenses.filter(e => e.currency === "JPY")
   ```
7. **Scale of stored statistic amounts** (risk D-11) — after
   `PUT /accounts/current` with the smoke payload (`scripts/demo/smoke.sh:44-48`):
   ```js
   use piggymetrics_statistics
   db.datapoints.findOne({ "_id.account": "demo" }, { statistics: 1, rates: 1 })
   ```
