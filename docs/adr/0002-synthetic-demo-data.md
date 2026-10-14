# 2. Synthetic demo data only

**Status:** Accepted
**Date:** 2026-10-14

## Context

Publicly redistributing real supermarket prices carries legal risk (EU database rights).
The project is a personal portfolio piece but should still be deployed with a live demo.

## Decision

The application runs on a generated dataset: fictional store chains with branches around
Den Haag, realistic products, and realistic price ranges. The UI labels it as demo data.

Data access goes through a `SourceAdapter` interface, so a real data source could be
added later without touching the optimizer or the API.

## Consequences

- Safe to deploy publicly.
- Because the dataset is generated, the ground truth is known, which makes it easy to
  write precise tests (e.g. "the optimal basket costs exactly EUR 23.40").
- Price history analysis is not meaningful on synthetic data and is out of MVP scope.
