---
title: Permissions & roles
order: 2
description: Who can do what — firm roles and their permissions, members vs. authorizers, and staff-only commands.
---

# Permissions and roles

A lot of commands check whether you're *allowed* before they do anything. If a command
seems to do nothing, the usual reason is that you don't have access yet. Here's how
access works.

## Firm roles

Inside a **[firm](/docs/guides/setting-up-a-business)**, every employee has one
**role**, and each role carries a set of **permissions**. There are four permissions:

| Permission | Lets the holder… |
|---|---|
| `ADMIN` | Manage the firm — roles, accounts, hiring and firing |
| `FINANCIAL` | Move the firm's money — deposit, withdraw, and pay |
| `CHESTSHOP` | Create and use ChestShops that belong to the firm |
| `DEFAULT` | Nothing special — just be a member |

Firms start with five roles: **Proprietor** (rank 1, `ADMIN`), **Co-Proprietor**
(2, `ADMIN`), **Manager** (3, `FINANCIAL`), **Supervisor** (4, `CHESTSHOP`), and
**Employee** (5, `DEFAULT`). New hires join as Employee. You can rename these, change
what they grant, or [build your own](/docs/guides/hiring-staff#build-your-own-roles).
The firm's **owner** (proprietor) can always do everything, regardless of roles.

Roles also have a **rank**, which is what `promote` and `demote` move people through —
the **lower** the rank number, the more senior (rank 1 is the top).

## Members vs. authorizers

Shared accounts — both **[business](/docs/concepts/accounts-and-money#business-accounts)**
and **[government](/docs/concepts/accounts-and-money#government-accounts)** — separate
two things:

- A **member** can *see* the account and its history.
- An **authorizer** can also *manage who has access*.

An authorizer is always a member too. Who can actually **spend** differs by account type:

- On **government** accounts, **members can spend** (and authorizers additionally manage
  who has access).
- On **business** accounts, spending is reserved for authorizers — a plain member can
  only view the books.

This split lets you give lots of people visibility while keeping spending with a trusted
few. For firm accounts, members and authorizers are kept in step with staff roles
automatically, and the firm owner can also adjust them by hand.

## Staff and operator commands

Some commands aren't for everyone:

- **Government** commands only work if you've been added to the relevant department
  account.
- **Operator** commands — like `/eco` (adjusting balances) and `/tax` — are for server
  admins and won't appear for normal players.

Throughout the reference, these are flagged with notes like "needs the ADMIN role" or
"operators only".

## Where to go next

- Put roles into practice: **[Hiring & managing staff](/docs/guides/hiring-staff)**.
- How accounts hold the money these permissions guard:
  **[Accounts & money](/docs/concepts/accounts-and-money)**.
