---
title: ChestShop configuration
order: 13
description: ChestShop's trade limits and rules — and why its sales tax is actually configured in Treasury.
---

# ChestShop configuration

ChestShop's settings live in `plugins/ChestShop/config.yml`. It's a large upstream file;
below are the settings a DemocracyCraft operator actually cares about. Stock ChestShop
reads its config at enable, so changes need a **restart**.

> [!IMPORTANT]
> **ChestShop sales tax is NOT configured here.** Because Treasury is installed, ChestShop
> hands tax off to Treasury's tax API, which collects it into Treasury's default tax
> account (`DCGovernment`). The legacy `TAX_AMOUNT` and `SERVER_ECONOMY_ACCOUNT` keys are
> **inert** on this server — leave them at `0` / empty. To change shop sales tax, edit
> **[Treasury](/docs/admin/config-treasury)**. Tax *exemptions* are by permission:
> `ChestShop.notax.buy`.

## Trade limits & fees

| Key | Default | What it does |
|---|---|---|
| `MAX_SHOP_AMOUNT` | `3456` | **Per-trade cap** — the most items one transaction can move (a double chest of stacks). |
| `SHOP_CREATION_PRICE` | `0` | Fee to create a shop. `0` = free (the live value). |
| `SHOP_REFUND_PRICE` | `0` | Money refunded when a shop sign is destroyed. |
| `PRICE_PRECISION` | `2` | Max decimal places allowed on prices. |

> [!NOTE]
> Per-item min/max price caps live in a separate file, `priceLimits.yml`, enforced at shop
> creation. It currently holds only example entries.

## Trade rules

| Key | Default | What it does |
|---|---|---|
| `BLOCK_SHOPS_WITH_SELL_PRICE_HIGHER_THAN_BUY_PRICE` | `true` | Blocks shops where sell > buy (anti money-printing). **Keep on.** |
| `ALLOW_PARTIAL_TRANSACTIONS` | `true` | Lets a trade scale down when funds/items/space are short rather than failing. |
| `REVERSE_BUTTONS` | `false` | Swaps clicks: left-click buys, right-click sells. (Default: right=buy, left=sell.) |
| `SHIFT_SELLS_IN_STACKS` | `false` | Shift-click trades in 64-stacks. |
| `SHIFT_SELLS_EVERYTHING` | `false` | Shift-click trades all matching items at once. |
| `SHOP_INTERACTION_INTERVAL` | `250` | Anti-spam cooldown between sign uses (ms). |
| `IGNORE_CREATIVE_MODE` | `true` | Blocks creative-mode players from trading. **Keep on.** |
| `ALLOW_AUTO_ITEM_FILL` | `true` | Allows `?` on the item line to fill from the held item. |

## Admin shops & naming

| Key | Default | What it does |
|---|---|---|
| `ADMIN_SHOP_NAME` | `Admin Shop` | The owner-line text that marks a sign as an unlimited admin shop. |
| `FORCE_UNLIMITED_ADMIN_SHOP` | `false` | Makes admin shops unlimited even with a container attached. |
| `TAX_AMOUNT` / `SERVER_ECONOMY_ACCOUNT` | `0` / *(empty)* | **Inert on DC** — tax is handled by Treasury (see the note above). |

## Protection

| Key | Default | What it does |
|---|---|---|
| `USE_BUILT_IN_PROTECTION` | `true` | ChestShop's own chest-destruction protection. Keep on. |
| `TURN_OFF_SIGN_PROTECTION` | `false` | **Leave `false`** — `true` lets anyone destroy others' shop signs. |
| `TURN_OFF_HOPPER_PROTECTION` | `false` | **Leave `false`** — `true` lets hoppers siphon shop chests. |
| `ALLOW_LEFT_CLICK_DESTROYING` | `true` | Owner left-clicking their own sign starts breaking it. |
| `REMOVE_EMPTY_SHOPS` | `false` | Auto-destroys a shop's sign when it runs empty. |

## Region gating

`WORLDGUARD_INTEGRATION`, `GRIEFPREVENTION_INTEGRATION`, `REDPROTECT_INTEGRATION` and the
related flags are all **off** on the live server (shops can be built anywhere allowed by
normal build perms). Turn these on if you want to restrict shop creation to claims or
regions.

## DemocracyCraft-specific behaviour (not config)

These are fork behaviours with no config key — worth knowing they exist:

- **Treasury is the backend** — every trade moves money through Treasury; ChestShop stores
  no balances.
- **Firm shops** — a `B:<code>` owner line routes a shop to a Treasury business account,
  gated by the firm's `CHESTSHOP` role permission. Legacy `b:<FirmName>` signs self-heal
  to the new format on first trade.
- **Market analytics** — every trade is recorded to Treasury's market API, which is what
  feeds this Explorer's Market pages. On whenever the API is present.
