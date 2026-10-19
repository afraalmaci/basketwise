package io.github.afraalmaci.basketwise.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TravelModeTest {

    @Test
    void parsesApiNamesIgnoringCase() {
        assertThat(TravelMode.fromApiName("bike")).isEqualTo(TravelMode.BIKE);
        assertThat(TravelMode.fromApiName(" WALK ")).isEqualTo(TravelMode.WALK);
    }

    @Test
    void missingModeMeansBike() {
        assertThat(TravelMode.fromApiName(null)).isEqualTo(TravelMode.BIKE);
        assertThat(TravelMode.fromApiName("")).isEqualTo(TravelMode.BIKE);
    }

    @Test
    void rejectsUnknownMode() {
        assertThatThrownBy(() -> TravelMode.fromApiName("car"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("car");
    }

    @Test
    void mapsToOpenRouteServiceProfiles() {
        assertThat(TravelMode.BIKE.orsProfile()).isEqualTo("cycling-regular");
        assertThat(TravelMode.WALK.orsProfile()).isEqualTo("foot-walking");
    }
}
