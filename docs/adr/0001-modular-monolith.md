# 1. Modular monolith with an isolated optimization core

**Status:** Accepted
**Date:** 2026-10-14

## Context

BasketWise has a few genuinely complex parts (basket optimization, routing, unit
normalization) and a thin CRUD layer around them. It is built by one developer with
roughly 15 hours per week.

## Decision

- One Spring Boot application, split into packages by domain (`catalog`, `pricing`,
  `optimization`, `routing`, `system`) rather than by technical layer.
- The optimization engine is plain Java with no Spring or database dependencies. It
  receives prices and travel costs as input and returns a plan.

## Consequences

- One deployable, one database, simple local setup.
- The optimizer can be unit and property tested in milliseconds, without a database.
- If a module ever needs to scale separately, package boundaries make extraction possible.
