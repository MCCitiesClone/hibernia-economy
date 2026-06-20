---
title: Realty configuration
order: 14
description: The Realty property-tax formula and exemption, plus leases, signs, and tags. Config lives in plugins/Realty/.
---

# Realty configuration

Realty's config files live in `plugins/Realty/`. Unlike the other plugins, **`/realty
reload` applies most changes live** — it re-reads every file below and swaps them in
atomically; `taxes.yml` changes take effect on the **next tax cycle** with no restart.
(The database file still needs a restart.)

Files: `taxes.yml`, `settings.yml`, `region-tags.yml`, `profiles.yml`, `messages.yml`,
`database.yml`.

---

# Property tax

This is the big one. Realty charges a **daily property tax** on freehold (owned) plots.
It runs on Treasury's **daily** tax cycle, is evaluated for **every owner in the
database** (online or not), and pays into a Treasury government account. All of it is
configured in **`taxes.yml`**.

## `taxes.yml` settings

| Key | Default (shipped) | On DemocracyCraft | What it does |
|---|---|---|---|
| `enabled` | `true` | `true` | Master switch. Off → no property tax is charged. Requires Treasury with its daily cycle enabled. |
| `government-account` | `DCGovernment` | `DCGovernment` | Treasury government account the tax is paid into. If the named account is missing, it falls back to Treasury's default tax account (with a warning). |
| `exempt-uuids` | *(empty)* | — | List of owner UUIDs that are skipped entirely — for authority/government/server-held regions. |
| `exempt-plot-threshold` | `7` | **`2`** (live override) | Owners holding **this many plots or fewer pay nothing**. Shipped default is **7**; the live server **overrides** this to **2**. |
| `default-formula` | *(see below)* | *(may be overridden)* | The federal tax formula — an owner's total daily tax as a function of plot count. The live server may override the shipped formula too. |
| `rules` | *(empty)* | *(empty)* | Optional per-tag/per-region formula overrides (local government). Off by default — see [Local overrides](#local-government-overrides-rules). |

## The formula

```text
0.25 * 1.16^<plots> + 0.3 * <plots>^2 + 2.5 * <plots> - 25
```

- `<plots>` is the owner's **total freehold plot count** (the number of WorldGuard
  regions they title-hold, server-wide).
- It's evaluated **once per owner**, on their total — **not** per plot and not per region.
- Supported maths: `+ - * /`, exponent `^`, parentheses, decimals. The result is **rounded
  down to the cent**.

Two things make small landholders pay nothing:

1. **The exemption threshold.** Owners at or under `exempt-plot-threshold` always pay $0 —
   the formula isn't even evaluated. The shipped default is **7**; the live server
   currently overrides this to **2**, so on DemocracyCraft owning **2 or fewer** plots is
   always $0.
2. **The `- 25` term.** The formula is *negative* for low plot counts, and a result of
   zero or below is treated as **$0 — never a credit and never debt**. With the default
   formula it only turns positive at around **7 plots**.

So in practice, with the shipped formula, an owner starts paying tax once they hold about
**7** plots, and it climbs steeply from there (the `1.16^<plots>` term is exponential).

## What it actually costs (worked example)

Daily tax for the **shipped formula** at DemocracyCraft's **current live 2-plot exemption**
(values floored to the cent; the $15.01 and $149.86 anchors match the plugin's own tests).
These numbers reflect the **current live config** — if the live server changes
`exempt-plot-threshold` or `default-formula`, they shift accordingly; they are not
permanent constants:

| Plots held | Daily tax |
|---:|---:|
| 1–2 | $0.00 (exempt) |
| 3 | $0.00 (formula still negative) |
| 5 | $0.00 (formula still negative) |
| 7 | $7.90 |
| 8 | $15.01 |
| 10 | $31.10 |
| 15 | $82.31 |
| 20 | $149.86 |
| 30 | $341.46 |
| 50 | $1,267.67 |

Because the exponential term dominates, each extra plot past ~20 costs dramatically more
than the last — the formula is deliberately a soft cap on hoarding land.

## How it's charged

- **When:** once per Treasury **daily** cycle (configured in
  [Treasury's tax cycles](/docs/admin/config-treasury)).
- **Who:** every freehold owner in the database, **including offline players**.
- **From:** the owner's **personal** Treasury account (or their first account if they have
  no personal one). Owners with no Treasury account are skipped with a warning.
- **Affordability:** Realty just submits the charge to Treasury and records whether each
  one was *collected*, *skipped*, or *failed*. Whether an owner who can't pay goes into
  debt or is skipped is **Treasury's** behaviour, not Realty's — Realty does not evict for
  unpaid tax.
- **Safety:** each daily charge carries a dedup key, so re-firing a cycle won't
  double-charge.

## Local-government overrides (`rules`)

`rules` lets a local government tax certain regions differently. Each rule has a **match**
(by [region tag](#region-tags-region-tagsyml)) and its own **formula**:

```yaml
rules:
  - match:
      any: [commercial]      # region carries the "commercial" tag…
    formula: "5 * <plots>"   # …taxed by this formula instead
```

- `all: [...]` requires every listed tag; `any: [...]` requires at least one; an empty
  match is a catch-all. **First matching rule wins**, top to bottom.
- A plot matched by a rule is taxed by that rule's formula and is **removed from the
  federal count**, so it doesn't also get hit by `default-formula`. The federal and
  rule-based amounts are summed.
- A rule whose formula fails to parse is dropped (with a warning) and its plots fall back
  to the federal formula.

## Tuning the tax

- **Make it bite sooner / later:** raise or lower `exempt-plot-threshold`, or adjust the
  `- 25` constant (more negative = the formula turns positive at a higher plot count).
- **Flatten the curve:** shrink the `1.16^<plots>` base (e.g. `1.08`) or the `0.3 *
  <plots>^2` coefficient — both drive the steep growth.
- **Change where the money goes:** point `government-account` at a different Treasury
  government account.
- After editing, run **`/realty reload`** — it takes effect on the next daily cycle.

---

# Other settings

## `settings.yml`

| Key | Default | What it does |
|---|---|---|
| `default-freehold-authority-uuid` | `0000…0000` | Default authority UUID applied to new freehold regions. |
| `default-leasehold-authority-uuid` | `0000…0000` | Default authority UUID for new leasehold regions. |
| `default-freehold-titleholder-uuid` | *(unset)* | Optional default title-holder for new freeholds. |
| `date-format` | `EEE, d MMMM yyyy (HH:mm)` | Date format used in messages and on signs. |
| `offer-payment-duration-seconds` | `86400` (24h) | How long a buyer has to pay after an offer is accepted before it expires and refunds. |
| `subregion-min-volume` | `20` | Minimum block volume for a subregion selection. |
| `subregion-tag-blacklist` | *(empty)* | Regions with any of these tags can't be used as a subregion parent. |
| `profile-reapply-per-tick` | `10` | How many region profiles are re-applied per tick on startup/reload (lower = less lag). |

> [!NOTE]
> Lease **term, price, and max-extensions** are set per-plot with commands (`/realty
> create leasehold …`, `/realty set duration`, etc.), not in config. The expiry sweep runs
> every minute and is not configurable. Expired leases return the plot to the landlord and
> notify both parties; ending a lease early (`/realty unrent`) gives a prorated refund.

## Region tags (`region-tags.yml`)

Defines the tag vocabulary used for searching and for tax `rules`. Each tag has a display
name and a permission node controlling who can apply it. Ships with three (OP-only by
default):

| Tag | Permission |
|---|---|
| `residential` | `realty.tag.residential` |
| `commercial` | `realty.tag.commercial` |
| `industrial` | `realty.tag.industrial` |

## Signs (`profiles.yml`)

Realty signs aren't a standalone config — they're defined inside **region flag profiles**
in `profiles.yml`, layered by plot state (`FOR_SALE`, `SOLD`, `FOR_LEASE`, `LEASED`). A
profile can set WorldGuard flags/priority and a `sign:` block with up to four MiniMessage
`lines:` (placeholders like `<region>`, `<price>`, `<duration>`, `<time_left>`) plus
right/left-click commands. **`profiles.yml` ships fully commented out** — no profiles are
active until you add them.

## Database (`database.yml`)

`url`, `username`, `password` for the MariaDB connection. Empty `url` disables DB-backed
features. Database changes need a **restart** (not just `/realty reload`).
