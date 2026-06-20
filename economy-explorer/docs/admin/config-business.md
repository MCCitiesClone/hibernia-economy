---
title: Business configuration
order: 12
description: Business's config.yml — the firm ownership limit and the weekly firm balance tax.
---

# Business configuration

The Business (firms) plugin's settings live in `plugins/Business/config.yml`. It's a small
file — three sections: `database`, `firm`, and `tax`.

> [!CAUTION]
> **`/business reload` only refreshes message strings.** It does *not* re-read the firm
> limit, the tax brackets, or the database — those are captured once at startup. Any real
> config change needs a **server restart**.

## Firm ownership — `firm`

| Key | Default | What it does |
|---|---|---|
| `firm.owned-limit` | `3` | Maximum firms a single player may **own** (be proprietor of). `0` or less = unlimited. |
| `firm.create-cooldown-seconds` | `300` | Minimum seconds between a player creating firms (counts **disbanded** firms too, so rapid create/disband cycles still wait). `0` or less = disabled. |

Notes:

- Counts only firms you're the **proprietor** of, not firms you're employed at.
- Enforced **only at creation** — a player can end up over the limit by receiving a firm
  transfer, but can't `create` a new one while at or over it.

## Firm balance tax — `tax.balance`

A weekly wealth tax on each firm's total balance, charged on Treasury's **weekly** tax
cycle.

| Key | Default | What it does |
|---|---|---|
| `tax.balance.enabled` | `true` | Master switch. **Deleting the key defaults it to `false`** — set it explicitly. |
| `tax.balance.government-account` | `DCGovernment` | Government account the tax is paid into (falls back to Treasury's default account if missing). |
| `tax.balance.brackets` | *(see below)* | Balance-floor → weekly-rate table. |

Shipped brackets (flat, applied to the firm's **whole** balance — not marginal):

| Firm balance from | Weekly rate |
|---|---|
| $0 | 0% |
| $100,000 | 1.0% |
| $200,000 | 1.2% |
| $300,000 | 1.4% |
| $500,000 | 1.8% |

> [!WARNING]
> **Bracket keys must be integers — decimal keys are silently broken.** Business loads the
> brackets via `getConfigurationSection("tax.balance.brackets").getKeys(false)` and parses
> each key as the balance floor. Bukkit/SnakeYAML treats a `.` in a YAML mapping key as a
> **path separator**, so a decimal key like `100000.00:` is read as a nested section
> (`100000` → `00`) rather than a scalar key — its rate **never applies**. Always use whole
> integer threshold keys; the value is the **weekly rate** at that balance floor:
>
> ```yaml
> tax:
>   balance:
>     brackets:
>       0: 0.0
>       100000: 0.01
>       200000: 0.012
>       300000: 0.014
>       500000: 0.018
> ```

How it's charged: the tax is `total firm balance × rate`, split proportionally across the
firm's accounts (rounding drift folded into the largest account), with a dedup key so a
re-fired cycle can't double-charge. A firm whose total balance is zero or negative is
skipped.

> [!NOTE]
> Emptying the bracket map doesn't disable the tax — it silently reverts to these
> defaults. Use `enabled: false` to turn it off. Individual firms can be exempted in-game
> with `/business tax exempt <firm> true` (this is a per-firm flag stored in the database,
> not a config key); check a firm's status with `/business tax info <firm>`.

## Database — `database`

`host`, `port`, `database`, `username`, `password`. Business **shares Treasury's
database** (default schema `treasury`) — don't point it elsewhere or firm/account links
break. The connection pool and `table-prefix` are not meaningfully tunable
(`table-prefix` is read but unused). Database changes need a restart.

## Not configurable

There's **no firm-creation fee, account-creation fee, or configurable role/permission
defaults** — those behaviours are fixed in code. The only tax is the weekly balance tax
above.
