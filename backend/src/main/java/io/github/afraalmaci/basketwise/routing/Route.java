package io.github.afraalmaci.basketwise.routing;

import java.util.List;

/**
 * A route through waypoints in the given order.
 *
 * @param geometry  the line to draw on the map
 * @param legs      one leg per consecutive pair of waypoints
 * @param estimated true when computed with the straight-line fallback
 */
public record Route(List<GeoPoint> geometry, List<Leg> legs, boolean estimated) {

    public Route {
        geometry = List.copyOf(geometry);
        legs = List.copyOf(legs);
    }

    public Leg total() {
        return legs.stream().reduce(new Leg(0, 0), Leg::plus);
    }
}
