export const meta = {
  name: 'monorepo-quality-audit',
  description: 'Exhaustive read-only quality audit: fan out audit cells, adversarially verify findings',
  phases: [
    { title: 'Audit' },
    { title: 'Verify' },
  ],
}

// ============================================================================
// Shared operating rules — included verbatim in every audit sub-agent prompt.
// ============================================================================
const SHARED_RULES = `
## Shared operating rules (READ-ONLY audit — never modify source)

**Evidence discipline.** Every finding MUST include the file path, a 1-indexed line range, and a verbatim snippet you re-read from the file immediately before emitting the finding. A finding you cannot anchor to specific lines does not exist. If you suspect a problem, confirm it by reading the code or discard it — suspicion is not a finding.

**No deferrals.** Banned outputs: "consider reviewing", "might want to", "further investigation recommended", "appears to possibly", findings without a concrete recommendation, and meta-commentary about what you did not have time to check. If a check in your charter is genuinely inapplicable to your scope, record it in your summary's not_applicable list with one line of justification — that is the only permitted omission.

**Exhaustiveness over exemplars.** When a violation pattern repeats, do not report one instance "as an example". Enumerate every occurrence: either one finding per file, or one finding with an occurrences array covering all sites. Your summary must state which checks you ran against which files so gaps are auditable.

**Triage strategy for scale.** Do not attempt to read every file linearly. For each check: (1) design rg patterns that surface candidate sites, (2) read every flagged file fully around the match, (3) additionally read a random sample of 5 unflagged files per package to catch what grep cannot express (naming drift, misplaced logic, structural issues). Report your sample list in the summary.

**Severity definitions.**
- blocker — data loss, currency duplication/destruction, security vulnerability, crash on a mainline path
- major — incorrect behaviour, race condition, missing validation on external input, architectural violation of the stated criteria
- minor — inconsistency, duplication, missing test for testable behaviour, config drift
- nit — naming, minor style within the stated criteria

**Confidence.** high = verified by reading all relevant code paths; medium = strong local evidence, cross-cutting path not fully traced; low = pattern match with plausible mitigating context elsewhere. Low-confidence findings still require evidence; if you cannot state what would confirm or refute it, discard it.

**Repo context.** Ledger is the source of truth (Treasury). Balances live in account_balances_mat, written SOLELY by the trg_postings_ai DB trigger on ledger_postings — application code never UPDATEs a balance. In-process plugins move money only via TreasuryApi.transfer(...). The one exception is treasury-rest-api, a separate JVM that re-implements transfer against ledger_txns/ledger_postings (service/TransferService.java) — if you audit transfer logic, note that treasury LedgerServiceImpl.transferInternal and rest-api TransferService must stay behaviour-equivalent. AccountType enum: PERSONAL/BUSINESS/GOVERNMENT/SYSTEM. Money is stored as long minor units / BIGINT — any double/float for currency is a major finding. UUIDs as BINARY(16). Plugins are built on the Hibernia Framework (Guice Event-Service-Dao; annotation @Command/@Route handlers; @Async routes; ParameterResolvers; @Dialog/@Screen/@Action; Message-based i18n; semantic exceptions NotFoundException/ConflictException/BadCommandException/ExceedsLimitException/NoPermissionException/InternalException).

**Effort values:** trivial, small, medium, large.

**Output contract.** You MUST call the StructuredOutput tool exactly once with an object {findings:[...], summary:{...}}. Each finding: {seq (integer, 1-based within this cell), category, severity, confidence, file, lines:[start,end], snippet (verbatim), description, recommendation, effort, occurrences:[{file,lines,snippet}] (may be empty)}. The summary: {files_examined:int, checks_run:[strings], not_applicable:[strings], sampled_files:[strings]}. If you find nothing for a check, that's fine — return an empty findings array and explain coverage in the summary. Do NOT write any files; return everything through the tool.
`

// ============================================================================
// Dimension charters (verbatim from the audit spec).
// ============================================================================
const CHARTERS = {
  structure: `### structure charter
- Placement: every class/module lives where its name and package imply. Flag business logic in controllers/commands, persistence in services, Bukkit types leaking into service or domain layers, utility dumping grounds (Utils/Helpers classes with >3 unrelated responsibilities).
- DRY: near-duplicate blocks across files (rg for distinctive literals/signatures, then compare); re-implemented stdlib/library functionality; copy-pasted validation or mapping logic that should be shared.
- Naming consistency: the same domain concept must have one name everywhere (not balance/funds/money interchangeably; not PlayerAccount in one module and UserWallet in another). Build a glossary of domain terms as you read and flag divergences.
- Null and type safety (Java): public API returning null where Optional<T> is warranted; missing null contracts on parameters; raw generic types; unchecked casts; @SuppressWarnings without justification comment; mutable static state; fields that should be final.
- Type safety (TypeScript): any (explicit or via missing types on boundaries), non-null assertions ! on genuinely nullable values, as casts hiding real type errors, strict flags disabled in tsconfig.
- OOP discipline: god classes (>~400 lines or >~10 dependencies — verify, don't just count), inheritance where composition fits, public mutable fields, constructors doing real work, service locators instead of injection.`,

  'plugin-architecture': `### plugin-architecture charter (Hibernia Framework: Guice Event-Service-Dao)
- Command handlers: @Route methods contain ONLY light arg normalisation, input validation, and a call into a service, with success messaging via Message keys. Any SQL, HikariCP usage, scheduler logic, or economy arithmetic inside a CommandHandler is a finding. Framework-bypass findings: hand-rolled CommandExecutor/onCommand or manual getCommand().setExecutor(...); manual sub-command switch/if routing inside a single @Route; manual hasPermission checks where @Permission belongs (service-layer authorisation legitimately throws NoPermissionException instead); manual arg parsing (args[0], Integer.parseInt) where @Arg typing or a ParameterResolver should carry it.
- Error/messaging flow: validation/business failures surface as semantic exceptions thrown from the service layer and allowed to propagate — flag handlers that catch them to hand-format errors, swallow them, or return boolean success codes. All player-facing text via Message keys: flag hardcoded strings, legacy §/ChatColor codes, raw sender.sendMessage(...) in handlers/services. Injection safety: player-controlled values passed as plain placeholder values (framework escapes them); Message.rich(...) wrapping anything player-influenced is a major finding.
- Resolvers: each ParameterResolver suggestions(...) runs off main thread and resolve(...) runs on a worker for @Async routes, so resolvers must be backed by service-managed caches, never live Bukkit state (Bukkit.getPlayer, world/entity access) or blocking DB/HTTP calls. Player lookups keyed by UUID with name fallback; ambiguous-prefix matches rejected not guessed.
- Events: listeners DI-managed via ListenerManager (flag manual registerEvents); early-return guards ordered cheapest-first; ignoreCancelled=true where cancellation matters; EventPriority deliberate (flag MONITOR handlers that mutate state); all business logic delegated to services.
- Hot events: flag every listener on PlayerMoveEvent, BlockPhysicsEvent, EntityMoveEvent, ChunkLoadEvent, InventoryMoveItemEvent, VehicleMoveEvent. Verify the guard prefix is O(1) and allocation-free. Registration on hot events for rarely-relevant logic is a finding even if guarded.
- Dialogs: @Screen methods only build DialogViews from model state; @Action methods validate @Input values and delegate to services. Long-running work goes through flow.await(...); blocking service/mapper calls directly inside an @Action on the main thread is a finding, as is hand-rolled Supplier<Dialog> navigation instead of DialogFlow. Dialog labels are Message keys.
- Async: (a) no Bukkit API calls (world, entity, inventory, scoreboard mutation) from async contexts — trace every @Async route body, resolver, flow.await future, runTaskAsynchronously/CompletableFuture chain; results touching Bukkit state must hop back via runTask or the flow.await completion callback. (b) Inverse: no JDBC/HTTP/file I/O on the main thread — any mapper call reachable from a sync (non-@Async) route, listener, or @Action without an async hop is a finding. Enumerate sync routes that touch persistence.
- Services: interface + impl split for every service; services re-validate inputs and enforce authorisation (don't trust handlers) throwing semantic exceptions; no Bukkit types in service signatures where a domain type suffices; services are the ONLY layer calling mappers.
- Mappers and pooling: all persistence via mapper classes backed by HikariCP — flag any DriverManager.getConnection or connection use outside mappers. Verify pool config is sane (maximumPoolSize sized to workload, connectionTimeout, maxLifetime below DB server timeout, leakDetectionThreshold in dev). Every connection/statement/resultset in try-with-resources. DB settings via a @ConfigurationComponent (flag hardcoded credentials); a reload() that leaves a stale pool or rebuilds without draining is a finding.
- DI discipline: constructor injection throughout; flag field injection outside dialog handlers, injector.getInstance(...) service-locator calls in business code, hand-wired multibinders duplicating HiberniaModule. Plugin modules bind only services and persistence.`,

  behaviour: `### behaviour charter (TRACE, do not checklist)
Build a map of every economy operation (deposit, withdraw, transfer, purchase, sell, payout, admin grant) from entry point through the service layer to persistence and verify:
1. Conservation: no code path creates or destroys currency except explicit mint/burn. Transfers debit+credit atomically (same transaction, or conditional-update with compensation).
2. No double-spend: concurrent execution by one player cannot spend the same balance twice. Read-modify-write on balances without SELECT ... FOR UPDATE, conditional UPDATE ... WHERE balance >= ?, or serialized per-account execution is a blocker.
3. Precision: currency as double/float anywhere (Java, TS, SQL) is automatically a major finding. Expect long minor units or BigDecimal/DECIMAL.
4. Failure atomicity: every purchase/sale path — what happens if the process dies or an exception fires between payment and delivery. Money taken without goods (or inverse) with no compensation/transactional boundary is a blocker.
5. Dupe vectors: inventory-based interactions (shop GUIs, trade menus) — click events cancelled before item extraction, closing inventory mid-op can't duplicate, shift-click/hotbar-swap/drag paths all handled.
6. Idempotency: scheduled payouts, vote rewards, retries — a retry or double-fire cannot double-pay (unique txn keys or dedup checks).
7. Identity: offline players, name changes (UUID keying not name keying), never-joined targets — no NPEs or wrong-account credits.
8. API/website consistency: the Next.js viewer and REST API display/compute the same numbers the plugin persists — flag divergent formatting, rounding, or stale-cache assumptions.
Also flag plainly confusing behaviour: silent failures, error messages that misstate the cause, commands that partially apply.`,

  testing: `### testing charter
- Mock discipline: flag tests where (a) the assertion verifies interactions with mocks rather than observable behaviour, (b) a mocked collaborator returns exactly the value being asserted, (c) the subject's own logic is stubbed out. For each service class, verify the tests would fail if the business logic were deleted.
- Coverage of testable surface: enumerate service classes, mappers (against a real DB via Testcontainers, not H2 — flag H2-vs-production-dialect mismatches), validation logic, utility functions with no tests. List the gaps per class.
- Regression locks: behaviours with obvious historical-bug shape (rounding, boundary amounts, permission edges) should have pinning tests.
- Test quality: assertions on real values not just non-null; no order-dependent tests; no Thread.sleep-based concurrency tests.
- Framework seams: services/mappers take injected deps and domain types, so flag any service that can't be instantiated in a plain unit test (hidden Bukkit statics). ParameterResolvers (resolve + suggestions, ambiguity/UUID paths) are pure enough to test directly — flag untested custom resolvers. Verify a startup-shaped test exists that builds HiberniaModule and calls registerAll() so route conflicts and missing message keys fail in CI; flag message keys referenced in code but absent from messages.properties (and vice versa).`,

  build: `### build charter (global)
- Shared build logic: convention plugins in build-logic/buildSrc, not copy-pasted subprojects {} blocks or duplicated script plugins. Flag each subproject build.gradle.kts re-declaring what a convention plugin should own (java version, test config, publishing, formatting).
- JDK 21 everywhere: java.toolchain.languageVersion = 21 via convention plugin; flag sourceCompatibility/targetCompatibility remnants or subprojects diverging.
- Single source of truth for versions: gradle/libs.versions.toml owns every dependency and plugin version, project version in exactly one place. Flag every hardcoded version string in a build file.
- Zero warnings: note deprecations/warnings emitted by build files (you cannot run gradle here — reason from the build scripts).
- Publishing: shared libraries apply maven-publish with sources+javadoc jars and complete POM metadata; verify publish target (repo URL, credentials via env not hardcoded) and that consumers resolve via the repo, not includeBuild hacks in CI.
- Coverage in CI: JaCoCo (and JS equivalent) wired into workflows with report aggregation and upload; flag workflows that run tests without collecting coverage.
- Wrapper: Gradle wrapper current and consistent.`,

  dependencies: `### dependencies charter (global)
- Reason from the version catalog and build files (you cannot run network tooling here). For each dependency and plugin: current version, whether it is materially outdated, and known-vulnerable versions you recognise.
- Every known vulnerability is a finding at severity matching the CVE (critical/high -> blocker/major).
- Every available major upgrade is a finding with: current -> target version, breaking changes relevant to THIS codebase (grep for affected APIs), migration outline. These feed a separate remediation run — do NOT perform upgrades.
- Flag duplicate dependencies serving the same purpose (two HTTP clients, two JSON libs) and unused declared dependencies.`,

  infra: `### infra charter (global)
- Base images: pinned by tag (ideally digest), current — eclipse-temurin:21 family for Java, current LTS node for the viewer; flag latest tags, EOL bases, unpinned images.
- Dockerfile quality: multi-stage builds (JDK to build, JRE/distroless to run), non-root user, .dockerignore present and effective, layer ordering that caches dependency resolution before source copy, no secrets in layers or ARGs.
- Harbor workflows: images built and pushed on the right triggers with both immutable (sha-...) and semver tags; registry auth via secrets; flag missing image vulnerability scanning.
- Compose/runtime config: healthchecks, resource limits, env-based config with no credentials committed.`,

  database: `### database charter (global)
- Reconstruct the final schema by replaying migrations in order; audit the result; flag migrations that are destructive without guards or that would fail on non-empty tables.
- Normalisation: appropriate 3NF; flag denormalisation without a stated/discernible reason; verify FK constraints exist with deliberate ON DELETE behaviour (flag accidental CASCADE on financial records — transaction history should survive account deletion or be explicitly archived).
- Index coverage against real queries: extract every SQL statement from mappers/repositories; for each, map WHERE/JOIN/ORDER BY columns to existing indexes; flag missing composite indexes (correct column order) and indexes no query uses.
- Types: money as DECIMAL(p,s) or BIGINT minor units — FLOAT/DOUBLE is a major finding; UUIDs as BINARY(16) not CHAR(36) unless justified; timestamps with timezone discipline; sensible charset/collation (utf8mb4).
- Transaction-history integrity: ledger tables append-only in practice (no code path UPDATEs history rows), balances either derivable from the ledger or reconciled against it.`,
}

// ============================================================================
// Cells.
// ============================================================================
const COMPONENTS = {
  common:               { type: 'library-jar', paths: 'common/src/main/**  (tests: common/src/test/**)' },
  treasury:             { type: 'bukkit-plugin', paths: 'treasury/src/main/**, treasury/treasury-api/src/main/**  (tests: treasury/src/test/**, treasury/treasury-api/src/test/**)' },
  business:             { type: 'bukkit-plugin', paths: 'business/src/main/**, business/business-api/src/main/**  (tests: business/src/test/**, business/business-api/src/test/**)' },
  'treasury-api-plugin':{ type: 'bukkit-plugin', paths: 'treasury-api-plugin/src/main/**  (tests: treasury-api-plugin/src/test/**)' },
  'treasury-rest-api':  { type: 'spring-boot', paths: 'treasury-rest-api/src/main/**  (tests: treasury-rest-api/src/test/**)' },
  chestshop:            { type: 'bukkit-plugin', paths: 'chestshop/src/main/**  (tests: chestshop/src/test/**)' },
  'economy-explorer':   { type: 'nextjs', paths: 'economy-explorer/{app,components,lib,types}/**, economy-explorer/middleware.ts  (tests: economy-explorer/test/**)' },
}

const COMPONENT_CELLS = {
  common:                ['structure', 'behaviour', 'testing'],
  treasury:              ['structure', 'plugin-architecture', 'behaviour', 'testing'],
  business:              ['structure', 'plugin-architecture', 'behaviour', 'testing'],
  'treasury-api-plugin': ['structure', 'plugin-architecture', 'behaviour', 'testing'],
  'treasury-rest-api':   ['structure', 'behaviour', 'testing'],
  chestshop:             ['structure', 'plugin-architecture', 'behaviour', 'testing'],
  'economy-explorer':    ['structure', 'behaviour', 'testing'],
}

const GLOBAL_SCOPES = {
  build: 'Root & every subproject build.gradle.kts, settings.gradle.kts, build-logic/**, gradle/libs.versions.toml, gradle/wrapper/**, gradle.properties. Exclude realty/ and hibernia-framework/ (submodules).',
  infra: 'economy-explorer/Dockerfile, treasury-rest-api/Dockerfile, .github/workflows/**, any .dockerignore, economy-explorer/next.config.ts.',
  dependencies: 'gradle/libs.versions.toml, every build.gradle.kts, economy-explorer/package.json + package-lock.json, ops/gov-export/package.json, server/bot/package.json. Exclude submodules.',
  database: 'economy-flyway/src/main/resources/db/migration/V1..V27, plus every *Mapper*.java / mapper package across treasury, business, treasury-api-plugin, treasury-rest-api, chestshop, and economy-explorer/lib/sql/** (Kysely DAL). Exclude submodules and economy-flyway/build/**.',
}
const GLOBAL_CELLS = ['build', 'infra', 'dependencies', 'database']

// Build the flat cell list.
const cells = []
for (const [component, dims] of Object.entries(COMPONENT_CELLS)) {
  for (const dimension of dims) {
    cells.push({
      component, dimension,
      scope: `Component: ${component} (type: ${COMPONENTS[component].type}). Scope paths: ${COMPONENTS[component].paths}. Stay strictly within these paths; do not audit submodules (realty/, hibernia-framework/) or build/ outputs.`,
    })
  }
}
for (const dimension of GLOBAL_CELLS) {
  cells.push({ component: 'global', dimension, scope: `GLOBAL dimension. Scope: ${GLOBAL_SCOPES[dimension]}` })
}

log(`Fanning out ${cells.length} audit cells (${Object.keys(COMPONENT_CELLS).length} components + ${GLOBAL_CELLS.length} global).`)

// ============================================================================
// Schemas.
// ============================================================================
const FINDINGS_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  required: ['findings', 'summary'],
  properties: {
    findings: {
      type: 'array',
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['seq', 'category', 'severity', 'confidence', 'file', 'lines', 'snippet', 'description', 'recommendation', 'effort'],
        properties: {
          seq: { type: 'integer' },
          category: { type: 'string' },
          severity: { type: 'string', enum: ['blocker', 'major', 'minor', 'nit'] },
          confidence: { type: 'string', enum: ['high', 'medium', 'low'] },
          file: { type: 'string' },
          lines: { type: 'array', items: { type: 'integer' }, minItems: 2, maxItems: 2 },
          snippet: { type: 'string' },
          description: { type: 'string' },
          recommendation: { type: 'string' },
          effort: { type: 'string', enum: ['trivial', 'small', 'medium', 'large'] },
          occurrences: {
            type: 'array',
            items: {
              type: 'object', additionalProperties: false,
              required: ['file', 'lines', 'snippet'],
              properties: { file: { type: 'string' }, lines: { type: 'array', items: { type: 'integer' } }, snippet: { type: 'string' } },
            },
          },
        },
      },
    },
    summary: {
      type: 'object',
      additionalProperties: false,
      required: ['files_examined', 'checks_run', 'not_applicable', 'sampled_files'],
      properties: {
        files_examined: { type: 'integer' },
        checks_run: { type: 'array', items: { type: 'string' } },
        not_applicable: { type: 'array', items: { type: 'string' } },
        sampled_files: { type: 'array', items: { type: 'string' } },
      },
    },
  },
}

const VERIFY_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  required: ['verdicts'],
  properties: {
    verdicts: {
      type: 'array',
      items: {
        type: 'object', additionalProperties: false,
        required: ['seq', 'status', 'reason'],
        properties: {
          seq: { type: 'integer' },
          status: { type: 'string', enum: ['confirmed', 'rejected', 'downgraded'] },
          reason: { type: 'string' },
          corrected_severity: { type: 'string', enum: ['blocker', 'major', 'minor', 'nit'] },
        },
      },
    },
  },
}

// ============================================================================
// Pipeline: audit -> verify (per cell, no barrier).
// ============================================================================
const results = await pipeline(
  cells,
  // Stage 1: audit
  (cell) => agent(
    `You are auditing ONE cell of a monorepo quality audit. Working directory is the repo root (/home/debian/hibernia-economy).\n\nDIMENSION: ${cell.dimension}\nCOMPONENT: ${cell.component}\n\n${cell.scope}\n\n${SHARED_RULES}\n\n${CHARTERS[cell.dimension]}\n\nExecute the charter exhaustively against your scope using the triage strategy. Read real code before every finding. Then call StructuredOutput with {findings, summary}.`,
    { label: `audit:${cell.component}/${cell.dimension}`, phase: 'Audit', schema: FINDINGS_SCHEMA }
  ).then(r => ({ cell, audit: r })),

  // Stage 2: adversarial verification of that cell's findings
  async (prev) => {
    if (!prev || !prev.audit) return prev
    const { cell, audit } = prev
    const findings = audit.findings || []
    if (findings.length === 0) return { cell, findings: [], summary: audit.summary, verdicts: [] }

    // Select: every blocker + deterministic 20% sample of the rest (every 5th by seq).
    const blockers = findings.filter(f => f.severity === 'blocker')
    const rest = findings.filter(f => f.severity !== 'blocker')
    const sampled = rest.filter((_, i) => i % 5 === 0)
    const toVerify = [...blockers, ...sampled]
    if (toVerify.length === 0) return { cell, findings, summary: audit.summary, verdicts: [] }

    const list = toVerify.map(f => `- seq ${f.seq} [${f.severity}] ${f.file}:${f.lines[0]}-${f.lines[1]} — ${f.category}: ${f.description}\n  claimed snippet: ${JSON.stringify(f.snippet).slice(0, 400)}`).join('\n')
    const verdict = await agent(
      `You are an ADVERSARIAL verifier for a monorepo audit. Working dir is the repo root. Your job is to REFUTE each finding below by re-opening the cited file and checking the claim against reality. Default to skepticism.\n\nFor each finding: (1) read the cited file at the cited lines, (2) confirm the verbatim snippet actually appears there, (3) confirm the described defect genuinely holds given surrounding context and the repo rules (ledger source-of-truth, balances via DB trigger, long minor units, treasury/rest-api dual engines, framework conventions), (4) confirm the severity is justified.\n\nMark status=confirmed only if the snippet matches AND the defect holds AND severity is fair. Mark status=rejected if the snippet is wrong, the code doesn't do what's claimed, or mitigating context elsewhere defeats it (state where). Mark status=downgraded if real but over-severe, and give corrected_severity. Always give a concrete reason citing what you read.\n\nFindings to verify:\n${list}\n\nCall StructuredOutput with {verdicts:[{seq,status,reason,corrected_severity?}]}.`,
      { label: `verify:${cell.component}/${cell.dimension}`, phase: 'Verify', schema: VERIFY_SCHEMA }
    )
    return { cell, findings, summary: audit.summary, verdicts: verdict?.verdicts || [] }
  }
)

// ============================================================================
// Emit the full dataset as the workflow return value.
// ============================================================================
const out = results.filter(Boolean).map(r => ({
  component: r.cell.component,
  dimension: r.cell.dimension,
  findings: r.findings || [],
  summary: r.summary || null,
  verdicts: r.verdicts || [],
}))

const totalFindings = out.reduce((n, c) => n + c.findings.length, 0)
const totalVerdicts = out.reduce((n, c) => n + c.verdicts.length, 0)
log(`Audit complete: ${out.length} cells, ${totalFindings} raw findings, ${totalVerdicts} verification verdicts.`)

return { cells: out }
