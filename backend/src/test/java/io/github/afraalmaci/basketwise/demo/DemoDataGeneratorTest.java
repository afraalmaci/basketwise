package io.github.afraalmaci.basketwise.demo;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.afraalmaci.basketwise.normalization.QuantityParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DemoDataGeneratorTest {

    private static List<CatalogEntry> catalog;
    private static List<DemoOffer> offers;

    @BeforeAll
    static void generate() {
        catalog = DemoCsv.readCatalog(new InputStreamReader(
                DemoDataGeneratorTest.class.getResourceAsStream("/demo/catalog.csv"), StandardCharsets.UTF_8));
        offers = new DemoDataGenerator(42).generate(ChainProfile.demoChains(), catalog);
    }

    @Test
    void sameSeedProducesSameDataset() {
        assertThat(new DemoDataGenerator(42).generate(ChainProfile.demoChains(), catalog)).isEqualTo(offers);
    }

    /**
     * The key property: whatever label style a chain uses, the parser recovers the
     * exact quantity the generator started from.
     */
    @Test
    void parserRecoversTrueQuantityForEveryLabel() {
        QuantityParser parser = new QuantityParser();

        assertThat(offers).allSatisfy(offer ->
                assertThat(parser.parse(offer.quantityText()))
                        .as("%s / %s", offer.chainCode(), offer.quantityText())
                        .isEqualTo(offer.trueQuantity()));
    }

    @Test
    void everyProductIsSoldSomewhere() {
        Set<String> offered = offers.stream().map(DemoOffer::productKey).collect(Collectors.toSet());

        assertThat(catalog).allSatisfy(entry -> assertThat(offered).contains(entry.key()));
    }

    @Test
    void budgetChainIsCheaperButIncomplete() {
        Map<String, Long> productCount = offers.stream()
                .collect(Collectors.groupingBy(DemoOffer::chainCode,
                        Collectors.mapping(DemoOffer::productKey, Collectors.collectingAndThen(Collectors.toSet(), s -> (long) s.size()))));

        assertThat(productCount.get("HOEKWINKEL")).isEqualTo((long) catalog.size());
        assertThat(productCount.get("BUDGETBAAS")).isLessThan(catalog.size() * 85L / 100);

        // Compare identical packs only, so a different assortment cannot skew the result.
        Map<String, Long> hoekwinkel = pricesByPack("HOEKWINKEL");
        Map<String, Long> budgetbaas = pricesByPack("BUDGETBAAS");
        long cheaper = budgetbaas.entrySet().stream()
                .filter(entry -> entry.getValue() < hoekwinkel.get(entry.getKey()))
                .count();
        assertThat(cheaper).isEqualTo(budgetbaas.size());
    }

    @Test
    void shelfPricesEndInNine() {
        assertThat(offers).allSatisfy(offer -> assertThat(offer.priceCents() % 10).isEqualTo(9));
        assertThat(DemoDataGenerator.roundToShelfPrice(123)).isEqualTo(119);
        assertThat(DemoDataGenerator.roundToShelfPrice(126)).isEqualTo(129);
    }

    private Map<String, Long> pricesByPack(String chainCode) {
        return offers.stream()
                .filter(offer -> offer.chainCode().equals(chainCode))
                .collect(Collectors.toMap(
                        offer -> offer.productKey() + "/" + offer.trueQuantity(),
                        DemoOffer::priceCents));
    }
}
