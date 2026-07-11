# Remediation Pass — Close-out Report

**Findings:** 131 total — **129 resolved**, 0 rejected, 0 decision-needed, 1 external, 1 deferred, 0 pending.

**By severity (resolved / total):** blocker 0/0 · major 25/25 · minor 87/89 · nit 17/17

Scoring: `max(0, 10 − Σ weight)`, weight blocker 3.0/major 1.5/minor 0.5/nit 0.1, confidence ×1.0/0.7/0.4. **After** counts only findings still open (decision-needed/external); resolved findings no longer subtract.

| Component | structure | plugin-architecture | behaviour | testing | build | infra | dependencies | database | mean |
|---|---|---|---|---|---|---|---|---|---|
| common | 9.9→10 | — | 9.5→10 | 8.0→10 | 7.5→10 | 7.5→10 | 8.2→9.5 | 8.7→10 | **8.5→9.9** |
| treasury | 5.5→10 | 7.8→10 | 9.4→10 | 6.0→10 | 7.5→10 | 7.5→10 | 8.2→9.5 | 8.7→10 | **7.6→9.9** |
| business | 7.9→10 | 3.9→10 | 8.7→10 | 7.0→10 | 7.5→10 | 7.5→10 | 8.2→9.5 | 8.7→10 | **7.4→9.9** |
| treasury-api-plugin | 7.4→10 | 5.5→10 | 9.6→10 | 2.7→10 | 7.5→10 | 7.5→10 | 8.2→9.5 | 8.7→10 | **7.1→9.9** |
| treasury-rest-api | 8.4→10 | — | 8.8→10 | 3.3→10 | 7.5→10 | 7.5→10 | 8.2→9.5 | 8.7→10 | **7.5→9.9** |
| chestshop | 5.0→10 | 4.9→10 | 7.2→10 | 7.5→10 | 7.5→10 | 7.5→10 | 8.2→9.5 | 8.7→10 | **7.1→9.9** |
| economy-explorer | 9.1→10 | — | 9.5→10 | 4.9→10 | 7.5→10 | 7.5→10 | 8.2→9.5 | 8.7→10 | **7.9→9.9** |
| **mean** | 7.6→10.0 | 5.5→10.0 | 9.0→10.0 | 5.6→10.0 | 7.5→10.0 | 7.5→10.0 | 8.2→9.5 | 8.7→10.0 |  |

**Score regressions (any cell where after < before):** NONE — every dimension improved or held.

## Remaining open findings (require your decision)

### external (1)

- **global/dependencies/0004** (minor) — Cut hibernia-framework 1.2.0 release and pin — external (framework repo).

### deferred (1)

- **global/dependencies/0002** (minor) — vitest 2→3 major upgrade — approve for Wave 6 upgrade lane.
