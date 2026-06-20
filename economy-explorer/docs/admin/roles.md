---
title: Managing Explorer access
order: 2
description: Grant or revoke admin and government roles for the Explorer.
---

# Managing Explorer access

Who can see what on the Explorer is controlled by **roles**. Roles are granted in-game
with `/treasuryapi ui user`.

## The roles

| Role | Grants |
|---|---|
| **player** | The default. Public pages, plus their own data once linked. |
| **government** | Adds the government views (department accounts and related data). |
| **admin** | Full access — audit log, API-key management, and these admin docs. |

A player with no explicit grant is a **player**. Roles are tied to the player, so they
apply wherever that player is linked.

## Granting and revoking

```text
/treasuryapi ui user add <role> <player>
/treasuryapi ui user remove <role> <player>
/treasuryapi ui user list <player>
```

| Argument | What it is |
|---|---|
| `role` | `admin` or `government` |
| `player` | The player to grant or revoke |

For example, make someone a government viewer, then check what they hold:

```text
/treasuryapi ui user add government Steve
/treasuryapi ui user list Steve
```

> [!NOTE]
> A role only takes effect for a player who has **linked** their account (so the
> Explorer can match the website login to the in-game player). Linking is covered in
> [Using the Explorer](/docs/guides/using-the-explorer).

> [!CAUTION]
> `admin` is full access, including the audit log and API-key controls. Grant it
> sparingly and review it with `/treasuryapi ui user list` when in doubt.
