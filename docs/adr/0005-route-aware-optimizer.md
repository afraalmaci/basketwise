# 5. Route-aware optimizer: one score for price and time

**Status:** Accepted
**Date:** 2026-10-20

## Context

A cheaper basket often means visiting more stores. Whether that is worth it depends on the
person: a student with a free afternoon and someone squeezing groceries between meetings
answer differently. Price is in euros and travel in minutes, so the app needs a rule to
compare them.

Shopping lists are also not "one of each": people need 2 L of milk while stores sell 1 L and
1.5 L packs, or 10 eggs while stores sell 6, 10 and 12.

## Decision

- **Needed amounts, not pack counts.** List items carry an amount in the product's base unit
  (ml, g, pieces). For every chain the cheapest set of packs covering it is found with
  dynamic programming (min-cost covering, a variant of unbounded knapsack). Buying more
  than needed is allowed when it is cheaper.
- **Exhaustive search over chains.** Every subset of chains with at most 3 members is tried
  (14 subsets for 4 chains). Within a subset each item goes to the chain that sells it
  cheapest. Subsets where a chain would sell nothing are skipped.
- **Real travel times from one matrix.** The origin plus the 3 nearest branches of each
  selling chain go into one travel-time matrix request. The fastest round trip for each
  subset comes from `TourPlanner` (M2), so trying 14 subsets costs one routing API call.
- **Score = basket price + travel time × value of time.** The user picks a priority instead
  of typing a number: Lowest price (EUR 3/h), Balanced (EUR 10/h, default), Least time
  (EUR 25/h). Plans are compared on the exact score (cents × 3600, no rounding).
- **Show alternatives.** The response holds the best plan for every number of stores, best
  first, so the UI can say what a second store would have saved and cost.
- **Engine stays plain Java.** `optimization` depends only on the JDK; it asks for travel
  times through a `TripEstimator` interface. An ArchUnit test enforces this in CI, together
  with "routing core has no Spring" and "no package cycles".

## Consequences

- Exact answers in milliseconds for the demo size. The search is exponential in the number
  of chains (2^n subsets), which is fine for a handful of chains but would need pruning or a
  heuristic for dozens.
- Skipping subsets with an idle chain assumes that dropping a stop never makes a trip
  longer (triangle inequality). Real road networks satisfy this closely enough.
- Raising the value of time never makes the recommended trip longer or the basket cheaper
  (tested). It does not always mean fewer stores: two nearby stores can beat one far away.
- The three fixed priorities are coarser than a slider, but easier to understand; the API
  accepts any value from 0 to 100 EUR/h, so a finer control can be added without backend
  changes.
