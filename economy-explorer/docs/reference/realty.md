---
title: Realty
order: 4
description: Every /realty command — buying, renting, offers, auctions, and managing your plots.
---

# Realty

**Realty** handles land — buying, renting, auctioning, and managing plots. It's an
open-source plugin created by **md5sha256**, run on our servers and wired into the
economy: purchases and rent move real money through
[Treasury](/docs/reference/treasury), including property tax.

The commands below cover everything from finding a plot to managing one you own. The
base command is `/realty`.

> [!NOTE]
> Most commands act on the plot you're **standing in**. Where a command lists an
> optional `[region]`, you can name a specific plot instead of using your location.

## Looking around

| Command | What it does |
|---|---|
| `/realty info [region]` | Show a plot's type, owner, price, and members. |
| `/realty list [owned\|rented]` | List plots — yours by default, or someone else's with `--player <name>` (paginated with `--page <n>`). |
| `/realty me [--page <n>]` | List all the plots you own or rent. |
| `/realty search` | Open the search dialog to browse available plots (filter by price, tags, type, occupancy). |
| `/realty history [region]` | Show a plot's transaction history (filter with `--player`, `--event`, `--time`; paginate with `--page`). |
| `/realty help` | Show in-game help. |
| `/realty version` | Show the plugin version. |

## Getting a plot

| Command | What it does |
|---|---|
| `/realty buy [region]` | Buy a plot outright at its sale price. |
| `/realty rent [region]` | Rent a plot for its set period. |
| `/realty extend [region]` | Extend your rental before it expires. |
| `/realty unrent [region]` | End **your own** rental early and get a prorated refund. |

```text
/realty info
/realty buy
```

> [!WARNING]
> When a rental expires you lose access and the plot returns to the landlord. Use
> `/realty extend` in good time.

## Offers

Propose your own price when a plot isn't on sale at a fixed price.

| Command | What it does |
|---|---|
| `/realty offer send <price> [region]` | Offer to buy a plot at your price. |
| `/realty offer pay <amount> [region]` | Pay toward an offer you've made. |
| `/realty offer withdraw [region]` | Take back an offer. |
| `/realty offer outbox` | List offers you've made. |
| `/realty offer inbox` | List offers made on your plots. |
| `/realty offer accept <player> [region]` | Accept an offer (you're the owner). |
| `/realty offer reject <player> [region]` | Reject one player's offer. |
| `/realty offer rejectall [region]` | Reject every outstanding offer on your plot. |
| `/realty offer toggle <enabled> [region]` | Turn offers on or off for your plot. |

```text
/realty offer send 50000
/realty offer pay 50000
```

## Auctions

| Command | What it does |
|---|---|
| `/realty auction info [region]` | Show an auction's bids and deadline. |
| `/realty auction bid <amount> [region]` | Place a bid. |
| `/realty auction paybid <amount> [region]` | Pay toward your winning bid. |
| `/realty auction <bidDuration> <paymentDuration> <minBid> <minBidStep> [region]` | Start an auction (landlord). |
| `/realty auction cancel [region]` | Cancel an auction (landlord). |

Times use a short format like `30m`, `1h`, or `2d`.

```text
/realty auction info
/realty auction bid 12000
```

## Managing your plot

| Command | What it does |
|---|---|
| `/realty set price <value> [region]` | Set the sale price. |
| `/realty set duration <value> [region]` | Set the rental period. |
| `/realty set landlord <player> [region]` | Set the landlord. |
| `/realty set authority <player> [region]` | Set who can manage the plot. |
| `/realty set titleholder <player> [region]` | Set the owner. |
| `/realty set tenant <player> [region]` | Set the tenant. |
| `/realty set maxextensions <value> [region]` | Limit how many times a rental can be extended. |
| `/realty unset <price\|titleholder\|tenant> [region]` | Clear one of those settings. |
| `/realty add <player> [region]` | Add a player to the plot. |
| `/realty remove <player> [region]` | Remove a player from the plot. |
| `/realty transfer <player> [region]` | Transfer ownership. |

> [!NOTE]
> These are for plot owners and authorities; some are limited to staff.

### Agents (sanctioned auctioneers)

An **agent** is authorised to run auctions and handle offers on your plot for you. Agents
are *not* added as plot members — they can't build or change the plot, only manage its
auctions and offers.

| Command | What it does |
|---|---|
| `/realty agent invite <player> [region]` | Invite a player to act as your agent. |
| `/realty agent invite accept [region]` | Accept an agent invite. |
| `/realty agent invite reject [region]` | Reject an agent invite. |
| `/realty agent invite withdraw <player> [region]` | Withdraw an invite you sent. |
| `/realty agent remove <player> [region]` | Remove an agent. |

### Tags & signs

| Command | What it does |
|---|---|
| `/realty tag add <tag> [region]` | Tag a plot (for searching). |
| `/realty tag remove <tag> [region]` | Remove a tag. |
| `/realty tag list` | List the tags you can apply. |
| `/realty tag clear [region]` | Clear all tags. |
| `/realty sign place <region>` | Place an info sign for a plot. |
| `/realty sign remove` | Remove an info sign (the one you're looking at). |
| `/realty sign list [region]` | List a plot's signs. |

## Staff & admin

| Command | What it does |
|---|---|
| `/realty create leasehold <name> <price> <duration> <maxExtensions> [--landlord <player>]` | Create and register a rentable plot. |
| `/realty create freehold <name> [--price <price>] [--titleholder <player>] [--authority <player>]` | Create and register a for-sale plot. |
| `/realty register leasehold <price> <duration> <maxExtensions> [--landlord <player>] [region]` | Register an existing region as a rentable plot. |
| `/realty register freehold [--price <price>] [--titleholder <player>] [--authority <player>] [region]` | Register an existing region as a for-sale plot. |
| `/realty delete <region> [includeworldguard]` | Remove a plot from Realty (optionally delete the WorldGuard region too). |
| `/realty subregion quickcreate <parentRegion> <name> <price> <duration>` | Create a rentable sub-plot of a parent region. |
| `/realty tp <region>` | Teleport to a plot. |
| `/realty cleanup tags` | Remove unused tags. |
| `/realty reload` | Reload configuration. |

## Permission nodes

Realty nodes follow the command path: **`realty.command.<path>`**. For example
`/realty buy` → `realty.command.buy`, `/realty offer send` → `realty.command.offer.send`,
`/realty agent invite accept` → `realty.command.agent.invite.accept`. (`/realty version`
is the only command with no permission — it's open to all.)

| Area | Nodes |
|---|---|
| Looking around | `realty.command.info` · `.list` · `.search` · `.history` · `.help` |
| Getting a plot | `realty.command.buy` · `.rent` · `.extend` · `.unrent` |
| Offers | `realty.command.offer.send` · `.pay` · `.withdraw` · `.inbox` · `.outbox` · `.accept` · `.reject` · `.toggle` *(`rejectall` uses `.reject`)* |
| Auctions | `realty.command.auction` *(start)* · `.auction.info` · `.bid` · `.paybid` · `.cancel` |
| Managing a plot | `realty.command.set.price` · `.duration` · `.landlord` · `.authority` · `.titleholder` · `.tenant` · `.maxextensions`; `realty.command.unset.price` · `.titleholder` · `.tenant`; `realty.command.add` · `.remove` · `.transfer` |
| Agents | `realty.command.agent.invite` · `.invite.accept` · `.invite.reject` · `.invite.withdraw` · `.remove` |
| Tags & signs | `realty.command.tag.add` · `.remove` · `.list` · `.clear`; `realty.command.sign.place` · `.remove` · `.list` |
| Staff & admin | `realty.command.create.leasehold` · `.create.freehold` · `.register.leasehold` · `.register.freehold` · `.delete` · `.subregion.quickcreate` · `.tp` · `.cleanup.tags` · `.reload` |

> [!NOTE]
> Several commands have an **`.others`** variant that lets staff act on plots they
> don't own — e.g. `realty.command.set.price.others`, `realty.command.add.others`,
> `realty.command.transfer.others` (and the same for the other `set`/`unset` nodes).
> There are also bypass nodes: `realty.command.offer.toggle.bypass`,
> `realty.command.subregion.quickcreate.bypass`, and
> `realty.command.delete.includeworldguard`.
