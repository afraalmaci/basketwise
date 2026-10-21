package io.github.afraalmaci.basketwise.planning;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.afraalmaci.basketwise.catalog.CatalogDtos.OfferDto;
import io.github.afraalmaci.basketwise.catalog.CatalogDtos.ProductDto;
import io.github.afraalmaci.basketwise.optimization.ScoringPolicy;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.ChainPriceDto;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.ComparisonRowDto;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.OptimizeResponse;
import io.github.afraalmaci.basketwise.planning.PlanningService.ListItem;
import io.github.afraalmaci.basketwise.planning.PlanningService.Request;
import io.github.afraalmaci.basketwise.routing.GeoPoint;
import io.github.afraalmaci.basketwise.routing.ServiceArea;
import io.github.afraalmaci.basketwise.routing.StoreLocation;
import io.github.afraalmaci.basketwise.routing.StraightLineRoutingClient;
import io.github.afraalmaci.basketwise.routing.TravelMode;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class OptimizeResponseMapperTest {

    private static final GeoPoint HOME = new GeoPoint(52.07, 4.30);
    private static final ServiceArea AREA = new ServiceArea("Test", 52.0, 4.2, 52.2, 4.4, HOME);

    private static final ProductDto MILK = new ProductDto(10, "melk", "Halfvolle melk", "dairy", "ml", List.of(
            new OfferDto("A", "Melk", "1 L", 1000, 100, 100),
            new OfferDto("B", "MELK", "1 liter", 1000, 100, 100),
            new OfferDto("C", "melk groot", "1,5l", 1500, 120, 80)));
    private static final ProductDto TEA = new ProductDto(11, "thee", "Thee", "drinks", "pc", List.of(
            new OfferDto("A", "Thee", "20 st", 20, 199, 10)));
    private static final ProductDto SAFFRON = new ProductDto(12, "saffraan", "Saffraan", "spices", "g", List.of());

    private final PlanningService service = new PlanningService(new StraightLineRoutingClient(), AREA,
            () -> List.of(store(1, "A", 0.005), store(2, "B", 0.010), store(3, "C", 0.020)),
            OptimizeResponseMapperTest::products);

    @Test
    void comparisonFollowsTheListAndMarksEveryCheapestPrice() {
        List<ListItem> items = List.of(new ListItem(11, 20), new ListItem(10, 1000), new ListItem(12, 5));

        OptimizeResponse response = map(items);

        List<ComparisonRowDto> rows = response.comparison().items();
        assertThat(rows).extracting(ComparisonRowDto::name).containsExactly("Thee", "Halfvolle melk", "Saffraan");

        // 1 L of milk: A and B tie at 1.00, C's only cheaper per liter, not for 1 L.
        ComparisonRowDto milk = rows.get(1);
        assertThat(milk.neededAmount()).isEqualTo(1000L);
        assertThat(milk.prices()).extracting(ChainPriceDto::chainCode).containsExactly("A", "B", "C");
        assertThat(milk.prices()).extracting(ChainPriceDto::cheapest).containsExactly(true, true, false);
        ChainPriceDto atC = milk.prices().get(2);
        assertThat(atC.boughtAmount()).isEqualTo(1500L);
        assertThat(atC.packs().get(0).quantityText()).isEqualTo("1,5l"); // the chain's own label

        // Sold nowhere: a row without prices.
        assertThat(rows.get(2).prices()).isEmpty();
    }

    @Test
    void chainColumnsSumWhatEachChainSells() {
        OptimizeResponse response = map(List.of(new ListItem(10, 1000), new ListItem(11, 20)));

        var chains = response.comparison().chains();
        assertThat(chains).extracting(PlanningDtos.ChainColumnDto::chainCode).containsExactly("A", "B", "C");
        assertThat(chains.get(0).totalCents()).isEqualTo(299L);
        assertThat(chains.get(0).itemsCovered()).isEqualTo(2);
        assertThat(chains.get(1).itemsCovered()).isEqualTo(1);
        assertThat(chains.get(0).nearestStoreName()).isEqualTo("A 1");
        assertThat(chains.get(0).nearestSeconds()).isLessThan(chains.get(2).nearestSeconds());
    }

    private OptimizeResponse map(List<ListItem> items) {
        var outcome = service.plan(new Request(HOME, TravelMode.BIKE, items, 3, ScoringPolicy.eurosPerHour(10)));
        return OptimizeResponseMapper.toResponse(outcome, items, TravelMode.BIKE, 10);
    }

    private static List<ProductDto> products(Collection<Long> ids) {
        return List.of(MILK, TEA, SAFFRON).stream().filter(product -> ids.contains(product.id())).toList();
    }

    private static StoreLocation store(long id, String chain, double eastDegrees) {
        return new StoreLocation(id, chain, chain + " " + id, new GeoPoint(HOME.latitude(), HOME.longitude() + eastDegrees));
    }
}
