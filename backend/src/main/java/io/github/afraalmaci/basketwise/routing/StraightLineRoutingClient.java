package io.github.afraalmaci.basketwise.routing;

import java.util.ArrayList;
import java.util.List;

/**
 * Estimates travel from straight-line distance: no network calls, no API key.
 *
 * <p>Streets are never straight, so the distance is multiplied by a detour factor. 1.3 is a
 * common rule of thumb for dense European cities. Used for tests, local development, and
 * as a fallback when OpenRouteService fails.
 */
public class StraightLineRoutingClient implements RoutingClient {

    static final double DETOUR_FACTOR = 1.3;

    @Override
    public TravelMatrix matrix(List<GeoPoint> sources, List<GeoPoint> destinations, TravelMode mode) {
        List<List<Leg>> rows = new ArrayList<>(sources.size());
        for (GeoPoint from : sources) {
            List<Leg> row = new ArrayList<>(destinations.size());
            for (GeoPoint to : destinations) {
                row.add(estimate(from, to, mode));
            }
            rows.add(row);
        }
        return new TravelMatrix(rows, true);
    }

    @Override
    public Route route(List<GeoPoint> waypoints, TravelMode mode) {
        requireTwoWaypoints(waypoints);
        List<Leg> legs = new ArrayList<>(waypoints.size() - 1);
        for (int i = 1; i < waypoints.size(); i++) {
            legs.add(estimate(waypoints.get(i - 1), waypoints.get(i), mode));
        }
        return new Route(waypoints, legs, true);
    }

    @Override
    public String provider() {
        return "straight-line";
    }

    static Leg estimate(GeoPoint from, GeoPoint to, TravelMode mode) {
        double meters = from.distanceMetersTo(to) * DETOUR_FACTOR;
        double seconds = meters / mode.fallbackSpeedMetersPerSecond();
        return new Leg(Math.round(seconds), Math.round(meters));
    }

    static void requireTwoWaypoints(List<GeoPoint> waypoints) {
        if (waypoints.size() < 2) {
            throw new IllegalArgumentException("A route needs at least two waypoints, got " + waypoints.size());
        }
    }
}
