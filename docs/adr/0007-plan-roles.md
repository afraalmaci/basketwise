# 7. Plans are chosen by role, not by number of stores

**Status:** Accepted (replaces "best plan per number of stores" from ADRs 0005 and 0006)
**Date:** 2026-10-21

## Context

The plan cards showed the best plan for one, two and three stores. That hid useful
options: with milk and eggs on the list, one far-away chain was the cheapest way to shop,
but another one-store plan scored better at the default value of time, so the cheapest
option never appeared as a card. The number of stores is a means, not what people choose
by; they choose between "cheapest", "quickest" and "a sensible balance".

## Decision

- The optimizer keeps every candidate plan (at most 14 for 4 chains) and returns up to
  three, each with one or more roles:
  - **best**: lowest score (basket + travel time × value of time), always first;
  - **cheapest**: lowest basket, ties to the shorter trip;
  - **fastest**: shortest trip, ties to the cheaper basket.
- A plan that wins several roles is shown once, with all of them ("Best for you · Cheapest").
- The verdict sentence compares the recommendation with the cheapest and the fastest plan.
- The separate "drop plans beaten on both price and time" step is gone: the cheapest and the
  fastest plan cannot be beaten on both by construction.

## Consequences

- The cheapest way to buy the list is always visible, whatever the value of time.
- A plan that is neither the best, the cheapest nor the fastest is no longer shown, even if
  it is the best one for its number of stores.
- The API adds `roles` to each plan; clients that read `storeCount` keep working.
