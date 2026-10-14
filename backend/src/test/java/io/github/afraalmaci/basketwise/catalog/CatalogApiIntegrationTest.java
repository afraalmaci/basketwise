package io.github.afraalmaci.basketwise.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs against a real PostgreSQL with the demo data loaded on startup.
 * Response bodies are checked as strings to stay independent of the JSON library.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogApiIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void demoDataIsLoaded() {
        assertThat(count("chain")).isEqualTo(4);
        assertThat(count("store")).isEqualTo(20);
        assertThat(count("product")).isGreaterThan(150);
        assertThat(count("offer")).isGreaterThan(count("product"));
    }

    @Test
    void listsStoresWithCoordinates() throws Exception {
        HttpResponse<String> response = get("/api/stores");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"name\":\"Hoekwinkel Centrum\"", "\"latitude\":52.0786");
    }

    @Test
    void listsChainsWithStoreCounts() throws Exception {
        HttpResponse<String> response = get("/api/chains");

        assertThat(response.body()).contains("\"code\":\"HOEKWINKEL\"", "\"storeCount\":8");
    }

    @Test
    void searchFindsProductsAndReturnsOffersWithUnitPrices() throws Exception {
        HttpResponse<String> response = get("/api/products/search?q=" + encode("halfvolle melk"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .startsWith("[{")
                .contains("\"name\":\"Halfvolle melk\"", "\"chainCode\":\"HOEKWINKEL\"", "\"unitPriceCents\":");
    }

    @Test
    void searchToleratesTypos() throws Exception {
        assertThat(get("/api/products/search?q=" + encode("halvolle melk")).body())
                .contains("\"name\":\"Halfvolle melk\"");
    }

    @Test
    void tooShortQueryReturnsEmptyList() throws Exception {
        assertThat(get("/api/products/search?q=m").body()).isEqualTo("[]");
    }

    @Test
    void likeWildcardsAreTreatedAsText() throws Exception {
        assertThat(get("/api/products/search?q=" + encode("%%")).body()).isEqualTo("[]");
    }

    private long count(String table) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return count == null ? 0 : count;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
