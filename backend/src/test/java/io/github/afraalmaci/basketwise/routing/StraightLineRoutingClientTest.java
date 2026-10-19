package io.github.afraalmaci.basketwise.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class StraightLineRoutingClientTest {

    private final StraightLineRoutingClient client = new StraightLineRoutingClient();

    // Exactly 1 km apart (north-south), so the numbers are easy to check by hand.
    private final GeoPoint a = new GeoPoint(52.0, 4.3);
    private final GeoPoint b = new GeoPoint(52.0 + 1000 / 111_194.93, 4.3);

    @Test
    void appliesDetourFactorAndModeSpeed() {
        Leg bike = StraightLineRoutingClient.estimate(a, b, TravelMode.BIKE);
        Leg walk = StraightLineRoutingClient.estimate(a, b, TravelMode.WALK);

        // 1 km * 1.3 detour = 1300 m. Bike 15 km/h = 312 s, walk 5 km/h = 936 s.
        assertThat(bike.distanceMeters()).isEqualTo(1300);
        assertThat(bike.durationSeconds()).isEqualTo(312);
        assertThat(walk.distanceMeters()).isEqualTo(1300);
        assertThat(walk.durationSeconds()).isEqualTo(936);
    }

    @Test
    void matrixHasOneRowPerSourceAndIsMarkedEstimated() {
        TravelMatrix matrix = client.matrix(List.of(a), List.of(a, b), TravelMode.BIKE);

        assertThat(matrix.estimated()).isTrue();
        assertThat(matrix.sourceCount()).isEqualTo(1);
        assertThat(matrix.destinationCount()).isEqualTo(2);
        assertThat(matrix.leg(0, 0)).isEqualTo(new Leg(0, 0));
        assertThat(matrix.leg(0, 1).distanceMeters()).isEqualTo(1300);
    }

    @Test
    void routeGeometryIsTheWaypointsThemselves() {
        Route route = client.route(List.of(a, b, a), TravelMode.WALK);

        assertThat(route.estimated()).isTrue();
        assertThat(route.geometry()).containsExactly(a, b, a);
        assertThat(route.legs()).hasSize(2);
        assertThat(route.total()).isEqualTo(new Leg(1872, 2600));
    }

    @Test
    void routeNeedsTwoWaypoints() {
        assertThatThrownBy(() -> client.route(List.of(a), TravelMode.BIKE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
