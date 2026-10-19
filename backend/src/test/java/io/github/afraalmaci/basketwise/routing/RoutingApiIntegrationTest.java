package io.github.afraalmaci.basketwise.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Full stack with the demo data. No ORS_API_KEY is set in tests, so the app uses the
 * straight-line client: results are deterministic and no network is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "basketwise.routing.ors.api-key=")
class RoutingApiIntegrationTest {

    private static final String CENTRAAL = "lat=52.0809&lon=4.3245";

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void configDescribesTheServiceArea() throws Exception {
        HttpResponse<String> response = get("/api/routing/config");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "\"provider\":\"straight-line\"", "\"name\":\"Den Haag\"",
                "\"modes\":[\"bike\",\"walk\"]", "\"maxStops\":3");
    }

    @Test
    void nearestReturnsOneStorePerChain() throws Exception {
        HttpResponse<String> response = get("/api/routing/nearest?" + CENTRAAL + "&mode=walk");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "\"mode\":\"walk\"", "\"estimated\":true",
                "\"chainCode\":\"BUDGETBAAS\"", "\"chainCode\":\"HOEKWINKEL\"",
                "\"chainCode\":\"VERSPLEIN\"", "\"chainCode\":\"MARKTWIJS\"",
                "\"durationSeconds\":");
        assertThat(response.body().split("\"storeId\"")).hasSize(5); // four entries
    }

    @Test
    void tripReturnsFastestOptionFirstWithLegsAndGeometry() throws Exception {
        long centrum = storeId("Hoekwinkel Centrum");
        long schilderswijk = storeId("Versplein Schilderswijk");

        HttpResponse<String> response = post("/api/routing/trip", """
                {"origin": {"latitude": 52.0809, "longitude": 4.3245},
                 "storeIds": [%d, %d], "mode": "bike"}
                """.formatted(centrum, schilderswijk));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "\"options\":[{", "\"storeName\":\"Versplein Schilderswijk\"",
                "\"totalDurationSeconds\":", "\"extraSeconds\":0", "\"geometry\":[[52.0809,4.3245]");
        // Up to three options; each one has three legs for two stops.
        int options = response.body().split("\"extraSeconds\"").length - 1;
        assertThat(options).isBetween(1, 3);
        assertThat(response.body().split("\"distanceMeters\"").length - 1).isEqualTo(options * 3);
    }

    @Test
    void locationOutsideTheDemoAreaIsRejected() throws Exception {
        HttpResponse<String> response = get("/api/routing/nearest?lat=52.3676&lon=4.9041");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Outside demo area");
    }

    @Test
    void invalidTripsAreRejected() throws Exception {
        String origin = "\"origin\": {\"latitude\": 52.0809, \"longitude\": 4.3245}";

        assertThat(post("/api/routing/trip", "{" + origin + ", \"storeIds\": [1, 2, 3, 4]}").statusCode())
                .isEqualTo(400);
        assertThat(post("/api/routing/trip", "{" + origin + ", \"storeIds\": []}").statusCode())
                .isEqualTo(400);
        assertThat(post("/api/routing/trip", "{" + origin + ", \"storeIds\": [999999]}").statusCode())
                .isEqualTo(404);
        assertThat(get("/api/routing/nearest?" + CENTRAAL + "&mode=car").statusCode())
                .isEqualTo(400);
    }

    private long storeId(String name) {
        Long id = jdbc.queryForObject("SELECT id FROM store WHERE name = ?", Long.class, name);
        assertThat(id).isNotNull();
        return id;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
