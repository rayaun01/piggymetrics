# Provenance

This kit adapts the Apache-2.0 licensed code-modernization plugin from
https://github.com/anthropics/claude-plugins-official, path
`plugins/code-modernization`. Source file inventory was verified with `find`.

| Upstream path | Disposition | Our path | Rationale |
|---|---|---|---|
| `.claude-plugin/plugin.json` | deliberately not ported | — | Plugin metadata and packaging are not part of a repository-local method kit. |
| `LICENSE` | ported to | `.agents/LICENSE-APACHE-2.0` | Preserve the upstream Apache-2.0 text byte-for-byte. |
| `README.md` | merged into | `.agents/skills/modernization/README.md` | Retain the method sequence while replacing packaging and workspace assumptions with Git stages. |
| `agents/architecture-critic.md` | ported to | `.agents/skills/modernization/roles/architecture-critic.md` | Preserve the review mandate and add repository scope and delegation boundaries. |
| `agents/business-rules-extractor.md` | ported to | `.agents/skills/modernization/roles/business-rules-extractor.md` | Preserve rule extraction and narrow it to uplift-sensitive behavior. |
| `agents/legacy-analyst.md` | ported to | `.agents/skills/modernization/roles/legacy-analyst.md` | Preserve cited read-only analysis for this Java/Maven repository. |
| `agents/scaffolder.md` | ported to | `.agents/skills/modernization/roles/scaffolder.md` | Retain the role for provenance but mark greenfield scaffolding out of scope. |
| `agents/security-auditor.md` | ported to | `.agents/skills/modernization/roles/security-auditor.md` | Preserve adversarial scanning without adding a separate security skill. |
| `agents/test-engineer.md` | ported to | `.agents/skills/modernization/roles/test-engineer.md` | Preserve characterization testing and add the dual-JDK oracle boundary. |
| `agents/uplift-migrator.md` | ported to | `.agents/skills/modernization/roles/uplift-migrator.md` | Preserve one-unit migration and build proof with the Python workflow schema. |
| `agents/version-delta-analyst.md` | ported to | `.agents/skills/modernization/roles/version-delta-analyst.md` | Preserve same-stack delta/referee work and Maven tool states. |
| `assets/topology-viewer-screenshot.jpg` | deliberately not ported | — | The interactive topology viewer is intentionally outside this kit. |
| `assets/topology-viewer.html` | deliberately not ported | — | No browser viewer is required for the committed as-is topology artifact. |
| `commands/modernize-assess.md` | merged into | `.agents/skills/modernization-assess-map/SKILL.md` | Assessment is bound to existing repository artifacts. |
| `commands/modernize-brief.md` | ported to | `.agents/skills/modernization-brief/SKILL.md` | Preserve gates, approval, pilot, and staged proof. |
| `commands/modernize-extract-rules.md` | merged into | `.agents/skills/modernization-assess-map/SKILL.md` | Rule cards are part of the bounded discovery pass. |
| `commands/modernize-harden.md` | deliberately not ported | — | Security hardening is orthogonal to this version uplift. |
| `commands/modernize-map.md` | merged into | `.agents/skills/modernization-assess-map/SKILL.md` | Topology and trust boundaries already live in artifact 01. |
| `commands/modernize-preflight.md` | ported to | `.agents/skills/modernization-preflight/SKILL.md` | Preserve readiness checks using recorded repository evidence. |
| `commands/modernize-reimagine.md` | deliberately not ported | — | Greenfield redesign is outside a same-stack uplift. |
| `commands/modernize-status.md` | ported to | `.agents/skills/modernization-status/SKILL.md` | Replace filesystem time with Git commit freshness. |
| `commands/modernize-transform.md` | deliberately not ported | — | Cross-stack rewrite is outside the current method kit. |
| `commands/modernize-uplift.md` | ported to | `.agents/skills/modernization-uplift/SKILL.md` | Preserve numbered gates and steps with Java/Maven facts. |
| `workflows/extract-rules.js` | deliberately not ported | — | The bounded rule pass is sequential within discovery. |
| `workflows/harden-scan.js` | deliberately not ported | — | No dedicated security-hardening workflow is in scope. |
| `workflows/portfolio-assess.js` | deliberately not ported | — | Portfolio orchestration is unnecessary at this repository scale. |
| `workflows/reimagine-scaffold.js` | deliberately not ported | — | Greenfield service scaffolding is out of scope. |
| `workflows/uplift-deltas.js` | ported to | `.agents/workflows/uplift_deltas.py` | Port to the supplied deterministic Python workflow runtime. |
| `workflows/uplift-migrate.js` | ported to | `.agents/workflows/uplift_migrate.py` | Port to the supplied deterministic Python workflow runtime. |

The four unported orchestration scripts are represented by sequential skill
steps or an explicitly out-of-scope role; no packaging or install behavior is
required.
