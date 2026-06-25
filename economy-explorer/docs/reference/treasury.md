---
title: Treasury
order: 1
description: Money commands — paying, balances, transaction history, government accounts, and fines.
---

# Treasury

**Treasury** is the server's money system — the official record of every balance and
every transaction. When you pay someone, earn a salary, sell at a shop, or owe a fine,
Treasury is what moves the money and writes it down. It's the foundation the rest of the
economy is built on, and it's the data this Explorer reads.

**Made by** Paradaux, built in-house for the Minecraft Cities Network (DemocracyCraft &
StateCraft) as the single, trustworthy ledger for the whole economy.

The commands below cover checking balances, paying people, reviewing your history, and
— if you're in government — managing department accounts and fines.

> [!NOTE]
> `<angle brackets>` are required, `[square brackets]` are optional. Don't type the
> brackets.

## Everyday commands

Everyone can use these.

### `/pay <player> <amount>`

Send money to another player.

```text
/pay Steve 250
```

| Argument | Required | What it is |
|---|---|---|
| `player` | yes | Who you're paying |
| `amount` | yes | How much (more than 0) |

You can also pay a government account by its name instead of a player.

### `/pay-account <type> <name|id> <amount> [memo]`

Pay an **explicitly-typed** account. Where `/pay` guesses (and resolves a government
account ahead of a same-named player), `/pay-account` lets you say exactly which kind of
account you mean — handy for paying a business, or an account by its numeric ID. Also
answers to `/paya`.

```text
/pay-account business Acme 500 invoice
/pay-account account 42 100
```

| Argument | Required | What it is |
|---|---|---|
| `type` | yes | `player`, `business`, `government`, or `account` (by ID) |
| `name\|id` | yes | The account's name, or its numeric ID for `account` |
| `amount` | yes | How much (more than 0) |
| `memo` | no | A note recorded on the transaction |

### `/bal [player]`

Check a balance. With no name, it shows yours. Also answers to `/balance` and
`/money`.

```text
/bal
/bal Steve
```

| Argument | Required | What it is |
|---|---|---|
| `player` | no | Whose balance to check (defaults to you) |

> [!NOTE]
> Checking **someone else's** balance is staff-only.

### `/baltop [page]`

See the richest players on the server, ten per page.

```text
/baltop
/baltop 2
```

| Argument | Required | What it is |
|---|---|---|
| `page` | no | Which page to view (defaults to 1) |

### `/transactions [page]`

Your money history — what came in, what went out, and why. Also answers to `/txns`.

```text
/transactions
/transactions 3
```

| Argument | Required | What it is |
|---|---|---|
| `page` | no | Which page to view (defaults to 1) |

Need it as a file? Export a copy:

```text
/transactions export
```

> [!TIP]
> `/transactions export <accountId>` exports the history of a shared account you're a
> member of (such as a firm or government account).

### Staff: auditing other people's history

Staff can look up anyone's transaction history, by player or by account ID.

```text
/transactions audit Steve
/transactions audit Steve 2
/transactions auditaccount 42
/transactions auditaccount 42 3
```

| Command | What it does |
|---|---|
| `/transactions audit <player> [page]` | View any player's history |
| `/transactions auditaccount <accountId> [page]` | View any account's history by its numeric ID |

> [!NOTE]
> Both require `treasury.transactions.audit` (staff-only).

## Government accounts

Government **departments** share accounts that several people can use. You can only
see and use an account if you've been added to it. Two levels of access:

- **Members** can view the account and its history **and spend from it** (pay/payout).
- **Authorizers** can do the same **and manage who has access** (add/remove members
  and authorizers).

All of these live under `/government` (also `/gov`).

> [!NOTE]
> `/government` (and `/fine`) are gated by a **command-level** permission first
> (`treasury.gov` / `treasury.gov.fine`), and *then* by the per-action runtime nodes
> below. Granting only a child node won't work if the player can't reach the command —
> they need the base node too.

### `/government account list`

List the government accounts you can see, with balances.

```text
/government account list
```

### `/government account balance <name>`

Show one account's balance.

```text
/government account balance Treasury
```

| Argument | Required | What it is |
|---|---|---|
| `name` | yes | The account's name |

### `/government account history <name> [page]`

Review an account's transaction history.

```text
/government account history Treasury
/government account history Treasury 2
```

| Argument | Required | What it is |
|---|---|---|
| `name` | yes | The account's name |
| `page` | no | Which page to view (defaults to 1) |

### `/government account transfer <from> <to> <amount> [reason]`

Move money **between two government accounts**. `/government pay …` is an alias for the
same command.

```text
/government account transfer Treasury Roads 10000 Q3 budget
/government pay Treasury Roads 10000 Q3 budget
```

| Argument | Required | What it is |
|---|---|---|
| `from` | yes | Account to take money from |
| `to` | yes | Account to send money to |
| `amount` | yes | How much (more than 0) |
| `reason` | no | A note saved with the transfer |

> [!NOTE]
> You must be a **member or authorizer** of the `from` account, or hold
> `treasury.gov.admin` (which overrides the access check).

### `/government payout <from> <to> <amount> [reason]`

Pay a **player or a business** out of a government account — grants, contracts,
salaries.

```text
/government payout Roads Steve 1500 paving contract
```

| Argument | Required | What it is |
|---|---|---|
| `from` | yes | Government account to pay from |
| `to` | yes | A player name or a business account |
| `amount` | yes | How much (more than 0) |
| `reason` | no | A note saved with the payment |

> [!NOTE]
> You must be a **member or authorizer** of the `from` account, or hold
> `treasury.gov.admin` (which overrides the access check).

### Managing who has access

Add or remove the people who can use an account. Members can view and spend;
authorizers can also manage access. (An authorizer must be a member first.)

```text
/government account member add Treasury Steve
/government account member list Treasury
/government account auth add Treasury Steve
/government account auth list Treasury
```

Use `remove` in place of `add` to revoke access. Managing members needs account
management rights; managing authorizers is admin-only.

You can also grant access to a whole **LuckPerms group** at once, rather than naming
players one by one:

```text
/government account member addgroup Treasury treasury-staff
/government account member removegroup Treasury treasury-staff
/government account auth addgroup Treasury treasury-admins
/government account auth removegroup Treasury treasury-admins
```

### Creating and retiring accounts

```text
/government account create Parks
/government account archive Parks
```

Creating an account may be limited to certain roles, and archiving is admin-only.

## Fines

Government fines are issued and managed with `/fine`.

### `/fine issue <account> <player> <amount> <reason>`

Fine a player into a **government account**. The amount is taken from the player's
balance and paid into the named account.

```text
/fine issue Police Steve 500 griefing spawn
```

| Argument | Required | What it is |
|---|---|---|
| `account` | yes | The government account the fine is paid into |
| `player` | yes | Who is being fined |
| `amount` | yes | How much (more than 0) |
| `reason` | yes | Why the fine was issued |

You must have access to that account to fine into it — a **member or authorizer** of
it, or an admin. (Same access model as `/government pay`.)

### `/fine revoke <id>` · `/fine info <id>` · `/fine list [player]`

Look up, list, and reverse fines by their ID. Revoking refunds the fine **out of the
account it was paid into**, so you must have access to that account to revoke it.

```text
/fine list
/fine list Steve
/fine info 42
/fine revoke 42
```

> [!NOTE]
> Issuing and revoking are gated by access to the fine's account (see above). Viewing
> (`list`/`info`) needs `treasury.gov.fine` (to use `/fine` at all) **plus**
> `treasury.gov.fine.view`.

## Admin commands

These are for server operators.

### `/eco <give|take|set|reset> <target> [amount]`

Adjust a balance directly. Also answers to `/economy`.

```text
/eco give Steve 1000
/eco take Steve 250
/eco set Steve 0
/eco reset Steve
/eco give DCGovernment 100000
```

| Argument | Required | What it is |
|---|---|---|
| action | yes | `give`, `take`, `set`, or `reset` |
| `target` | yes | A player, or a **government account** name (`give`/`take`/`set` only) |
| `amount` | for give/take/set | How much (`reset` takes none) |

> [!NOTE]
> `give`, `take`, and `set` also accept a **government account** name as the target — a
> same-named government account takes precedence over a player. `reset` works on players
> only (it restores the configured starting balance).

> [!CAUTION]
> `/eco` creates or destroys money outside the normal economy. Operators only.

### `/tax <status|trigger> [cycle]`

Inspect and test the automatic tax cycles.

```text
/tax status
/tax trigger weekly
```

| Argument | Required | What it is |
|---|---|---|
| action | yes | `status`, or `trigger` |
| `cycle` | for trigger | `daily`, `weekly`, or `monthly` |

> [!NOTE]
> `/tax status` shows when each cycle next runs. `/tax trigger <cycle>` fires one early
> for testing. Operators only.

### `/treasury` (alias `/tr`)

The operator console for the plugin itself — reloading config and low-level account
admin.

```text
/treasury reload
/treasury admin ingest <source>
/treasury admin transfer <fromType> <from> <toType> <to> <amount> [reason]
/treasury admin balance <type> <id>
/treasury admin info <type> <id>
```

| Command | What it does |
|---|---|
| `/treasury reload` | Reload the config and messages |
| `/treasury admin ingest <source>` | Run an ingest from the given source |
| `/treasury admin transfer <fromType> <from> <toType> <to> <amount> [reason]` | Move money between any two accounts by type + id/name |
| `/treasury admin balance <type> <id>` | Show an account's balance by type + id |
| `/treasury admin info <type> <id>` | Show an account's details by type + id |

> [!CAUTION]
> `/treasury admin transfer` moves money between accounts directly, bypassing the
> usual access checks. Operators only.

## Permission nodes

The LuckPerms nodes that gate each command, with their `plugin.yml` defaults
(`true` = everyone, `op` = operators only, `false` = nobody until granted). On the
government accounts, the **member/authorizer** checks apply *in addition* to these —
see [Two levels of access](#government-accounts).

### Everyday

| Command | Node | Default |
|---|---|---|
| `/pay` | `treasury.pay` | `true` |
| `/bal` (your own) | `treasury.balance` | `true` |
| `/bal <player>` (someone else) | `treasury.balance.others` | `op` |
| `/baltop` | `treasury.baltop` | `true` |
| `/transactions` | `treasury.transactions` | `true` |
| `/transactions export` | `treasury.transactions.export` | `true` |
| `/transactions audit` · `auditaccount` | `treasury.transactions.audit` | `op` |

### Government accounts & fines

| Command | Node | Default |
|---|---|---|
| `/government …` (base command) | `treasury.gov` | `op` |
| `/government account list` | `treasury.gov.account.list` | `false` |
| `/government account balance` · `history` · `member list` · `auth list` | `treasury.gov.account.view` | `false` |
| `/government pay` · `payout` | `treasury.gov.account.transfer` *(or be a member/authorizer of the account)* | `false` |
| `/government account member add` · `remove` · `addgroup` · `removegroup` | `treasury.gov.account.manage` *(and authorizer access)* | `false` |
| `/government account auth add` · `remove` · `addgroup` · `removegroup` | `treasury.gov.admin` | `op` |
| `/government account create` | `treasury.gov.account.create` | `false` |
| `/government account archive` | `treasury.gov.admin` | `op` |
| `/fine …` (base command) | `treasury.gov.fine` | `op` |
| `/fine issue` · `revoke` | `treasury.gov.fine` *(plus member/authorizer access to the account, or `treasury.gov.admin`)* | `op` |
| `/fine list` · `info` | `treasury.gov.fine.view` *(plus `treasury.gov.fine` to reach the command)* | `false` |

### Admin

| Command | Node | Default |
|---|---|---|
| `/eco give` · `take` · `set` · `reset` | `treasury.eco.give` / `.take` / `.set` / `.reset` (base `treasury.eco`) | `op` |
| `/tax status` · `trigger` | `treasury.admin.tax` | `op` |
| `/treasury …` (base command) | `treasury.command` | `true` |
| `/treasury reload` | `treasury.admin.reload` | `op` |
| `/treasury admin transfer` | `treasury.admin.transfer` | `op` |
| `/treasury admin balance` · `info` | `treasury.admin.inspect` | `op` |
| `/treasury admin ingest` | `treasury.admin.ingest` | `op` |

> [!TIP]
> `treasury.gov` lets a player run the `/government` command; the individual
> `treasury.gov.account.*` nodes (default `false`) then decide what they can do.
> `treasury.gov.*` is a wildcard that grants every `treasury.gov.…` child at once.
> `treasury.gov.account.transfer` is a server-wide grant to spend from **any**
> government account — for normal department staff, add them as a member or authorizer
> of the specific account instead.
