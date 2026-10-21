package io.github.afraalmaci.basketwise.optimization;

import io.github.afraalmaci.basketwise.optimization.PackSelector.Selection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Decides where to buy a shopping list: which chains, which packs, and whether a second or
 * third store is worth the trip.
 *
 * <p>Three layers:
 * <ol>
 *   <li><b>Packs.</b> For every chain and item, the cheapest packs that cover the needed
 *       amount ({@link PackSelector}, dynamic programming).</li>
 *   <li><b>Chains.</b> Every subset of chains with at most {@code maxStores} members. Within
 *       a subset each item goes to the chain that sells it cheapest. With 4 chains and up to
 *       3 stores that is 4 + 6 + 4 = 14 subsets, so the search is exhaustive and exact.</li>
 *   <li><b>Travel.</b> For each subset, the fastest round trip visiting one store of each
 *       chain, from a {@link TripEstimator}. The optimizer does not know about maps or
 *       routing APIs; it only asks "how long does a trip through these chains take?".</li>
 * </ol>
 *
 * <p>Each plan is scored with a {@link ScoringPolicy} (price + time × value of time). The
 * result holds the best plan for every number of stores, best overall first, so the UI can
 * show "1 store: €42,00 · 10 min" next to "2 stores: €39,60 · 19 min". Alternatives that
 * another plan beats on both price and time are left out.
 *
 * <p>A subset where some chain would sell nothing is skipped: visiting a store to buy
 * nothing is never useful, and the smaller subset without that chain is evaluated anyway.
 * This assumes that dropping a stop never makes a trip longer, which holds for real travel
 * times (triangle inequality).
 */
public final class BasketOptimizer {

    public static final int MAX_STORES = 3;

    /** 2^16 subsets at most; the demo has 4 chains. */
    static final int MAX_CHAINS = 16;

    private BasketOptimizer() {
    }

    /** @param neededAmount in the product's base unit (ml, g or pieces) */
    public record Item(long productId, long neededAmount) {
    }

    /**
     * A round trip through one store of each chain.
     *
     * @param stopIds store ids in visiting order; opaque to the optimizer, passed through
     */
    public record Trip(List<Long> stopIds, long durationSeconds, long distanceMeters) {

        public Trip {
            stopIds = List.copyOf(stopIds);
        }
    }

    /** Where travel times come from. Returns empty when the stores cannot be reached. */
    @FunctionalInterface
    public interface TripEstimator {
        Optional<Trip> fastestTrip(Set<String> chainCodes);
    }

    /** One item of the list, bought at one chain. */
    public record Purchase(long productId, long neededAmount, String chainCode, Selection selection) {
    }

    /**
     * @param chainCodes    chains visited, sorted by code
     * @param purchases     one per available item, in the order of the shopping list
     * @param timeCostCents travel time expressed in money
     * @param scoreCents    basket + time cost; lower is better
     */
    public record Plan(List<String> chainCodes, List<Purchase> purchases, long basketCents, Trip trip,
                       long timeCostCents, long scoreCents) {

        public Plan {
            chainCodes = List.copyOf(chainCodes);
            purchases = List.copyOf(purchases);
        }

        public int storeCount() {
            return chainCodes.size();
        }
    }

    /**
     * @param plans                 best plan per number of stores, best overall first, without
     *                              plans that another plan beats on both price and time
     * @param unavailableProductIds items no chain sells; the plans leave them out
     * @param combinationsChecked   chain subsets considered
     * @param cheapestPerChain      layer 1 on its own: per chain, per product it sells, the
     *                              cheapest packs for the needed amount. Lets the UI compare
     *                              chains item by item.
     */
    public record Result(List<Plan> plans, List<Long> unavailableProductIds, int combinationsChecked,
                         Map<String, Map<Long, Selection>> cheapestPerChain) {

        public Result {
            plans = List.copyOf(plans);
            unavailableProductIds = List.copyOf(unavailableProductIds);
            Map<String, Map<Long, Selection>> copy = new TreeMap<>();
            cheapestPerChain.forEach((chain, byProduct) -> copy.put(chain, Map.copyOf(byProduct)));
            cheapestPerChain = java.util.Collections.unmodifiableMap(copy);
        }

        public Optional<Plan> best() {
            return plans.stream().findFirst();
        }
    }

    public static Result optimize(List<Item> items, List<Offer> offers, int maxStores,
                                  ScoringPolicy scoring, TripEstimator trips) {
        validate(items, maxStores);

        // Layer 1: cheapest packs per (chain, item). TreeMap: chains in a stable order.
        Map<String, Map<Long, List<Pack>>> packsByChain = new TreeMap<>();
        for (Offer offer : offers) {
            packsByChain.computeIfAbsent(offer.chainCode(), code -> new TreeMap<>())
                    .computeIfAbsent(offer.productId(), id -> new ArrayList<>())
                    .add(offer.pack());
        }
        Map<String, Map<Long, Selection>> cheapest = new TreeMap<>();
        packsByChain.forEach((chain, packsByProduct) -> {
            for (Item item : items) {
                List<Pack> packs = packsByProduct.get(item.productId());
                if (packs != null) {
                    PackSelector.cheapest(item.neededAmount(), packs).ifPresent(selection ->
                            cheapest.computeIfAbsent(chain, code -> new TreeMap<>()).put(item.productId(), selection));
                }
            }
        });

        List<Item> available = new ArrayList<>();
        List<Long> unavailable = new ArrayList<>();
        for (Item item : items) {
            boolean sold = cheapest.values().stream().anyMatch(byItem -> byItem.containsKey(item.productId()));
            if (sold) {
                available.add(item);
            } else {
                unavailable.add(item.productId());
            }
        }
        if (available.isEmpty()) {
            return new Result(List.of(), unavailable, 0, cheapest);
        }

        List<String> chains = new ArrayList<>(cheapest.keySet());
        if (chains.size() > MAX_CHAINS) {
            throw new IllegalArgumentException("Too many chains: " + chains.size());
        }

        // Layers 2 and 3: every subset of chains, best plan per subset size.
        Comparator<Plan> bestFirst = comparator(scoring);
        Map<Integer, Plan> bestPerSize = new TreeMap<>();
        int checked = 0;
        for (int mask = 1; mask < (1 << chains.size()); mask++) {
            if (Integer.bitCount(mask) > maxStores) {
                continue;
            }
            checked++;
            Set<String> subset = new TreeSet<>();
            for (int i = 0; i < chains.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    subset.add(chains.get(i));
                }
            }
            plan(subset, available, cheapest, scoring, trips).ifPresent(plan ->
                    bestPerSize.merge(plan.storeCount(), plan,
                            (current, candidate) -> bestFirst.compare(candidate, current) < 0 ? candidate : current));
        }

        List<Plan> plans = new ArrayList<>(bestPerSize.values());
        plans.sort(bestFirst);
        plans = withoutDominated(plans);
        return new Result(plans, unavailable, checked, cheapest);
    }

    /**
     * Drops alternatives that another plan beats on both price and time (and is at least as
     * good on the other): nobody would pick them. The best plan always stays, whatever the
     * value of time. Example: a 3-store plan that costs more and takes longer than the
     * 2-store plan is the best 3-store plan, but not worth showing.
     */
    static List<Plan> withoutDominated(List<Plan> sorted) {
        List<Plan> kept = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Plan candidate = sorted.get(i);
            boolean dominated = false;
            for (Plan other : sorted) {
                if (i > 0 && other != candidate && dominates(other, candidate)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private static boolean dominates(Plan a, Plan b) {
        long aSeconds = a.trip().durationSeconds();
        long bSeconds = b.trip().durationSeconds();
        return a.basketCents() <= b.basketCents() && aSeconds <= bSeconds
                && (a.basketCents() < b.basketCents() || aSeconds < bSeconds);
    }

    /** Best plan using exactly these chains, or empty if they cannot cover the list or be reached. */
    private static Optional<Plan> plan(Set<String> subset, List<Item> items, Map<String, Map<Long, Selection>> cheapest,
                                      ScoringPolicy scoring, TripEstimator trips) {
        List<Purchase> purchases = new ArrayList<>(items.size());
        Set<String> used = new HashSet<>();
        long basket = 0;
        for (Item item : items) {
            Purchase best = null;
            for (String chain : subset) { // sorted, so ties go to the first chain by code
                Selection selection = cheapest.getOrDefault(chain, Map.of()).get(item.productId());
                if (selection != null && (best == null || cheaper(selection, best.selection()))) {
                    best = new Purchase(item.productId(), item.neededAmount(), chain, selection);
                }
            }
            if (best == null) {
                return Optional.empty(); // no chain in the subset sells this item
            }
            purchases.add(best);
            used.add(best.chainCode());
            basket += best.selection().costCents();
        }
        if (used.size() != subset.size()) {
            return Optional.empty(); // a store we would visit for nothing
        }

        Optional<Trip> trip = trips.fastestTrip(new LinkedHashSet<>(subset));
        if (trip.isEmpty()) {
            return Optional.empty();
        }
        long seconds = trip.get().durationSeconds();
        long timeCost = scoring.timeCostCents(seconds);
        return Optional.of(new Plan(List.copyOf(subset), purchases, basket, trip.get(), timeCost, basket + timeCost));
    }

    private static boolean cheaper(Selection candidate, Selection current) {
        if (candidate.costCents() != current.costCents()) {
            return candidate.costCents() < current.costCents();
        }
        return candidate.boughtAmount() < current.boughtAmount();
    }

    /** Lowest exact score first; ties prefer fewer stores, then less travel, then chain codes. */
    static Comparator<Plan> comparator(ScoringPolicy scoring) {
        return Comparator
                .comparingLong((Plan plan) -> scoring.exactScore(plan.basketCents(), plan.trip().durationSeconds()))
                .thenComparingInt(Plan::storeCount)
                .thenComparingLong(plan -> plan.trip().durationSeconds())
                .thenComparing(plan -> String.join(",", plan.chainCodes()));
    }

    private static void validate(List<Item> items, int maxStores) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("The shopping list is empty");
        }
        if (maxStores < 1 || maxStores > MAX_STORES) {
            throw new IllegalArgumentException("Max stores must be 1 to " + MAX_STORES + ", got " + maxStores);
        }
        Set<Long> seen = new HashSet<>();
        for (Item item : items) {
            if (!seen.add(item.productId())) {
                throw new IllegalArgumentException("Product " + item.productId() + " is on the list twice");
            }
            if (item.neededAmount() <= 0) {
                throw new IllegalArgumentException("Quantity must be positive for product " + item.productId());
            }
        }
    }
}
