package io.github.afraalmaci.basketwise.routing;

import java.util.List;

/**
 * Travel times and routes from some routing provider. Implementations: the straight-line
 * estimate (no API key needed), OpenRouteService, and a caching decorator.
 */
public interface RoutingClient {

    /** Legs from each source to each destination. */
    TravelMatrix matrix(List<GeoPoint> sources, List<GeoPoint> destinations, TravelMode mode);

    /** A route visiting the waypoints in the given order (at least two). */
    Route route(List<GeoPoint> waypoints, TravelMode mode);

    /** Short name shown in the UI, e.g. "openrouteservice" or "straight-line". */
    String provider();
}
