---
title: Government accounts
order: 7
description: For department members — view, spend from, and manage shared government accounts, and issue fines.
---

# Government department accounts

Government **departments** run on shared accounts that several people can use together.
This guide is for players who've been added to a department account. If you haven't,
these commands simply won't do anything for you yet.

All of them live under `/government` (you can shorten it to `/gov`).

## See what you can access

```text
/government account list
```

This lists the government accounts you're allowed to see, with their balances. Check
one in detail, or read its history:

```text
/government account balance Treasury
/government account history Treasury
```

## Two levels of access

Every government account has two kinds of access:

- **Members** can *view* the account and its history **and spend from it** (transfer
  and payout).
- **Authorizers** can do everything a member can, **plus manage who has access** —
  adding and removing other members and authorizers.

So if a command says you don't have permission to spend, you're not a member of that
account at all. If you can spend but can't add other people, you're a member but not
an authorizer.

## Move money between departments

Transfer funds from one government account to another:

```text
/government pay Treasury Roads 10000 Q3 budget
```

The last part is an optional note saved with the transfer. You must be a **member or
authorizer** of the account you're paying *from*.

## Pay a player or a business

Grants, contracts, and salaries go out with `payout`:

```text
/government payout Roads Steve 1500 paving contract
```

The recipient can be a player name or a business account. Again, you need to be a
member or authorizer of the `from` account.

## Manage who has access

If you're an **authorizer** on an account, you can add and remove people. Members can
view and spend; authorizers can also manage access (and must be a member first):

```text
/government account member add Treasury Steve
/government account member list Treasury
/government account auth add Treasury Steve
```

Swap `add` for `remove` to revoke access.

## Issue and manage fines

Fines are handled with `/fine`. Issuing a fine pays it **into a government account**,
which you name first:

```text
/fine issue Police Steve 500 griefing spawn
/fine list Steve
/fine info 42
/fine revoke 42
```

The amount is taken from the player's balance and paid into that account. You can only
**issue or revoke** fines for an account you have access to — a member or authorizer of
it (revoking refunds out of that same account). Viewing fines uses the fines view
permission.

## Where to go next

- Every government and fine command, with arguments: the
  **[Treasury reference](/docs/reference/treasury#government-accounts)**.
- How the different account types relate: **[Accounts & money](/docs/concepts/accounts-and-money)**.
