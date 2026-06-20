# Security Policy

Hibernia Economy moves real in-game money and exposes an authenticated HTTP API,
so we take security reports seriously. Thank you for helping keep it safe.

## Reporting a vulnerability

**Please do not report security issues in public** — not in a public Discord
channel, not in a pull request, and not via a Tesks issue that others can see.

Instead, report it privately:

- **Preferred:** message a maintainer directly on the
  **[Hibernia Economy Discord](https://discord.gg/b88K3ETeHS)** and ask to open a
  private security report. We'll move the conversation somewhere confidential.

When you report, please include as much of the following as you can:

- The affected component (e.g. `treasury`, `treasury-rest-api`, `economy-explorer`).
- A description of the issue and its impact.
- Steps to reproduce, or a proof of concept.
- Any relevant versions, configuration, or logs (with secrets redacted).

## What to expect

- We'll acknowledge your report and confirm we're looking into it.
- We'll work with you to understand and validate the issue, keeping you updated on
  progress and remediation.
- Once a fix has shipped, we're happy to credit you (or keep you anonymous — your
  choice).

Please give us a reasonable opportunity to fix the issue before any public
disclosure.

## Scope

Things especially worth reporting:

- Ways to move, mint, or destroy money **outside** the ledger's double-entry
  guarantees, or to bypass `TreasuryApi` authorization/overdraft checks.
- Authentication or authorization flaws in the **REST API** or the **JWT API
  keys** issued by `treasury-api-plugin` (e.g. forgeable tokens, key reuse,
  rate-limit bypass).
- SQL injection or unsafe data access against the shared economy database.
- Leakage of secrets, other players' private data, or admin-only information via
  the **Economy Explorer**.
- Privilege escalation through Business roles/permissions or government accounts.

## Please don't

- Don't run denial-of-service tests against live servers or the public API.
- Don't access, modify, or exfiltrate data that isn't yours.
- Don't disclose the issue publicly until a fix is available.

## Secrets

If you ever find a credential, key, or token committed to this repository,
**treat it as compromised** and report it privately so it can be rotated — do not
open a public issue or PR that quotes it.
