package io.github.afraalmaci.basketwise.routing;

import io.github.afraalmaci.basketwise.catalog.CatalogRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires the plain-Java routing classes into Spring. This is the only place that decides
 * which {@link RoutingClient} the app uses:
 * <ul>
 *   <li>no {@code ORS_API_KEY}: straight-line estimates (local dev, tests, CI)</li>
 *   <li>with a key: OpenRouteService, cached, falling back to straight lines on errors</li>
 * </ul>
 */
@Configuration
class RoutingConfig {

    private static final Logger log = LoggerFactory.getLogger(RoutingConfig.class);
    private static final int CACHE_ENTRIES = 500;

    @Bean
    RoutingClient routingClient(@Value("${basketwise.routing.ors.api-key:}") String apiKey,
                                @Value("${basketwise.routing.ors.base-url}") String baseUrl) {
        StraightLineRoutingClient straightLine = new StraightLineRoutingClient();
        if (apiKey.isBlank()) {
            log.info("No ORS_API_KEY set: travel times are straight-line estimates");
            return straightLine;
        }

        // Fail fast: a slow routing API should not hang the UI; the fallback answers instead.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(8));

        RestClient http = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, apiKey)
                .build();
        log.info("Routing with OpenRouteService at {}", baseUrl);
        return new CachingRoutingClient(new OpenRouteServiceClient(http, straightLine), CACHE_ENTRIES);
    }

    @Bean
    ServiceArea serviceArea(@Value("${basketwise.routing.service-area.name}") String name,
                            @Value("${basketwise.routing.service-area.min-latitude}") double minLatitude,
                            @Value("${basketwise.routing.service-area.min-longitude}") double minLongitude,
                            @Value("${basketwise.routing.service-area.max-latitude}") double maxLatitude,
                            @Value("${basketwise.routing.service-area.max-longitude}") double maxLongitude,
                            @Value("${basketwise.routing.service-area.default-latitude}") double defaultLatitude,
                            @Value("${basketwise.routing.service-area.default-longitude}") double defaultLongitude) {
        return new ServiceArea(name, minLatitude, minLongitude, maxLatitude, maxLongitude,
                new GeoPoint(defaultLatitude, defaultLongitude));
    }

    @Bean
    RoutingService routingService(RoutingClient routingClient, ServiceArea serviceArea, CatalogRepository catalog) {
        return new RoutingService(routingClient, serviceArea, () -> catalog.findStores().stream()
                .map(store -> new StoreLocation(store.id(), store.chainCode(), store.name(),
                        new GeoPoint(store.latitude(), store.longitude())))
                .toList());
    }
}
