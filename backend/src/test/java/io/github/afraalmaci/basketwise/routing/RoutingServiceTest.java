package io.github.afraalmaci.basketwise.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.afraalmaci.basketwise.routing.RoutingExceptions.OutsideServiceAreaException;
import io.github.afraalmaci.basketwise.routing.RoutingExceptions.UnknownStoreException;
import io.github.afraalmaci.basketwise.routing.RoutingService.NearestStores;
import io.github.afraalmaci.basketwise.routing.RoutingService.Trip;
import io.github.afraalmaci.basketwise.routing.RoutingService.TripPlan;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutingServiceTest {

    private static final GeoPoint HOME = new GeoPoint(52.0700, 4.3000);

    private static final ServiceArea AREA =
            new ServiceArea("Test", 52.0, 4.2, 52.2, 4.4, new GeoPoint(52.08, 4.32));

    // Two chains; stores placed due east of home at increasing distances.
    private static final StoreLocation A_NEAR = store(1, "A", 0.005);
    private static final StoreLocation A_FAR = store(2, "A", 0.030);
    private static final StoreLocation B_MID = store(3, "B", 0.015);

    private final RoutingService service = new RoutingService(
            new StraightLineRoutingClient(), AREA, () -> List.of(A_FAR, B_MID, A_NEAR));

    @Test
    void returnsTheClosestStorePerChainSortedByTime() {
        NearestStores nearest = service.nearestPerChain(HOME, TravelMode.BIKE);

        assertThat(nearest.estimated()).isTrue();
        assertThat(nearest.stores()).extracting(n -> n.store().id()).containsExactly(1L, 3L);
        assertThat(nearest.stores().get(0).leg().durationSeconds())
                .isLessThan(nearest.stores().get(1).leg().durationSeconds());
    }

    @Test
    void walkingTakesLongerThanCycling() {
        long bike = service.nearestPerChain(HOME, TravelMode.BIKE).stores().get(0).leg().durationSeconds();
        long walk = service.nearestPerChain(HOME, TravelMode.WALK).stores().get(0).leg().durationSeconds();

        // 15 km/h vs 5 km/h; each value is rounded to whole seconds on its own.
        assertThat(walk).isCloseTo(bike * 3, within(2L));
    }

    @Test
    void tripVisitsStoresInTheFastestOrderRegardlessOfInputOrder() {
        // Two stores east of home and one west. Any good loop visits the two eastern stores
        // back to back; going east, back west past home, then east again would be wasteful.
        StoreLocation west = store(4, "B", -0.010);
        RoutingService withWest = new RoutingService(
                new StraightLineRoutingClient(), AREA, () -> List.of(A_NEAR, A_FAR, west));

        Trip trip = withWest.planTrip(HOME, List.of(2L, 4L, 1L), TravelMode.BIKE).fastest();

        assertThat(trip.estimated()).isTrue();
        assertThat(trip.stops()).hasSize(3);
        // Never zig-zag across home: the two eastern stores are visited one after another.
        List<Long> ids = trip.stops().stream().map(StoreLocation::id).toList();
        assertThat(Math.abs(ids.indexOf(1L) - ids.indexOf(2L))).isEqualTo(1);
        // Route: home, three stops, home. One leg per hop.
        assertThat(trip.route().geometry()).hasSize(5);
        assertThat(trip.route().geometry().get(0)).isEqualTo(HOME);
        assertThat(trip.route().geometry().get(4)).isEqualTo(HOME);
        assertThat(trip.route().legs()).hasSize(4);
    }

    @Test
    void offersSiblingBranchesAsAlternativesFastestFirst() {
        // Chain A has three branches east of home at 1.0, 1.2 and 1.4 km-ish; B has one.
        StoreLocation a1 = store(11, "A", 0.010);
        StoreLocation a2 = store(12, "A", 0.012);
        StoreLocation a3 = store(13, "A", 0.014);
        StoreLocation far = store(14, "A", 0.060);
        RoutingService withBranches = new RoutingService(
                new StraightLineRoutingClient(), AREA, () -> List.of(a3, far, a1, a2));

        // The user picked the 1.4 km branch; the 1.0 km sibling is faster and comes first.
        TripPlan plan = withBranches.planTrip(HOME, List.of(13L), TravelMode.BIKE);

        assertThat(plan.options()).extracting(trip -> trip.stops().get(0).id()).containsExactly(11L, 12L, 13L);
        assertThat(plan.options().get(0).route().total().durationSeconds())
                .isLessThan(plan.options().get(2).route().total().durationSeconds());
    }

    @Test
    void branchCandidatesAreTheChosenStorePlusTheNearestSiblings() {
        StoreLocation a1 = store(11, "A", 0.010);
        StoreLocation a2 = store(12, "A", 0.012);
        StoreLocation a3 = store(13, "A", 0.014);
        StoreLocation far = store(14, "A", 0.060);
        StoreLocation otherChain = store(15, "B", 0.001);

        List<StoreLocation> candidates = RoutingService.branchCandidates(
                far, List.of(a3, far, otherChain, a1, a2), HOME);

        assertThat(candidates).extracting(StoreLocation::id).containsExactly(14L, 11L, 12L);
    }

    @Test
    void rejectsLocationsOutsideTheServiceArea() {
        GeoPoint amsterdam = new GeoPoint(52.3676, 4.9041);

        assertThatThrownBy(() -> service.nearestPerChain(amsterdam, TravelMode.BIKE))
                .isInstanceOf(OutsideServiceAreaException.class);
        assertThatThrownBy(() -> service.planTrip(amsterdam, List.of(1L), TravelMode.BIKE))
                .isInstanceOf(OutsideServiceAreaException.class);
    }

    @Test
    void validatesTheStoreSelection() {
        assertThatThrownBy(() -> service.planTrip(HOME, List.of(), TravelMode.BIKE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.planTrip(HOME, List.of(1L, 2L, 3L, 1L), TravelMode.BIKE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.planTrip(HOME, List.of(1L, 1L), TravelMode.BIKE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only once");
        assertThatThrownBy(() -> service.planTrip(HOME, List.of(99L), TravelMode.BIKE))
                .isInstanceOf(UnknownStoreException.class);
    }

    private static StoreLocation store(long id, String chain, double longitudeOffset) {
        return new StoreLocation(id, chain, chain + " " + id,
                new GeoPoint(HOME.latitude(), HOME.longitude() + longitudeOffset));
    }
}
