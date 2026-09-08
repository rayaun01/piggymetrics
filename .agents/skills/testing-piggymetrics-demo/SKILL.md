---
name: testing-piggymetrics-local
description: Boot and browser-test the PiggyMetrics bare-JVM migration-demo stack.
---

# Local stack
- Work from the current PiggyMetrics checkout; do not assume similarly named sibling directories are the active checkout.
- Read `docs/RUNBOOK.md` and `scripts/demo/start-local.sh` first. Use JDK 8 for existing baseline jars/builds.
- If MongoDB is not on PATH, set `MONGO_BIN` to an installed `mongod` with a matching sibling `mongo` shell. In this environment the installed tarball is `/home/ubuntu/repos/piggymetrics-demo-harness/mongodb-linux-x86_64-3.2.2/bin/mongod`.
- Start with `MONGO_BIN=<absolute binary> scripts/demo/start-local.sh`. Measure readiness using a monotonic timer and retain the script output. A gateway listening socket or root HTTP 200 does not prove routed OAuth/account readiness.
- Gateway UI is `http://localhost:4000`; Eureka is `http://localhost:8761`. Logs/PIDs live under `.demo-runtime`.
- Stop with the same `MONGO_BIN` override and `scripts/demo/stop-local.sh`, unless the user requests leaving services running.

# Browser workflow
- Create a unique account through “create new account”; username requires three characters and password six.
- Reminder subscription is offered after signup; do not assume a later notification preferences page exists.
- Login is two-stage: username → Enter, then password → Enter. Greeting offers Get in and Log out.
- Get in opens transaction columns. Modal Save changes updates local rows; the main bottom Save changes persists the account and opens charts. Reload and re-enter to distinguish UI-only state from persistence.
- Demo is reached from the login speech-bubble/info icon → Demo account; it is read-only and does not require a demo password.
- Allow card/modal animations to finish before the next click. Amount formatting may filter punctuation: inspect the visible value after typing before saving.
- Frontend chart rendering may use a different rate source from backend statistics. When charts show NaN or columns stay blank, inspect native console errors and rate loading before assuming data was lost.
- After gateway static-asset rebuilds, hard reload. The local currency path is `/rates/latest?base=USD`; read-only diagnostics should show RUB-per-unit globals of USD 92.5, EUR approximately 100.543478, JPY approximately 0.625634 with the deterministic stub.
- A useful integer transaction is 14785 JPY/month, which converts to 100 USD/month without relying on the amount widget's decimal handling. Savings cycling offers only USD/RUB/EUR; test repeated USD 100 → RUB 9250 → EUR 92 → USD 100.
- Tiny expense structure bars may round to zero-percent width, making their monthly labels inaccessible by clicking. Select the item in a per-day/per-year chart dropdown for visible finite conversion evidence; do not count hidden DOM monthly text as visible proof.
- Do not inject currency options, patch rates in the console, or use authenticated curl to claim UI functionality.

## Devin Secrets Needed
No external user secret is required for local browser signup. Existing `.env` supplies `CONFIG_SERVICE_PASSWORD`, `ACCOUNT_SERVICE_PASSWORD`, `STATISTICS_SERVICE_PASSWORD`, `NOTIFICATION_SERVICE_PASSWORD`, and `MONGODB_PASSWORD`; never publish values.
