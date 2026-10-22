# 6. List-first layout

**Status:** Accepted
**Date:** 2026-10-21

## Context

The interface grew out of M2 (routing): a full-screen map with a floating panel, like a
navigation app. After M3 the app answers a different question: not "how do I get to these
stores?" but "where should I buy this list?". In the map-first layout the list sat under
travel settings in a long scrolling panel, and the optimizer's most interesting work, pricing
every item at every chain, was not visible at all.

## Decision

- Desktop first, two columns: the shopping list on the left (always visible), the answer on
  the right. Mobile gets its own layout later.
- The settings every plan depends on live in a top bar: start point (a chip that opens a
  small map with the draggable pin), travel mode, and priority (a chip that opens the three
  options with what each means in euros).
- The answer has two tabs:
  - **Compare:** items as rows, chains as columns, each cell the cheapest packs for the
    needed amount, the lowest price per row highlighted, chain totals and "nearest N min"
    per chain. The best plan is summarized above it.
  - **Plan:** the plans as cards, then the selected one as a route with what to buy at each
    stop (with checkboxes for use in the store) next to the map.
- `POST /api/optimize` returns the comparison with the plans: the optimizer already prices
  every item at every chain, so this is one response, not a second endpoint.
- Plans that another plan beats on both price and time are no longer returned. (Superseded
  by ADR 0007: plans are now chosen by role.)
- The "nearest store per chain" list is gone; the comparison's column headers carry that
  information.
- The look from the UI refresh carries over unchanged: Delft-blue ink on paper, the route
  and the chosen plan marked in fietspad red, shelf-tag prices (`Price`) wherever an amount
  is read at a glance (table cells, totals, plan cards, stop subtotals), desaturated tiles.

## Consequences

- The map is no longer the first thing a visitor sees; it appears where it answers a
  question (where am I starting, which route does this plan take).
- The comparison table grows with the list. Up to the 30-item limit it stays readable on a
  desktop; on a phone it will need a different presentation (per item cards or per chain
  totals), which is part of the mobile milestone.
- `GET /api/routing/nearest` and `POST /api/routing/trip` are no longer used by the frontend.
  They stay in the API for now.
