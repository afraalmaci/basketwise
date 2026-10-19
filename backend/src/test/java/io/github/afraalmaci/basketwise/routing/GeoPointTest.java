package io.github.afraalmaci.basketwise.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoPointTest {

    @Test
    void distanceBetweenKnownPoints() {
        // Den Haag Centraal to Den Haag HS: about 1.3 km as the crow flies.
        GeoPoint centraal = new GeoPoint(52.0809, 4.3245);
        GeoPoint hollandsSpoor = new GeoPoint(52.0696, 4.3225);

        assertThat(centraal.distanceMetersTo(hollandsSpoor)).isCloseTo(1265, within(15.0));
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        GeoPoint a = new GeoPoint(52.0, 4.3);
        GeoPoint b = new GeoPoint(53.0, 4.3);

        assertThat(a.distanceMetersTo(b)).isCloseTo(111_195, within(10.0));
    }

    @Test
    void distanceIsSymmetricAndZeroToItself() {
        GeoPoint a = new GeoPoint(52.0561, 4.2972);
        GeoPoint b = new GeoPoint(52.1046, 4.2768);

        assertThat(a.distanceMetersTo(b)).isEqualTo(b.distanceMetersTo(a));
        assertThat(a.distanceMetersTo(a)).isZero();
    }

    @Test
    void rejectsInvalidCoordinates() {
        assertThatThrownBy(() -> new GeoPoint(91, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(52, 181)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(Double.NaN, 4)).isInstanceOf(IllegalArgumentException.class);
    }
}
