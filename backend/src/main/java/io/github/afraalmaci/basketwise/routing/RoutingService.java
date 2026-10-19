package io.github.afraalmaci.basketwise.routing;

import io.github.afraalmaci.basketwise.routing.RoutingExceptions.OutsideServiceAreaException;
import io.github.afraalmaci.basketwise.routing.RoutingExceptions.UnknownStoreException;
import io.github.afraalmaci.basketwise.routing.TourPlanner.Tour;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Use cases of the routing module. Plain Java: stores come in through a {@link Supplier},
 * travel times through a {@link RoutingClient}, so it is tested without Spring or a database.
 */
public class RoutingService {

    private final RoutingClient routingClient;
    private final ServiceArea serviceArea;
    private final Supplier<List<StoreLocation>> stores;

    public RoutingService(RoutingClient routingClient, ServiceArea serviceArea,
                          Supplier<List<StoreLocation>> stores) {
        this.routingClient = routingClient;
        this.serviceArea = serviceArea;
        this.stores = stores;
    }

    public ServiceArea serviceArea() {
        return serviceArea;
    }

    public String provider() {
        return routingClient.provider();
    }

    /** One entry per chain: its store with the shortest travel time from the origin. */
    public record NearestStore(StoreLocation store, Leg leg) {
    }

    public record NearestStores(List<NearestStore> stores, boolean estimated) {
    }

    /** Stops in visiting order, and the route origin → stops → origin. */
    public record Trip(List<StoreLocation> stops, Route route, boolean estimated) {
    }

    /** Trip options, fastest first. */
    public record TripPlan(List<Trip> options, boolean estimated) {

        public Trip fastest() {
            return options.get(0);
        }
    }

    /** Fastest trip plus at most two alternatives, like a navigation app. */
    static final int MAX_OPTIONS = 3;

    /** Branches considered per chosen store: the chosen one and its two nearest siblings. */
    static final int BRANCHES_PER_STOP = 3;

    /** Alternatives more than 50% slower than the fastest trip are not worth showing. */
    static final double MAX_SLOWDOWN = 1.5;

    /**
     * The closest store of each chain by travel time (not by straight-line distance: a store
     * across a canal can be close on the map and far by bike). Sorted fastest first.
     */
    public NearestStores nearestPerChain(GeoPoint origin, TravelMode mode) {
        requireInServiceArea(origin);
        List<StoreLocation> all = stores.get();
        TravelMatrix matrix = routingClient.matrix(
                List.of(origin), all.stream().map(StoreLocation::location).toList(), mode);

        Comparator<NearestStore> fastest = Comparator
                .comparingLong((NearestStore n) -> n.leg().durationSeconds())
                .thenComparingLong(n -> n.leg().distanceMeters())
                .thenComparingLong(n -> n.store().id());

        Map<String, NearestStore> bestPerChain = new LinkedHashMap<>();
        for (int i = 0; i < all.size(); i++) {
            Leg leg = matrix.leg(0, i);
            if (leg == null) {
                continue; // unreachable from here, e.g. across water
            }
            NearestStore candidate = new NearestStore(all.get(i), leg);
            bestPerChain.merge(candidate.store().chainCode(), candidate,
                    (current, next) -> fastest.compare(next, current) < 0 ? next : current);
        }

        List<NearestStore> sorted = bestPerChain.values().stream().sorted(fastest).toList();
        return new NearestStores(sorted, matrix.estimated());
    }

    /**
     * Plans round trips from the origin through the chosen stores: the fastest one first,
     * followed by up to two alternatives.
     *
     * <p>Alternatives mix two kinds of change: a different visiting order, and a different
     * branch of the same chain (for every chosen store, it and the two sibling branches nearest
     * to the origin are considered). So the fastest trip may swap a chosen store for
     * a sibling branch when that saves time overall.
     */
    public TripPlan planTrip(GeoPoint origin, List<Long> storeIds, TravelMode mode) {
        requireInServiceArea(origin);
        if (storeIds.isEmpty() || storeIds.size() > TourPlanner.MAX_STOPS) {
            throw new IllegalArgumentException("Choose 1 to " + TourPlanner.MAX_STOPS + " stores");
        }
        if (new HashSet<>(storeIds).size() != storeIds.size()) {
            throw new IllegalArgumentException("Each store can be chosen only once");
        }

        List<StoreLocation> all = stores.get();
        Map<Long, StoreLocation> byId = all.stream()
                .collect(Collectors.toMap(StoreLocation::id, Function.identity()));
        List<StoreLocation> chosen = new ArrayList<>();
        for (long id : storeIds) {
            StoreLocation store = byId.get(id);
            if (store == null) {
                throw new UnknownStoreException(id);
            }
            chosen.add(store);
        }

        // Matrix points: index 0 is the origin, then every candidate store once.
        List<GeoPoint> points = new ArrayList<>();
        List<StoreLocation> storeAt = new ArrayList<>();
        points.add(origin);
        storeAt.add(null);
        Map<Long, Integer> indexOf = new LinkedHashMap<>();
        List<List<Integer>> groups = new ArrayList<>();
        for (StoreLocation store : chosen) {
            List<Integer> group = new ArrayList<>();
            for (StoreLocation candidate : branchCandidates(store, all, origin)) {
                Integer index = indexOf.get(candidate.id());
                if (index == null) {
                    index = points.size();
                    indexOf.put(candidate.id(), index);
                    points.add(candidate.location());
                    storeAt.add(candidate);
                }
                group.add(index);
            }
            groups.add(group);
        }

        TravelMatrix matrix = routingClient.matrix(points, points, mode);
        List<Tour> tours = TourPlanner.ranked(matrix, groups, MAX_OPTIONS, MAX_SLOWDOWN);

        List<Trip> options = new ArrayList<>();
        for (Tour tour : tours) {
            List<StoreLocation> ordered = tour.stops().stream().map(storeAt::get).toList();
            List<GeoPoint> waypoints = new ArrayList<>();
            waypoints.add(origin);
            ordered.forEach(store -> waypoints.add(store.location()));
            waypoints.add(origin);
            Route route = routingClient.route(waypoints, mode);
            options.add(new Trip(ordered, route, matrix.estimated() || route.estimated()));
        }
        boolean estimated = options.stream().anyMatch(Trip::estimated);
        return new TripPlan(List.copyOf(options), estimated);
    }

    /**
     * The chosen store plus the other branches of its chain closest to the origin (straight
     * line, which is good enough to pick candidates; real travel times decide afterwards).
     */
    static List<StoreLocation> branchCandidates(StoreLocation chosen, List<StoreLocation> all, GeoPoint origin) {
        List<StoreLocation> result = new ArrayList<>();
        result.add(chosen);
        all.stream()
                .filter(store -> store.chainCode().equals(chosen.chainCode()) && store.id() != chosen.id())
                .sorted(Comparator
                        .comparingDouble((StoreLocation store) -> origin.distanceMetersTo(store.location()))
                        .thenComparingLong(StoreLocation::id))
                .limit(BRANCHES_PER_STOP - 1)
                .forEach(result::add);
        return result;
    }

    private void requireInServiceArea(GeoPoint point) {
        if (!serviceArea.contains(point)) {
            throw new OutsideServiceAreaException(point, serviceArea);
        }
    }
}
