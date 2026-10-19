package io.github.afraalmaci.basketwise.routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decorator that remembers recent answers from another {@link RoutingClient}.
 *
 * <p>The OpenRouteService free tier has a daily request quota, and the same questions repeat
 * a lot: toggling between bike and walk, re-selecting the same stores. Coordinates are
 * rounded to 4 decimals (about 10 m) for the cache key, so a pin dragged a few meters still
 * hits the cache.
 *
 * <p>Estimated results (the upstream fell back to straight lines because the API failed)
 * are not cached, so a short outage does not stick around.
 */
public class CachingRoutingClient implements RoutingClient {

    private static final double KEY_PRECISION = 10_000;

    private final RoutingClient delegate;
    private final Map<Key, TravelMatrix> matrices;
    private final Map<Key, Route> routes;

    public CachingRoutingClient(RoutingClient delegate, int maxEntries) {
        this.delegate = delegate;
        this.matrices = lru(maxEntries);
        this.routes = lru(maxEntries);
    }

    @Override
    public TravelMatrix matrix(List<GeoPoint> sources, List<GeoPoint> destinations, TravelMode mode) {
        List<GeoPoint> all = new ArrayList<>(sources);
        all.addAll(destinations);
        Key key = Key.of("matrix:" + sources.size(), mode, all);
        synchronized (matrices) {
            TravelMatrix cached = matrices.get(key);
            if (cached != null) {
                return cached;
            }
        }
        TravelMatrix result = delegate.matrix(sources, destinations, mode);
        if (!result.estimated()) {
            synchronized (matrices) {
                matrices.put(key, result);
            }
        }
        return result;
    }

    @Override
    public Route route(List<GeoPoint> waypoints, TravelMode mode) {
        Key key = Key.of("route", mode, waypoints);
        synchronized (routes) {
            Route cached = routes.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Route result = delegate.route(waypoints, mode);
        if (!result.estimated()) {
            synchronized (routes) {
                routes.put(key, result);
            }
        }
        return result;
    }

    @Override
    public String provider() {
        return delegate.provider();
    }

    /** A LinkedHashMap in access order that drops the least recently used entry when full. */
    private static <V> Map<Key, V> lru(int maxEntries) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, V> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /**
     * @param kind   what was asked ("route", or "matrix:N" where N is the number of sources,
     *               so the same points split differently are different questions)
     * @param coords rounded latitude/longitude pairs, flattened
     */
    record Key(String kind, TravelMode mode, List<Long> coords) {

        static Key of(String kind, TravelMode mode, List<GeoPoint> points) {
            List<Long> coords = new ArrayList<>(points.size() * 2);
            for (GeoPoint point : points) {
                coords.add(Math.round(point.latitude() * KEY_PRECISION));
                coords.add(Math.round(point.longitude() * KEY_PRECISION));
            }
            return new Key(kind, mode, List.copyOf(coords));
        }
    }
}
