# 3. Hosted routing API for bike and walking routes

**Status:** Accepted
**Date:** 2026-10-14

## Context

The route-aware optimizer needs travel times between the user and stores, and between
stores. Public transport planning (e.g. 9292) is paid and unnecessary for a demo.

## Decision

- Use a hosted routing API (OpenRouteService) for bike and walking profiles, behind a
  `RoutingClient` interface. The API key lives in configuration, never in the repo.
- Map rendering uses Leaflet with OpenStreetMap tiles.

## Consequences

- No self-hosted routing server to operate.
- Free tier limits apply; travel time matrices should be cached per location and store set.
- A straight-line fallback implementation of `RoutingClient` keeps tests and local runs
  working without an API key.

## Amendment (2026-10-18)

Map tiles: CARTO basemaps (Voyager, and Dark Matter in dark mode) when `VITE_CARTO_API_KEY`
is set. CARTO has required a key for all tiles since August 2026; it is free for
non-commercial use. Without a key the app uses the standard OpenStreetMap tiles, darkened
with a CSS filter in dark mode, so the map works with zero configuration.
