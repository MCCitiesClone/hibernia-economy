# Remediation Pass — Close-out Report

**Findings:** 131 total — **118 resolved**, 0 rejected, 12 decision-needed, 1 external, 0 deferred, 0 pending.

**By severity (resolved / total):** blocker 0/0 · major 24/25 · minor 81/89 · nit 13/17

Scoring: `max(0, 10 − Σ weight)`, weight blocker 3.0/major 1.5/minor 0.5/nit 0.1, confidence ×1.0/0.7/0.4. **After** counts only findings still open (decision-needed/external); resolved findings no longer subtract.

| Component | structure | plugin-architecture | behaviour | testing | build | infra | dependencies | database | mean |
|---|---|---|---|---|---|---|---|---|---|
| common | 9.9→10 | — | 9.5→10 | 8.0→10 | 7.5→10 | 7.5→10 | 8.2→9.1 | 8.7→9.5 | **8.5→9.8** |
| treasury | 5.5→10 | 7.8→9.7 | 9.4→10.0 | 6.0→10 | 7.5→10 | 7.5→10 | 8.2→9.1 | 8.7→9.5 | **7.6→9.8** |
| business | 7.9→9.5 | 3.9→9.9 | 8.7→10 | 7.0→10 | 7.5→10 | 7.5→10 | 8.2→9.1 | 8.7→9.5 | **7.4→9.8** |
| treasury-api-plugin | 7.4→9.9 | 5.5→10 | 9.6→10 | 2.7→10 | 7.5→10 | 7.5→10 | 8.2→9.1 | 8.7→9.5 | **7.1→9.8** |
| treasury-rest-api | 8.4→10 | — | 8.8→10 | 3.3→9.7 | 7.5→10 | 7.5→10 | 8.2→9.1 | 8.7→9.5 | **7.5→9.8** |
| chestshop | 5.0→9.5 | 4.9→9.5 | 7.2→10 | 7.5→10 | 7.5→10 | 7.5→10 | 8.2→9.1 | 8.7→9.5 | **7.1→9.7** |
| economy-explorer | 9.1→10 | — | 9.5→10 | 4.9→8.5 | 7.5→10 | 7.5→10 | 8.2→9.1 | 8.7→9.5 | **7.9→9.6** |
| **mean** | 7.6→9.8 | 5.5→9.8 | 9.0→10.0 | 5.6→9.7 | 7.5→10.0 | 7.5→10.0 | 8.2→9.1 | 8.7→9.5 |  |

**Score regressions (any cell where after < before):** NONE — every dimension improved or held.

## Remaining open findings (require your decision)

### decision-needed (12)

- **business/plugin-architecture/0007** (nit) — Same interface+impl convention decision.
- **business/structure/0004** (minor) — Standardise firm addressing on int firmId — large API-breaking; approve scope or defer.
- **chestshop/plugin-architecture/0008** (minor) — Same interface+impl convention decision.
- **chestshop/structure/0003** (minor) — canHold: implement a real ceiling or delete the dead guard — product call.
- **economy-explorer/testing/0005** (major) — Run Flyway in test harness vs add CI drift check — pick one strategy (also treasury-rest-api/testing/0009).
- **global/database/0002** (minor) — DROP TABLE firm_sale — confirm no retention requirement for legacy rows.
- **global/dependencies/0002** (minor) — vitest 2→3 major upgrade — approve for Wave 6 upgrade lane.
- **global/dependencies/0005** (nit) — server/bot/package.json is an un-customised `npm init` stub: it declares no dependencies, points `main` at an index.js that does not exist i
- **treasury-api-plugin/structure/0004** (nit) — Interface+impl convention for ALL injected services (decide once; applies with business/pa/0007, chestshop/pa/0008).
- **treasury-rest-api/testing/0009** (minor) — Same test-harness Flyway-vs-drift-check decision.
- **treasury/behaviour/0001** (nit) — Salary dedup clock — low-confidence, downgraded; decide fix vs formally reject.
- **treasury/plugin-architecture/0005** (minor) — The charter calls for an interface + impl split for every service. Three service-package classes are concrete-only with no interface: Balanc

### external (1)

- **global/dependencies/0004** (minor) — Cut hibernia-framework 1.2.0 release and pin — external (framework repo).
