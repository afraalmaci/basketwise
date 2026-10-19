package io.github.afraalmaci.basketwise.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CachingRoutingClientTest {

    private final GeoPoint home = new GeoPoint(52.0809, 4.3245);
    private final GeoPoint store = new GeoPoint(52.0786, 4.3104);

    @Test
    void secondIdenticalRequestIsServedFromCache() {
        CountingClient upstream = new CountingClient(false);
        CachingRoutingClient cache = new CachingRoutingClient(upstream, 10);

        TravelMatrix first = cache.matrix(List.of(home), List.of(store), TravelMode.BIKE);
        TravelMatrix second = cache.matrix(List.of(home), List.of(store), TravelMode.BIKE);

        assertThat(second).isSameAs(first);
        assertThat(upstream.calls).isEqualTo(1);
    }

    @Test
    void pointsAFewMetersApartShareACacheEntry() {
        CountingClient upstream = new CountingClient(false);
        CachingRoutingClient cache = new CachingRoutingClient(upstream, 10);
        GeoPoint draggedSlightly = new GeoPoint(52.08091, 4.32451); // about 1 m away

        cache.route(List.of(home, store, home), TravelMode.WALK);
        cache.route(List.of(draggedSlightly, store, draggedSlightly), TravelMode.WALK);

        assertThat(upstream.calls).isEqualTo(1);
    }

    @Test
    void travelModeIsPartOfTheKey() {
        CountingClient upstream = new CountingClient(false);
        CachingRoutingClient cache = new CachingRoutingClient(upstream, 10);

        cache.matrix(List.of(home), List.of(store), TravelMode.BIKE);
        cache.matrix(List.of(home), List.of(store), TravelMode.WALK);

        assertThat(upstream.calls).isEqualTo(2);
    }

    @Test
    void sameCoordinatesSplitDifferentlyAreDifferentQuestions() {
        CountingClient upstream = new CountingClient(false);
        CachingRoutingClient cache = new CachingRoutingClient(upstream, 10);

        cache.matrix(List.of(home), List.of(store, home), TravelMode.BIKE);
        cache.matrix(List.of(home, store), List.of(home), TravelMode.BIKE);

        assertThat(upstream.calls).isEqualTo(2);
    }

    @Test
    void estimatedResultsAreNotCached() {
        CountingClient upstream = new CountingClient(true);
        CachingRoutingClient cache = new CachingRoutingClient(upstream, 10);

        cache.matrix(List.of(home), List.of(store), TravelMode.BIKE);
        cache.matrix(List.of(home), List.of(store), TravelMode.BIKE);

        assertThat(upstream.calls).isEqualTo(2);
    }

    @Test
    void evictsLeastRecentlyUsedEntryWhenFull() {
        CountingClient upstream = new CountingClient(false);
        CachingRoutingClient cache = new CachingRoutingClient(upstream, 2);
        GeoPoint other = new GeoPoint(52.0561, 4.2972);
        GeoPoint third = new GeoPoint(52.1046, 4.2768);

        cache.route(List.of(home, store), TravelMode.BIKE);   // A
        cache.route(List.of(home, other), TravelMode.BIKE);   // B
        cache.route(List.of(home, store), TravelMode.BIKE);   // A again: now most recent
        cache.route(List.of(home, third), TravelMode.BIKE);   // C evicts B
        cache.route(List.of(home, store), TravelMode.BIKE);   // A still cached
        cache.route(List.of(home, other), TravelMode.BIKE);   // B must be fetched again

        assertThat(upstream.calls).isEqualTo(4);
    }

    /** Wraps the straight-line client, counts calls, and can pretend to be a real provider. */
    private static final class CountingClient implements RoutingClient {

        private final boolean estimated;
        private final StraightLineRoutingClient straightLine = new StraightLineRoutingClient();
        int calls;

        CountingClient(boolean estimated) {
            this.estimated = estimated;
        }

        @Override
        public TravelMatrix matrix(List<GeoPoint> sources, List<GeoPoint> destinations, TravelMode mode) {
            calls++;
            return new TravelMatrix(straightLine.matrix(sources, destinations, mode).legs(), estimated);
        }

        @Override
        public Route route(List<GeoPoint> waypoints, TravelMode mode) {
            calls++;
            Route route = straightLine.route(waypoints, mode);
            return new Route(route.geometry(), route.legs(), estimated);
        }

        @Override
        public String provider() {
            return "counting";
        }
    }
}
