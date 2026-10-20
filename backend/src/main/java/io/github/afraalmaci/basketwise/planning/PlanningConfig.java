package io.github.afraalmaci.basketwise.planning;

import io.github.afraalmaci.basketwise.catalog.CatalogRepository;
import io.github.afraalmaci.basketwise.routing.GeoPoint;
import io.github.afraalmaci.basketwise.routing.RoutingClient;
import io.github.afraalmaci.basketwise.routing.ServiceArea;
import io.github.afraalmaci.basketwise.routing.StoreLocation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the plain-Java {@link PlanningService} to the catalog and the routing client. */
@Configuration
class PlanningConfig {

    @Bean
    PlanningService planningService(RoutingClient routingClient, ServiceArea serviceArea, CatalogRepository catalog) {
        return new PlanningService(
                routingClient,
                serviceArea,
                () -> catalog.findStores().stream()
                        .map(store -> new StoreLocation(store.id(), store.chainCode(), store.name(),
                                new GeoPoint(store.latitude(), store.longitude())))
                        .toList(),
                catalog::findProducts);
    }
}
