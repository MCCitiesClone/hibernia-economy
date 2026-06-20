---
title: Plugin configuration
order: 10
description: Where each economy plugin's config lives, how reloads work, and what every setting does.
---

# Plugin configuration

A reference to the configuration files behind the economy plugins — **Treasury**,
**Business**, **ChestShop**, and **Realty** — and what each setting does. These are
server-side files (`plugins/<Plugin>/…`), edited on the host, not from the website.

> [!CAUTION]
> Treasury is the economy **system of record**. A bad value in `database.*` or a malformed
> currency/format pattern can stop a plugin from enabling. Change one thing at a time and
> keep a backup of each file.

## Per-plugin guides

- **[Treasury configuration](/docs/admin/config-treasury)** — accounts, currency, tax
  cycles, balance/income tax, salaries, database.
- **[Business configuration](/docs/admin/config-business)** — firm ownership limit and the
  weekly firm balance tax.
- **[ChestShop configuration](/docs/admin/config-chestshop)** — trade limits and rules
  (note: ChestShop **sales tax is configured in Treasury**, not here).
- **[Realty configuration](/docs/admin/config-realty)** — property **tax** (the formula,
  brackets, and exemption), plus leases, signs, and tags.

## When changes take effect

Reload behaviour differs by plugin — this trips people up, so check before you assume an
edit is live:

| Plugin | How to apply a change |
|---|---|
| **Treasury** | **Restart only.** There is no reload command; config is read once at enable and cached. |
| **Business** | **Restart for real changes.** `/business reload` only refreshes message strings — it does *not* re-read the tax brackets, firm limit, or database. |
| **ChestShop** | Restart (stock ChestShop reads its config at enable). |
| **Realty** | **`/realty reload` applies most changes live**, including `taxes.yml` (it takes effect on the next tax cycle). Database changes still need a restart. |

## How the pieces fit together

- **Treasury** owns all money and runs the **tax cycles** (daily/weekly/monthly). It only
  *fires* a cycle event on schedule; the other plugins **listen** and collect their own
  tax during it. So a cycle does nothing unless something is registered for it.
- **Realty** property tax runs on Treasury's **daily** cycle. **Business** balance tax runs
  on the **weekly** cycle. Both pay into a Treasury government account (default
  `DCGovernment`).
- **ChestShop** stores no money — every trade moves funds through Treasury, and ChestShop
  sales tax is collected by Treasury's tax API into Treasury's default tax account.
