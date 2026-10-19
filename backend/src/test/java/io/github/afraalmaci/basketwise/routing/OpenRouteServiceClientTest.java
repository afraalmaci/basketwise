package io.github.afraalmaci.basketwise.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Talks to a fake HTTP server instead of the real API: checks the request we send and how
 * we read the response, without an API key or network access.
 */
class OpenRouteServiceClientTest {

    private static final String BASE = "https://ors.test";

    private final GeoPoint home = new GeoPoint(52.0809, 4.3245);
    private final GeoPoint storeA = new GeoPoint(52.0786, 4.3104);
    private final GeoPoint storeB = new GeoPoint(52.0681, 4.3059);

    private MockRestServiceServer server;
    private OpenRouteServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenRouteServiceClient(builder.build(), new StraightLineRoutingClient());
    }

    @Test
    void matrixSendsLongitudeFirstAndReadsDurationsAndDistances() {
        server.expect(requestTo(BASE + "/v2/matrix/cycling-regular"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "test-key"))
                .andExpect(jsonPath("$.locations[0][0]").value(4.3245))
                .andExpect(jsonPath("$.locations[0][1]").value(52.0809))
                .andExpect(jsonPath("$.sources[0]").value("0"))
                .andExpect(jsonPath("$.destinations[1]").value("2"))
                .andRespond(withSuccess("""
                        {"durations": [[312.4, null]], "distances": [[1203.6, null]],
                         "metadata": {"service": "matrix"}}
                        """, MediaType.APPLICATION_JSON));

        TravelMatrix matrix = client.matrix(List.of(home), List.of(storeA, storeB), TravelMode.BIKE);

        assertThat(matrix.estimated()).isFalse();
        assertThat(matrix.leg(0, 0)).isEqualTo(new Leg(312, 1204));
        assertThat(matrix.leg(0, 1)).isNull(); // ORS could not route there
        server.verify();
    }

    @Test
    void routeReadsGeometryAndOneLegPerSegment() {
        server.expect(requestTo(BASE + "/v2/directions/foot-walking/geojson"))
                .andExpect(jsonPath("$.coordinates.length()").value(3))
                .andExpect(jsonPath("$.radiuses[0]").value(-1))
                .andRespond(withSuccess("""
                        {"type": "FeatureCollection",
                         "features": [{
                           "type": "Feature",
                           "geometry": {"type": "LineString",
                                        "coordinates": [[4.3245, 52.0809], [4.3180, 52.0795], [4.3104, 52.0786], [4.3245, 52.0809]]},
                           "properties": {"segments": [{"distance": 1100.0, "duration": 790.2, "steps": []},
                                                       {"distance": 1150.0, "duration": 828.0, "steps": []}],
                                          "summary": {"distance": 2250.0, "duration": 1618.2}}
                         }]}
                        """, MediaType.APPLICATION_JSON));

        Route route = client.route(List.of(home, storeA, home), TravelMode.WALK);

        assertThat(route.estimated()).isFalse();
        assertThat(route.geometry()).hasSize(4);
        assertThat(route.geometry().get(1)).isEqualTo(new GeoPoint(52.0795, 4.3180)); // swapped back to lat, lon
        assertThat(route.legs()).containsExactly(new Leg(790, 1100), new Leg(828, 1150));
        server.verify();
    }

    @Test
    void fallsBackToStraightLineWhenTheApiFails() {
        server.expect(requestTo(BASE + "/v2/matrix/cycling-regular")).andRespond(withServerError());

        TravelMatrix matrix = client.matrix(List.of(home), List.of(storeA), TravelMode.BIKE);

        assertThat(matrix.estimated()).isTrue();
        assertThat(matrix.leg(0, 0)).isEqualTo(StraightLineRoutingClient.estimate(home, storeA, TravelMode.BIKE));
    }

    @Test
    void fallsBackWhenTheResponseHasAnUnexpectedShape() {
        server.expect(requestTo(BASE + "/v2/directions/cycling-regular/geojson"))
                .andRespond(withSuccess("{\"features\": []}", MediaType.APPLICATION_JSON));

        Route route = client.route(List.of(home, storeA), TravelMode.BIKE);

        assertThat(route.estimated()).isTrue();
        assertThat(route.geometry()).containsExactly(home, storeA);
    }
}
