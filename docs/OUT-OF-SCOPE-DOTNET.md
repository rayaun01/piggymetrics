# .NET services out of scope

`fraud-detection-service`, `compliance-service`, and `currency-exchange-service` are
.NET Core 2.1 services. They are excluded from this migration and from every run
tier, including the full Java stack.

Their REST contracts are frozen. Nothing in the Java migration may depend on
these services. Their source directories remain in the repository as the
documented out-of-scope boundary.

No LATAM-currency change is needed: excluding the .NET services also removes
the LATAM currency set from the migration scope. JPY on the Java side is the
complete currency story for this work.
