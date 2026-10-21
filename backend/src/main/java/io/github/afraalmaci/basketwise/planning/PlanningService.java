package io.github.afraalmaci.basketwise.planning;

import io.github.afraalmaci.basketwise.catalog.CatalogDtos.OfferDto;
import io.github.afraalmaci.basketwise.catalog.CatalogDtos.ProductDto;
import io.github.afraalmaci.basketwise.optimization.BasketOptimizer;
import io.github.afraalmaci.basketwise.optimization.BasketOptimizer.Item;
import io.github.afraalmaci.basketwise.optimization.BasketOptimizer.Plan;
import io.github.afraalmaci.basketwise.optimization.BasketOptimizer.Purchase;
import io.github.afraalmaci.basketwise.optimization.BasketOptimizer.Result;
import io.github.afraalmaci.basketwise.optimization.BasketOptimizer.Trip;
import io.github.afraalmaci.basketwise.optimization.Offer;
import io.github.afraalmaci.basketwise.optimization.Pack;
import io.github.afraalmaci.basketwise.optimization.PackSelector.Selection;
import io.github.afraalmaci.basketwise.optimization.ScoringPolicy;
import io.github.afraalmaci.basketwise.routing.GeoPoint;
import io.github.afraalmaci.basketwise.routing.Leg;
import io.github.afraalmaci.basketwise.routing.Route;
import io.github.afraalmaci.basketwise.routing.RoutingClient;
import io.github.afraalmaci.basketwise.routing.RoutingExceptions.OutsideServiceAreaException;
import io.github.afraalmaci.basketwise.routing.ServiceArea;
import io.github.afraalmaci.basketwise.routing.StoreLocation;
import io.github.afraalmaci.basketwise.routing.TourPlanner;
import io.github.afraalmaci.basketwise.routing.TourPlanner.Tour;
import io.github.afraalmaci.basketwise.routing.TravelMatrix;
import io.github.afraalmaci.basketwise.routing.TravelMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The route-aware optimizer as a use case: connects the catalog (what things cost), routing
 * (how long trips take) and the optimization engine (what to buy where).
 *
 * <p>Plain Java, like {@code RoutingService}: products and stores come in through functions,
 * so it is tested without Spring or a database.
 *
 * <p>Travel time costs one matrix request per optimization, however many chain combinations
 * the optimizer tries: the origin plus the {@value #BRANCHES_PER_CHAIN} nearest branches of
 * every chain (13 points for 4 chains). Each combination is
 * then answered from that matrix by {@link TourPlanner}. Only the plans shown to the user get
 * a full route with geometry.
 */
public class PlanningService {

    public static final int MAX_ITEMS = 30;

    /** Branches considered per chain: real travel times decide which one a trip uses. */
    static final int BRANCHES_PER_CHAIN = 3;

    /** Upper limits per unit, to keep requests sane: 25 L, 25 kg, 100 pieces. */
    static final long MAX_MILLILITERS_OR_GRAMS = 25_000;
    static final long MAX_PIECES = 100;

    private final RoutingClient routingClient;
    private final ServiceArea serviceArea;
    private final Supplier<List<StoreLocation>> stores;
    private final Function<Collection<Long>, List<ProductDto>> products;

    public PlanningService(RoutingClient routingClient, ServiceArea serviceArea, Supplier<List<StoreLocation>> stores,
                           Function<Collection<Long>, List<ProductDto>> products) {
        this.routingClient = routingClient;
        this.serviceArea = serviceArea;
        this.stores = stores;
        this.products = products;
    }

    /** @param quantity how much is needed, in the product's base unit (ml, g or pieces) */
    public record ListItem(long productId, long quantity) {
    }

    public record Request(GeoPoint origin, TravelMode mode, List<ListItem> items, int maxStores, ScoringPolicy scoring) {
    }

    /** One store of a plan, in visiting order, and what to buy there. */
    public record Stop(StoreLocation store, List<Purchase> purchases, long subtotalCents) {
    }

    /** An optimizer plan made concrete: stores in order, and the route to draw. */
    public record RoutedPlan(Plan plan, List<Stop> stops, Route route, boolean estimated) {
    }

    /**
     * One chain's column in the comparison.
     *
     * @param nearestStore   its branch with the shortest travel time from the origin, or null
     *                       when no travel times were needed (nothing on the list is sold)
     * @param nearestSeconds one-way travel time to that branch, or null like nearestStore
     * @param totalCents     the whole list bought at this chain, for the items it sells
     * @param itemsCovered   how many items of the list it sells
     */
    public record ChainSummary(String chainCode, StoreLocation nearestStore, Long nearestSeconds,
                               long totalCents, int itemsCovered) {
    }

    /**
     * Every item priced at every chain: the optimizer's first layer, made visible.
     *
     * @param chains   one per chain that has stores, by chain code
     * @param cheapest per chain, per product it sells: the cheapest packs for the needed amount
     */
    public record Comparison(List<ChainSummary> chains, Map<String, Map<Long, Selection>> cheapest) {
    }

    /**
     * @param plans        best plan per number of stores, best first
     * @param products     every product on the list, by id, with its offers
     * @param unavailable  products on the list that no chain sells
     */
    public record Outcome(List<RoutedPlan> plans, Map<Long, ProductDto> products, List<ProductDto> unavailable,
                          int combinationsChecked, boolean estimated, Comparison comparison) {
    }

    public Outcome plan(Request request) {
        if (!serviceArea.contains(request.origin())) {
            throw new OutsideServiceAreaException(request.origin(), serviceArea);
        }
        Map<Long, ProductDto> byId = loadProducts(request.items());

        List<Item> items = request.items().stream().map(item -> new Item(item.productId(), item.quantity())).toList();
        List<Offer> offers = new ArrayList<>();
        Set<String> sellingChains = new HashSet<>();
        for (ProductDto product : byId.values()) {
            for (OfferDto offer : product.offers()) {
                offers.add(new Offer(offer.chainCode(), product.id(),
                        new Pack(offer.quantityAmount(), offer.priceCents())));
                sellingChains.add(offer.chainCode());
            }
        }

        // One matrix: index 0 is the origin, then the candidate branches of every chain. Chains
        // that sell nothing on the list are included too, so the comparison can say how far
        // away they are; with 4 chains that is at most 13 points.
        List<StoreLocation> all = stores.get();
        Set<String> allChains = new TreeSet<>();
        all.forEach(store -> allChains.add(store.chainCode()));
        List<GeoPoint> points = new ArrayList<>();
        List<StoreLocation> storeAt = new ArrayList<>();
        points.add(request.origin());
        storeAt.add(null);
        Map<String, List<Integer>> candidates = new TreeMap<>();
        for (String chain : allChains) {
            for (StoreLocation store : nearestBranches(chain, all, request.origin())) {
                candidates.computeIfAbsent(chain, code -> new ArrayList<>()).add(points.size());
                points.add(store.location());
                storeAt.add(store);
            }
        }
        // Nothing on the list is sold anywhere: no travel to ask about.
        TravelMatrix matrix = sellingChains.isEmpty() ? null : routingClient.matrix(points, points, request.mode());

        Result result = BasketOptimizer.optimize(items, offers, request.maxStores(), request.scoring(),
                chains -> fastestTrip(matrix, candidates, storeAt, chains));

        List<RoutedPlan> plans = new ArrayList<>();
        for (Plan plan : result.plans()) {
            plans.add(route(plan, request, storeAt));
        }
        List<ProductDto> unavailable = result.unavailableProductIds().stream().map(byId::get).toList();
        boolean estimated = (matrix != null && matrix.estimated()) || plans.stream().anyMatch(RoutedPlan::estimated);
        Comparison comparison = compare(allChains, candidates, storeAt, matrix, result.cheapestPerChain());
        return new Outcome(plans, byId, unavailable, result.combinationsChecked(), estimated, comparison);
    }

    private static Comparison compare(Set<String> chains, Map<String, List<Integer>> candidates,
                                      List<StoreLocation> storeAt, TravelMatrix matrix,
                                      Map<String, Map<Long, Selection>> cheapest) {
        List<ChainSummary> summaries = new ArrayList<>();
        for (String chain : chains) {
            StoreLocation nearest = null;
            Long nearestSeconds = null;
            if (matrix != null) {
                for (int index : candidates.getOrDefault(chain, List.of())) {
                    Leg leg = matrix.leg(0, index);
                    if (leg != null && (nearestSeconds == null || leg.durationSeconds() < nearestSeconds)) {
                        nearest = storeAt.get(index);
                        nearestSeconds = leg.durationSeconds();
                    }
                }
            }
            Map<Long, Selection> sold = cheapest.getOrDefault(chain, Map.of());
            long total = sold.values().stream().mapToLong(Selection::costCents).sum();
            summaries.add(new ChainSummary(chain, nearest, nearestSeconds, total, sold.size()));
        }
        return new Comparison(summaries, cheapest);
    }

    /** The fastest loop through one branch of each chain, answered from the precomputed matrix. */
    private static Optional<Trip> fastestTrip(TravelMatrix matrix, Map<String, List<Integer>> candidates,
                                              List<StoreLocation> storeAt, Set<String> chains) {
        List<List<Integer>> groups = new ArrayList<>();
        for (String chain : chains) {
            List<Integer> group = candidates.get(chain);
            if (group == null) {
                return Optional.empty(); // the chain has no stores
            }
            groups.add(group);
        }
        try {
            Tour tour = TourPlanner.ranked(matrix, groups, 1, Double.POSITIVE_INFINITY).get(0);
            List<Long> stopIds = tour.stops().stream().map(index -> storeAt.get(index).id()).toList();
            return Optional.of(new Trip(stopIds, tour.total().durationSeconds(), tour.total().distanceMeters()));
        } catch (IllegalArgumentException unreachable) {
            return Optional.empty();
        }
    }

    private RoutedPlan route(Plan plan, Request request, List<StoreLocation> storeAt) {
        Map<Long, StoreLocation> storeById = new LinkedHashMap<>();
        for (StoreLocation store : storeAt) {
            if (store != null) {
                storeById.put(store.id(), store);
            }
        }

        List<Stop> stops = new ArrayList<>();
        List<GeoPoint> waypoints = new ArrayList<>();
        waypoints.add(request.origin());
        for (long storeId : plan.trip().stopIds()) {
            StoreLocation store = storeById.get(storeId);
            List<Purchase> purchases = plan.purchases().stream()
                    .filter(purchase -> purchase.chainCode().equals(store.chainCode()))
                    .toList();
            long subtotal = purchases.stream().mapToLong(purchase -> purchase.selection().costCents()).sum();
            stops.add(new Stop(store, purchases, subtotal));
            waypoints.add(store.location());
        }
        waypoints.add(request.origin());

        Route route = routingClient.route(waypoints, request.mode());
        return new RoutedPlan(plan, stops, route, route.estimated());
    }

    private Map<Long, ProductDto> loadProducts(List<ListItem> items) {
        if (items.isEmpty() || items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("The list must have 1 to " + MAX_ITEMS + " items");
        }
        List<Long> ids = items.stream().map(ListItem::productId).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("Each product can be on the list only once");
        }

        Map<Long, ProductDto> byId = new LinkedHashMap<>();
        products.apply(ids).forEach(product -> byId.put(product.id(), product));
        for (ListItem item : items) {
            ProductDto product = byId.get(item.productId());
            if (product == null) {
                throw new UnknownProductException(item.productId());
            }
            long max = "pc".equals(product.unit()) ? MAX_PIECES : MAX_MILLILITERS_OR_GRAMS;
            if (item.quantity() <= 0 || item.quantity() > max) {
                throw new IllegalArgumentException("Quantity of " + product.name() + " must be 1 to " + max
                        + " " + product.unit() + ", got " + item.quantity());
            }
        }
        return byId;
    }

    /** The branches of a chain closest to the origin in a straight line; travel times pick among them. */
    static List<StoreLocation> nearestBranches(String chainCode, List<StoreLocation> all, GeoPoint origin) {
        return all.stream()
                .filter(store -> store.chainCode().equals(chainCode))
                .sorted(Comparator
                        .comparingDouble((StoreLocation store) -> origin.distanceMetersTo(store.location()))
                        .thenComparingLong(StoreLocation::id))
                .limit(BRANCHES_PER_CHAIN)
                .toList();
    }

    public static class UnknownProductException extends RuntimeException {
        public UnknownProductException(long productId) {
            super("Unknown product id " + productId);
        }
    }
}
