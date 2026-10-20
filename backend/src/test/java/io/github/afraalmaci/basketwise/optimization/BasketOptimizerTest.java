package io.github.afraalmaci.basketwise.optimization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.afraalmaci.basketwise.optimization.BasketOptimizer.Item;
import io.github.afraalmaci.basketwise.optimization.BasketOptimizer.Plan;
import io.github.afraalmaci.basketwise.optimization.BasketOptimizer.Purchase;
import io.github.afraalmaci.basketwise.optimization.BasketOptimizer.Result;
import io.github.afraalmaci.basketwise.optimization.BasketOptimizer.Trip;
import io.github.afraalmaci.basketwise.optimization.BasketOptimizer.TripEstimator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class BasketOptimizerTest {

    private static final long MILK = 1;
    private static final long COLA = 2;
    private static final long SAFFRON = 3;

    /**
     * NEAR is close and sells everything, but is expensive. CHEAP sells milk for less, but no
     * cola, and adds 9 minutes to the trip.
     *
     * <pre>
     *            basket   travel
     * NEAR only  5.00     10 min
     * CHEAP+NEAR 4.00     19 min   (milk at CHEAP, cola at NEAR)
     * </pre>
     *
     * Saving 1.00 for 9 minutes is worth it below 6.67 EUR/h.
     */
    private static final List<Offer> OFFERS = List.of(
            new Offer("NEAR", MILK, new Pack(1000, 150)),
            new Offer("NEAR", COLA, new Pack(1500, 200)),
            new Offer("CHEAP", MILK, new Pack(1000, 100)));

    private static final TripEstimator TRIPS = chains -> Optional.of(switch (String.join(",", new TreeSet<>(chains))) {
        case "NEAR" -> new Trip(List.of(10L), 600, 2_000);
        case "CHEAP" -> new Trip(List.of(20L), 900, 3_000);
        case "CHEAP,NEAR" -> new Trip(List.of(10L, 20L), 1_140, 4_500);
        default -> throw new AssertionError("Unexpected subset " + chains);
    });

    private static final List<Item> LIST = List.of(new Item(MILK, 2000), new Item(COLA, 1500));

    @Test
    void cheapTimeMakesTheSecondStoreWorthIt() {
        Result result = BasketOptimizer.optimize(LIST, OFFERS, 3, ScoringPolicy.eurosPerHour(3), TRIPS);

        Plan best = result.best().orElseThrow();
        assertThat(best.chainCodes()).containsExactly("CHEAP", "NEAR");
        assertThat(best.basketCents()).isEqualTo(400);
        assertThat(best.trip().durationSeconds()).isEqualTo(1_140);
        assertThat(best.timeCostCents()).isEqualTo(95); // 19 min at 3 EUR/h
        assertThat(best.scoreCents()).isEqualTo(495);
        assertThat(chainOf(best, MILK)).isEqualTo("CHEAP");
        assertThat(chainOf(best, COLA)).isEqualTo("NEAR");
    }

    @Test
    void expensiveTimeKeepsItToOneStore() {
        Result result = BasketOptimizer.optimize(LIST, OFFERS, 3, ScoringPolicy.eurosPerHour(10), TRIPS);

        assertThat(result.plans()).hasSize(2);
        Plan best = result.plans().get(0);
        Plan alternative = result.plans().get(1);
        assertThat(best.chainCodes()).containsExactly("NEAR");
        assertThat(best.basketCents()).isEqualTo(500);
        assertThat(best.scoreCents()).isEqualTo(667); // 5.00 + 10 min at 10 EUR/h
        // The two-store plan is still returned, so the UI can say what it would have saved.
        assertThat(alternative.storeCount()).isEqualTo(2);
        assertThat(alternative.scoreCents()).isEqualTo(717);
    }

    @Test
    void neverPlansAStoreThatSellsNothing() {
        // With milk alone, CHEAP+NEAR would buy nothing at NEAR, so that subset is skipped.
        Result result = BasketOptimizer.optimize(
                List.of(new Item(MILK, 1000)), OFFERS, 3, ScoringPolicy.eurosPerHour(0), TRIPS);

        assertThat(result.plans()).hasSize(1);
        assertThat(result.best().orElseThrow().chainCodes()).containsExactly("CHEAP");
        assertThat(result.combinationsChecked()).isEqualTo(3);
    }

    @Test
    void itemsNobodySellsAreReportedAndLeftOut() {
        List<Item> list = List.of(new Item(MILK, 1000), new Item(SAFFRON, 1));

        Result result = BasketOptimizer.optimize(list, OFFERS, 3, ScoringPolicy.eurosPerHour(10), TRIPS);

        assertThat(result.unavailableProductIds()).containsExactly(SAFFRON);
        assertThat(result.best().orElseThrow().purchases()).hasSize(1);
    }

    @Test
    void nothingAvailableMeansNoPlans() {
        Result result = BasketOptimizer.optimize(
                List.of(new Item(SAFFRON, 1)), OFFERS, 3, ScoringPolicy.eurosPerHour(10), TRIPS);

        assertThat(result.plans()).isEmpty();
        assertThat(result.unavailableProductIds()).containsExactly(SAFFRON);
    }

    @Test
    void unreachableSubsetsAreSkipped() {
        TripEstimator nearOnly = chains -> chains.equals(Set.of("NEAR"))
                ? Optional.of(new Trip(List.of(10L), 600, 2_000))
                : Optional.empty();

        Result result = BasketOptimizer.optimize(LIST, OFFERS, 3, ScoringPolicy.eurosPerHour(0), nearOnly);

        assertThat(result.plans()).hasSize(1);
        assertThat(result.best().orElseThrow().chainCodes()).containsExactly("NEAR");
    }

    @Test
    void maxStoresLimitsTheSearch() {
        Result result = BasketOptimizer.optimize(LIST, OFFERS, 1, ScoringPolicy.eurosPerHour(0), TRIPS);

        assertThat(result.plans()).hasSize(1);
        assertThat(result.best().orElseThrow().storeCount()).isEqualTo(1);
        assertThat(result.combinationsChecked()).isEqualTo(2);
    }

    @Test
    void fourChainsAndThreeStoresMeanFourteenCombinations() {
        List<Offer> offers = new ArrayList<>();
        for (String chain : List.of("A", "B", "C", "D")) {
            offers.add(new Offer(chain, MILK, new Pack(1000, 100)));
        }
        TripEstimator anyTrip = chains -> Optional.of(new Trip(List.of(), 60L * chains.size(), 0));

        Result result = BasketOptimizer.optimize(
                List.of(new Item(MILK, 1000)), offers, 3, ScoringPolicy.eurosPerHour(10), anyTrip);

        assertThat(result.combinationsChecked()).isEqualTo(14);
    }

    @Test
    void rejectsInvalidLists() {
        ScoringPolicy policy = ScoringPolicy.eurosPerHour(10);
        assertThatThrownBy(() -> BasketOptimizer.optimize(List.of(), OFFERS, 3, policy, TRIPS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BasketOptimizer.optimize(
                List.of(new Item(MILK, 1000), new Item(MILK, 500)), OFFERS, 3, policy, TRIPS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("twice");
        assertThatThrownBy(() -> BasketOptimizer.optimize(LIST, OFFERS, 4, policy, TRIPS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The optimizer only tries the cheapest chain per item within each subset. A naive search
     * over every assignment of items to chains must never find anything better.
     */
    @Test
    void matchesBruteForceOverEveryAssignment() {
        Random random = new Random(42);
        for (int round = 0; round < 500; round++) {
            Instance instance = Instance.random(random);
            ScoringPolicy policy = new ScoringPolicy(random.nextInt(4_000));
            int maxStores = 1 + random.nextInt(3);

            Result result = BasketOptimizer.optimize(instance.items, instance.offers, maxStores, policy, instance.trips());
            Map<Integer, Long> bruteForce = instance.bestExactScorePerStoreCount(maxStores, policy);

            if (bruteForce.isEmpty()) {
                assertThat(result.plans()).isEmpty();
                continue;
            }
            // The overall best is found exactly.
            long bestBruteForce = bruteForce.values().stream().mapToLong(Long::longValue).min().orElseThrow();
            Plan best = result.best().orElseThrow();
            assertThat(policy.exactScore(best.basketCents(), best.trip().durationSeconds())).isEqualTo(bestBruteForce);
            // No plan claims a score the naive search cannot reach with that many stores. (The
            // naive search may know more multi-store plans: ones that send an item to a pricier
            // chain just to visit it, which the optimizer rightly ignores.)
            for (Plan plan : result.plans()) {
                long score = policy.exactScore(plan.basketCents(), plan.trip().durationSeconds());
                assertThat(score).isGreaterThanOrEqualTo(bruteForce.get(plan.storeCount()));
            }
            for (int i = 1; i < result.plans().size(); i++) {
                Plan previous = result.plans().get(i - 1);
                Plan next = result.plans().get(i);
                assertThat(policy.exactScore(previous.basketCents(), previous.trip().durationSeconds()))
                        .isLessThanOrEqualTo(policy.exactScore(next.basketCents(), next.trip().durationSeconds()));
            }
        }
    }

    /**
     * The more your time is worth, the less time the best plan may take, and the more it may
     * cost. (Not "fewer stores": two close stores can be quicker than one far away.)
     */
    @Test
    void valuingTimeMoreNeverMakesTheBestPlanSlower() {
        Random random = new Random(3);
        for (int round = 0; round < 200; round++) {
            Instance instance = Instance.random(random);
            long previousSeconds = Long.MAX_VALUE;
            long previousBasket = Long.MIN_VALUE;
            for (int euros = 0; euros <= 60; euros++) {
                Optional<Plan> best = BasketOptimizer.optimize(instance.items, instance.offers, 3,
                        ScoringPolicy.eurosPerHour(euros), instance.trips()).best();
                if (best.isEmpty()) {
                    break;
                }
                assertThat(best.get().trip().durationSeconds()).isLessThanOrEqualTo(previousSeconds);
                assertThat(best.get().basketCents()).isGreaterThanOrEqualTo(previousBasket);
                previousSeconds = best.get().trip().durationSeconds();
                previousBasket = best.get().basketCents();
            }
        }
    }

    private static String chainOf(Plan plan, long productId) {
        return plan.purchases().stream()
                .filter(purchase -> purchase.productId() == productId)
                .map(Purchase::chainCode)
                .findFirst()
                .orElseThrow();
    }

    /** A random market: up to 4 chains, up to 4 items, random packs, random detours per chain. */
    private record Instance(List<String> chains, List<Item> items, List<Offer> offers, Map<String, Long> detour) {

        static Instance random(Random random) {
            List<String> chains = List.of("A", "B", "C", "D").subList(0, 1 + random.nextInt(4));
            List<Item> items = new ArrayList<>();
            List<Offer> offers = new ArrayList<>();
            int itemCount = 1 + random.nextInt(4);
            for (long product = 1; product <= itemCount; product++) {
                items.add(new Item(product, 100L * (1 + random.nextInt(20))));
                for (String chain : chains) {
                    if (random.nextDouble() < 0.7) {
                        int sizes = 1 + random.nextInt(2);
                        for (int s = 0; s < sizes; s++) {
                            offers.add(new Offer(chain, product, new Pack(100L * (1 + random.nextInt(10)), 30 + random.nextInt(300))));
                        }
                    }
                }
            }
            Map<String, Long> detour = new HashMap<>();
            for (String chain : chains) {
                detour.put(chain, 60L + random.nextInt(1_200));
            }
            return new Instance(chains, items, offers, detour);
        }

        /** Trip time grows with every chain added, as real trips do. */
        TripEstimator trips() {
            return subset -> Optional.of(new Trip(List.of(), 120 + subset.stream().mapToLong(detour::get).sum(), 0));
        }

        /** Every assignment of items to chains that sell them; best exact score per number of stores used. */
        Map<Integer, Long> bestExactScorePerStoreCount(int maxStores, ScoringPolicy policy) {
            Map<Integer, Long> best = new HashMap<>();
            assign(0, new ArrayList<>(), maxStores, policy, best);
            return best;
        }

        private void assign(int index, List<String> chosen, int maxStores, ScoringPolicy policy, Map<Integer, Long> best) {
            List<Item> sold = items.stream().filter(item -> anyChainSells(item.productId())).toList();
            if (index == sold.size()) {
                if (sold.isEmpty()) {
                    return;
                }
                Set<String> used = new TreeSet<>(chosen);
                if (used.size() > maxStores) {
                    return;
                }
                long basket = 0;
                for (int i = 0; i < sold.size(); i++) {
                    basket += cost(chosen.get(i), sold.get(i));
                }
                long seconds = trips().fastestTrip(used).orElseThrow().durationSeconds();
                best.merge(used.size(), policy.exactScore(basket, seconds), Math::min);
                return;
            }
            for (String chain : chains) {
                if (packs(chain, sold.get(index).productId()).isEmpty()) {
                    continue;
                }
                chosen.add(chain);
                assign(index + 1, chosen, maxStores, policy, best);
                chosen.remove(chosen.size() - 1);
            }
        }

        private boolean anyChainSells(long productId) {
            return offers.stream().anyMatch(offer -> offer.productId() == productId);
        }

        private List<Pack> packs(String chain, long productId) {
            return offers.stream()
                    .filter(offer -> offer.chainCode().equals(chain) && offer.productId() == productId)
                    .map(Offer::pack)
                    .toList();
        }

        private long cost(String chain, Item item) {
            return PackSelector.cheapest(item.neededAmount(), packs(chain, item.productId())).orElseThrow().costCents();
        }
    }
}
