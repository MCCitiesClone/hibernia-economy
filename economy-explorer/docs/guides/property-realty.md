---
title: Buying & renting property
order: 8
description: Buy a plot outright, rent one for a while, make an offer, or bid in an auction.
---

# Buying and renting property

Plots of land — homes, shops, farms — are bought and rented with the `/realty`
command. There are a few ways to get one: buy it outright, rent it for a set time, make
an offer to the owner, or win it at auction.

Most commands act on the plot you're **standing in**, so go to the property first. You
can also name a region explicitly where a command allows it.

## Look before you commit

```text
/realty info
```

Standing in a plot, this shows whether it's for sale or rent, the price, and who owns
it. Browse what's available with:

```text
/realty search
/realty list                # what you already own or rent
```

## Buy a plot

If a plot has a sale price, buy it outright:

```text
/realty buy
```

The price leaves your balance and the plot becomes yours.

## Rent a plot

If a plot is offered for rent, take it for its set period:

```text
/realty rent
```

Renting lasts a fixed time, then expires. Before it runs out you can extend it (up to
the limit the landlord allows):

```text
/realty extend
```

> [!WARNING]
> When a rental **expires**, you lose access to the plot and it returns to the
> landlord. Keep an eye on the clock and `/realty extend` in good time.

Done with a plot before the term is up? End the rental early and get a **prorated
refund** for the unused time:

```text
/realty unrent
```

## Make an offer

Not for sale at a fixed price? Propose your own to the owner:

```text
/realty offer send 50000
```

Then track and complete it:

```text
/realty offer outbox        # offers you've made
/realty offer pay 50000     # pay toward an accepted offer
/realty offer withdraw      # take an offer back
```

If you're the owner, incoming offers show up in your inbox:

```text
/realty offer inbox
/realty offer accept Steve
/realty offer reject Steve
```

## Bid at auction

Some plots are sold by auction. Check it, then bid:

```text
/realty auction info
/realty auction bid 12000
```

If you win, pay what you bid:

```text
/realty auction paybid 12000
```

> [!WARNING]
> Accepted offers and winning bids have a **payment deadline**. If you don't pay in full
> in time, the reservation is released and anything you'd paid so far is refunded — so
> finish paying promptly.

## Let an agent run sales for you

Selling a freehold and want help? Invite an **agent** — a player you authorise to run
auctions and handle offers on your behalf:

```text
/realty agent invite Steve
```

Steve accepts with `/realty agent invite accept`. Remove one with
`/realty agent remove Steve`. An agent only manages your auctions and offers — they
aren't added to the plot and can't build on it or change its settings.

## Property tax

If you **own** freehold plots, the government charges a **daily property tax** that
scales with how many plots you hold — the more you own, the steeper it gets. On the
current server config the tax stays at $0 for your first couple of plots, so small
holdings are effectively untaxed. That threshold isn't a fixed rule, though — it's a
configurable server setting, so the exact number of "free" plots can change over time.
The tax is deducted automatically each day and goes to the government, so factor it into
how much land you keep.

## Where to go next

- Every `/realty` command, including the admin and landlord options: the
  **[Realty reference](/docs/reference/realty)**.
