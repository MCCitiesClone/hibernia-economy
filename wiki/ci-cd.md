# CI / CD

All automation lives at the repo root in **`.github/workflows/`** — GitHub only
reads workflows from the root, which is the natural home in a monorepo. Each
workflow is scoped to one component with **`paths:` filters**, so a change to
Treasury doesn't rebuild ChestShop and vice versa.

## Workflows

| Workflow | Trigger | Purpose |
|---|---|---|
| `treasury-build` / `business-build` / `treasury-api-plugin-build` | push/PR on the project | Build the plugin shaded jar. |
| `treasury-test` / `business-test` | push/PR | Run tests + JaCoCo against a **MariaDB service container**; post a coverage comment on PRs. |
| `treasury-publish-api` / `business-publish-api` | push to `main`/`develop` | Publish the `*-api` jar (snapshot or release) and tag/release on `main`. |
| `chestshop-test` | push/PR | Build + test ChestShop through the root build (so `treasury-api`/`business-api` resolve locally). |
| `treasury-rest-api-docker` | push | Build and push the Docker image to **Harbor**. |
| `economy-explorer-ci` | push/PR | Typecheck, lint, test (MariaDB service container), then build + push the Docker image. |
| `economy-flyway-info` / `-migrate` / `-staging-clean` | manual (`workflow_dispatch`) | Gated schema operations (see [database.md](database.md)). |

### Integration tests in CI

The Java test workflows start a **MariaDB service container** and point the suites
at it via `TREASURY_TEST_JDBC_URL` / `BUSINESS_TEST_JDBC_URL`, which makes the test
harness skip its embedded MariaDB4j path. This is more reliable than MariaDB4j on
CI runners (whose system libs don't match the bundled binary).

### Container images & deployment

`treasury-rest-api` and `economy-explorer` ship as Docker images to a **Harbor**
registry and are rolled out by **Argo CD Image Updater**. Images are tagged
`<env>-sha-<short>` per overlay, where the env prefix (`development` /
`production`) is what Argo watches:

- push to **`develop`** → `development-sha-…` (+ `:latest`)
- push to **`main`** → `production-sha-…` (+ `:prod`)

Registry credentials and Harbor coordinates come from repo/organization secrets
(`HARBOR_REGISTRY`, `HARBOR_USERNAME`, `HARBOR_PASSWORD`).

## Release flow

- Work lands on **`develop`**. The published API snapshots and the development
  container images track `develop`.
- A release is the **`develop` → `main`** pull request. Its body enumerates every
  `PAR-…` (Tesks ref) shipping in the release; merging tags/releases the API jars
  and promotes the `production` images.
- `main` exists only for these release tags — no day-to-day work happens there.

See [conventions.md](conventions.md) for the branch model and how Tesks issues move
through their workflow.

## ⚠️ Migrate the workflows to the single root build

The build was consolidated into a **single root Gradle build** (per-project
wrappers and `settings.gradle.kts` files were removed). Most of the build/test
workflows were authored for the earlier **per-project** layout — they `cd` into a
project directory (`working-directory: <project>`) and call `./gradlew`, which no
longer exists there.

They need updating to invoke the root build, e.g.:

```yaml
# before (per-project):
#   working-directory: treasury
#   run: ./gradlew --no-daemon clean shadowJar -Pci=true

# after (single root build):
- run: ./gradlew --no-daemon :treasury:shadowJar -Pci=true
```

`chestshop-test` already uses the root form (`./gradlew :chestshop:plugin:test`)
and is the pattern to follow. The `paths:` filters, service containers, and
Harbor/Argo steps stay as they are. This is a known follow-up.
