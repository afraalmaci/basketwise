package io.github.afraalmaci.basketwise.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.afraalmaci.basketwise.catalog.CatalogDtos.OfferDto;
import io.github.afraalmaci.basketwise.catalog.CatalogDtos.ProductDto;
import io.github.afraalmaci.basketwise.optimization.ScoringPolicy;
import io.github.afraalmaci.basketwise.planning.PlanningService.ListItem;
import io.github.afraalmaci.basketwise.planning.PlanningService.Outcome;
import io.github.afraalmaci.basketwise.planning.PlanningService.Request;
import io.github.afraalmaci.basketwise.planning.PlanningService.RoutedPlan;
import io.github.afraalmaci.basketwise.planning.PlanningService.UnknownProductException;
import io.github.afraalmaci.basketwise.routing.GeoPoint;
import io.github.afraalmaci.basketwise.routing.Route;
import io.github.afraalmaci.basketwise.routing.RoutingClient;
import io.github.afraalmaci.basketwise.routing.RoutingExceptions.OutsideServiceAreaException;
import io.github.afraalmaci.basketwise.routing.ServiceArea;
import io.github.afraalmaci.basketwise.routing.StoreLocation;
import io.github.afraalmaci.basketwise.routing.StraightLineRoutingClient;
import io.github.afraalmaci.basketwise.routing.TravelMatrix;
import io.github.afraalmaci.basketwise.routing.TravelMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanningServiceTest {

    private static final GeoPoint HOME = new GeoPoint(52.0700, 4.3000);
    private static final ServiceArea AREA =
            new ServiceArea("Test", 52.0, 4.2, 52.2, 4.4, new GeoPoint(52.08, 4.32));

    // NEAR sells everything, expensively, 0.5 km east. CHEAP sells milk only, 2 km east.
    private static final StoreLocation NEAR_STORE = store(1, "NEAR", 0.007);
    private static final StoreLocation CHEAP_STORE = store(2, "CHEAP", 0.030);
    private static final StoreLocation CHEAP_FARTHER = store(3, "CHEAP", 0.060);

    private static final ProductDto MILK = new ProductDto(10, "melk", "Halfvolle melk", "dairy", "ml", List.of(
            new OfferDto("NEAR", "Melk 1L", "1 L", 1000, 150, 150),
            new OfferDto("CHEAP", "Melk", "1000 ml", 1000, 80, 80),
            new OfferDto("CHEAP", "Melk groot", "1,5 L", 1500, 110, 73)));
    private static final ProductDto COLA = new ProductDto(11, "cola", "Cola", "drinks", "ml", List.of(
            new OfferDto("NEAR", "Cola", "1,5 L", 1500, 200, 133)));
    private static final ProductDto SAFFRON = new ProductDto(12, "saffraan", "Saffraan", "spices", "g", List.of());

    private final CountingRoutingClient routing = new CountingRoutingClient();
    private final PlanningService service = new PlanningService(
            routing, AREA, () -> List.of(NEAR_STORE, CHEAP_STORE, CHEAP_FARTHER), PlanningServiceTest::products);

    @Test
    void returnsTheBestPlanPerStoreCountWithStopsInVisitingOrder() {
        Outcome outcome = service.plan(request(0, new ListItem(10, 2000), new ListItem(11, 1500)));

        // At 0 EUR/h only price counts: milk at CHEAP, cola at NEAR.
        RoutedPlan best = outcome.plans().get(0);
        assertThat(best.plan().storeCount()).isEqualTo(2);
        assertThat(best.plan().basketCents()).isEqualTo(160 + 200);
        assertThat(best.stops()).hasSize(2);
        PlanningService.Stop cheap = best.stops().stream()
                .filter(stop -> stop.store().chainCode().equals("CHEAP")).findFirst().orElseThrow();
        assertThat(cheap.store()).isEqualTo(CHEAP_STORE); // the nearer branch, not the farther one
        assertThat(cheap.subtotalCents()).isEqualTo(160); // 2 x 1 L, cheaper than 1,5 L + 1 L
        assertThat(cheap.purchases()).hasSize(1);
        // The route follows the stops in order: home, both stores, home.
        assertThat(best.route().legs()).hasSize(3);
        assertThat(best.route().geometry().get(1)).isEqualTo(best.stops().get(0).store().location());
        assertThat(outcome.estimated()).isTrue();

        // The one-store alternative is NEAR alone.
        RoutedPlan oneStore = outcome.plans().get(1);
        assertThat(oneStore.plan().storeCount()).isEqualTo(1);
        assertThat(oneStore.plan().basketCents()).isEqualTo(300 + 200);
    }

    @Test
    void valuingTimeHighlyPrefersTheNearStore() {
        Outcome outcome = service.plan(request(60, new ListItem(10, 2000), new ListItem(11, 1500)));

        assertThat(outcome.plans().get(0).plan().chainCodes()).containsExactly("NEAR");
    }

    @Test
    void asksForTravelTimesOnceHoweverManyCombinationsAreTried() {
        Outcome outcome = service.plan(request(10, new ListItem(10, 2000), new ListItem(11, 1500)));

        assertThat(outcome.combinationsChecked()).isEqualTo(3); // {CHEAP}, {NEAR}, {CHEAP, NEAR}
        assertThat(routing.matrixCalls).isEqualTo(1);
        assertThat(routing.matrixPoints).isEqualTo(4); // origin + 1 NEAR + 2 CHEAP branches
        assertThat(routing.routeCalls).isEqualTo(outcome.plans().size()); // one drawn route per plan
    }

    @Test
    void reportsItemsNobodySells() {
        Outcome outcome = service.plan(request(10, new ListItem(10, 1000), new ListItem(12, 5)));

        assertThat(outcome.unavailable()).containsExactly(SAFFRON);
        assertThat(outcome.plans().get(0).plan().purchases()).hasSize(1);
    }

    @Test
    void nothingSoldMeansNoPlansAndNoRoutingCalls() {
        Outcome outcome = service.plan(request(10, new ListItem(12, 5)));

        assertThat(outcome.plans()).isEmpty();
        assertThat(routing.matrixCalls).isEqualTo(0);
    }

    @Test
    void rejectsBadRequests() {
        assertThatThrownBy(() -> service.plan(request(10, new ListItem(99, 1))))
                .isInstanceOf(UnknownProductException.class);
        assertThatThrownBy(() -> service.plan(request(10, new ListItem(10, 1000), new ListItem(10, 500))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only once");
        assertThatThrownBy(() -> service.plan(request(10, new ListItem(10, 30_000))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be 1 to 25000 ml");
        assertThatThrownBy(() -> service.plan(request(10)))
                .isInstanceOf(IllegalArgumentException.class);
        Request farAway = new Request(new GeoPoint(52.37, 4.90), TravelMode.BIKE,
                List.of(new ListItem(10, 1000)), 3, ScoringPolicy.eurosPerHour(10));
        assertThatThrownBy(() -> service.plan(farAway)).isInstanceOf(OutsideServiceAreaException.class);
    }

    @Test
    void nearestBranchesAreLimitedPerChain() {
        List<StoreLocation> stores = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            stores.add(store(100 + i, "BIG", 0.005 * (6 - i)));
        }

        List<StoreLocation> nearest = PlanningService.nearestBranches("BIG", stores, HOME);

        assertThat(nearest).hasSize(3);
        assertThat(nearest.get(0).id()).isEqualTo(105L);
    }

    private static Request request(double eurosPerHour, ListItem... items) {
        return new Request(HOME, TravelMode.BIKE, List.of(items), 3, ScoringPolicy.eurosPerHour(eurosPerHour));
    }

    private static List<ProductDto> products(Collection<Long> ids) {
        return List.of(MILK, COLA, SAFFRON).stream().filter(product -> ids.contains(product.id())).toList();
    }

    /** A store due east of home. */
    private static StoreLocation store(long id, String chain, double eastDegrees) {
        return new StoreLocation(id, chain, chain + " " + id, new GeoPoint(HOME.latitude(), HOME.longitude() + eastDegrees));
    }

    /** Straight-line routing that counts how it is used. */
    private static final class CountingRoutingClient implements RoutingClient {
        private final StraightLineRoutingClient delegate = new StraightLineRoutingClient();
        int matrixCalls;
        int matrixPoints;
        int routeCalls;

        @Override
        public TravelMatrix matrix(List<GeoPoint> sources, List<GeoPoint> destinations, TravelMode mode) {
            matrixCalls++;
            matrixPoints = sources.size();
            return delegate.matrix(sources, destinations, mode);
        }

        @Override
        public Route route(List<GeoPoint> waypoints, TravelMode mode) {
            routeCalls++;
            return delegate.route(waypoints, mode);
        }

        @Override
        public String provider() {
            return delegate.provider();
        }
    }
}
