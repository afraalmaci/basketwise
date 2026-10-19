# 4. Browser geolocation with a demo service area

**Status:** Accepted
**Date:** 2026-10-18

## Context

Routes start from the user's location. Asking people to click their home on a map is
friction, so the app asks the browser for its location instead. But the demo data only
covers Den Haag: a visitor in Amsterdam (for example someone opening the live demo link)
would get routes to stores 50 km away, and long requests can fail or waste the routing quota.

## Decision

- On load, the frontend asks for the location once with the Geolocation API
  (`enableHighAccuracy: false`; city-block precision is enough).
- The origin is chosen in this order: a pin the user dragged, the device location if it is
  inside the service area, otherwise the area's default location (Den Haag Centraal) with a
  short notice explaining why.
- The pin is always draggable, and a dragged pin is clamped to the service area.
- The service area (bounding box and default location) lives in backend configuration and
  is exposed through `GET /api/routing/config`. The backend also rejects locations outside
  it (400), so the browser check is a convenience, not the only guard.
- The location is not stored or logged. It is sent only as part of a routing request, and
  from there to the routing provider.

## Consequences

- Works without any user action when permission is granted, and still works when it is
  denied, unavailable, or the prompt is ignored.
- Geolocation requires a secure context (HTTPS or localhost); deploy platforms provide HTTPS.
- Desktop browsers often locate by Wi-Fi or IP and can be off by hundreds of meters. The
  draggable pin covers that.
- Changing the demo city means changing one configuration block and the demo stores.
