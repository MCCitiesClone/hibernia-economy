---
title: Webhooks
order: 6
description: The admin webhooks console — list, search, and manage every webhook on the server, and register one for any account.
---

# Webhooks (admin)

Players manage their own webhooks at [My data → Webhooks](/me/webhooks). The admin
console at **[Admin → Webhooks](/admin/webhooks)** is the fleet-wide view across **every**
account and firm.

For what webhooks are and how delivery, signing, and Discord embeds work, see the player
guide: [Webhooks](/docs/guides/webhooks).

## Listing & searching

The page lists every webhook subscription, newest first, with its owner, scope, status,
consecutive-failure count, and source. You can:

- **Search** by target URL, owner name, account or firm name, or any id (subscription /
  account / firm).
- **Filter** by:
  - **Scope** — account-scoped vs firm-scoped.
  - **Status** — *active*, *paused* (manually disabled), or *auto-disabled* (paused by the
    system after repeated delivery failures).
  - **Source** — *explorer* (created on the website) vs *API key* (created in-game via a
    REST API key).

## Creating a webhook for an account

Enter an **account id** and a public `https://` URL, then **Create**. The webhook is
account-scoped and attributed to that account's **owner**, so:

- It behaves exactly like one the owner created themselves, and
- the owner can also see and manage it under their own [My data → Webhooks](/me/webhooks).

The signing secret is shown **once** on creation — copy it if the consumer needs it.
A Discord webhook URL is auto-detected and delivered as a rich embed, same as anywhere
else.

> [!NOTE]
> The webhook is visible to the account's owner. There's no "covert" oversight webhook
> today — if you need one the owner can't see, raise it as a feature request.

## Per-row actions

- **Pause / Enable** — re-enabling clears the failure counter and the auto-disabled flag.
- **Delete** — removes the subscription and its delivery history; no further events are
  sent.

> [!CAUTION]
> Creating, pausing, and deleting webhooks here are privileged actions and are written to
> the [audit log](/docs/admin/roles). Prefer pointing a player at their own
> [My data → Webhooks](/me/webhooks) page when they can self-serve.
