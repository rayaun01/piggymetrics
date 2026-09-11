# Compliance Service

ASP.NET Core (.NET 8) service for regulatory compliance checking, audit logging and
KYC/AML monitoring. It is part of the frozen .NET boundary described in
`docs/modernization/BRIEF.md` §2: it is not deployed by any run tier and no Java
module depends on it.

## Build and run

```bash
dotnet build
dotnet test tests/ComplianceService.Tests
dotnet publish -c Release -o out
docker build -t piggymetrics/compliance-service .
```

The service listens on `http://+:8086` (`ASPNETCORE_URLS`) and serves Swagger UI at
`/swagger`.

## Required configuration

There are no defaults for credentials or the token issuer; the service fails fast at
startup when either is missing.

| Environment variable | Purpose |
| --- | --- |
| `Mongo__ConnectionString` (or `MONGODB_URI`) | Full MongoDB connection string, including credentials |
| `Mongo__Database` | Database name (default `compliance`) |
| `Jwt__Authority` (or `JWT_AUTHORITY`) | OAuth2 issuer used to validate bearer tokens, i.e. the PiggyMetrics auth-service |
| `Jwt__Issuer` | Expected `iss` claim, when it differs from the authority |
| `Jwt__Audience` | Expected audience; audience validation is skipped when unset |
| `Jwt__RequireHttpsMetadata` | Set to `false` only for local HTTP issuers (default `true`) |

## Authentication

Every endpoint on `ComplianceController` requires a valid OAuth2 bearer (JWT) token
issued by auth-service; there are no anonymous compliance or audit endpoints. This
closes `sfind-391d3acb` in `docs/modernization/SECURITY-REMEDIATION-PLAN.md`.

If this service is ever added to an orchestration file, that file must supply the
variables above — the service will not start with implicit defaults.
