package io.github.afraalmaci.basketwise.planning;

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
 * {@code POST /api/optimize} on the full stack with the demo data. Without an ORS key the
 * straight-line client answers, so results are deterministic and need no network.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "basketwise.routing.ors.api-key=")
class PlanningApiIntegrationTest {

    private static final String ORIGIN = "\"origin\": {\"latitude\": 52.0809, \"longitude\": 4.3245}";

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void optimizesAShoppingList() throws Exception {
        HttpResponse<String> response = post("""
                {%s, "mode": "bike", "valueOfTimeEurPerHour": 10,
                 "items": [{"productId": %d, "quantity": 2000},
                           {"productId": %d, "quantity": 10},
                           {"productId": %d, "quantity": 500}]}
                """.formatted(ORIGIN, productId("halfvolle-melk"), productId("scharreleieren"), productId("tomaten")));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "\"mode\":\"bike\"", "\"valueOfTimeEurPerHour\":10.0", "\"estimated\":true",
                "\"combinationsChecked\":14", "\"unavailable\":[]",
                "\"plans\":[{\"storeCount\":", "\"basketCents\":", "\"scoreCents\":",
                "\"name\":\"Halfvolle melk\"", "\"neededAmount\":2000", "\"quantityText\":",
                "\"geometry\":[[52.0809,4.3245]",
                "\"comparison\":{\"chains\":[{\"chainCode\":\"BUDGETBAAS\"", "\"nearestSeconds\":",
                "\"itemsCovered\":", "\"cheapest\":true");
    }

    @Test
    void missingSettingsUseDefaults() throws Exception {
        HttpResponse<String> response = post("""
                {%s, "items": [{"productId": %d, "quantity": 1000}]}
                """.formatted(ORIGIN, productId("halfvolle-melk")));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"mode\":\"bike\"", "\"valueOfTimeEurPerHour\":10.0");
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        long milk = productId("halfvolle-melk");

        assertThat(post("{" + ORIGIN + ", \"items\": []}").statusCode()).isEqualTo(400);
        assertThat(post("{" + ORIGIN + ", \"items\": [{\"productId\": 999999, \"quantity\": 1}]}").statusCode())
                .isEqualTo(404);
        assertThat(post("{" + ORIGIN + ", \"items\": [{\"productId\": " + milk + ", \"quantity\": 0}]}").statusCode())
                .isEqualTo(400);
        assertThat(post("{" + ORIGIN + ", \"items\": [{\"productId\": " + milk + ", \"quantity\": 99000}]}").statusCode())
                .isEqualTo(400);
        assertThat(post("{" + ORIGIN + ", \"items\": [{\"productId\": " + milk + ", \"quantity\": 1000},"
                + " {\"productId\": " + milk + ", \"quantity\": 500}]}").statusCode())
                .isEqualTo(400);
        assertThat(post("{" + ORIGIN + ", \"maxStores\": 4, \"items\": [{\"productId\": " + milk + ", \"quantity\": 1}]}")
                .statusCode()).isEqualTo(400);

        HttpResponse<String> amsterdam = post("""
                {"origin": {"latitude": 52.3676, "longitude": 4.9041},
                 "items": [{"productId": %d, "quantity": 1000}]}
                """.formatted(milk));
        assertThat(amsterdam.statusCode()).isEqualTo(400);
        assertThat(amsterdam.body()).contains("Outside demo area");
    }

    private long productId(String slug) {
        Long id = jdbc.queryForObject("SELECT id FROM product WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    private HttpResponse<String> post(String json) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/optimize"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
