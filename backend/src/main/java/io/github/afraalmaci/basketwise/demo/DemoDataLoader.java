package io.github.afraalmaci.basketwise.demo;

import io.github.afraalmaci.basketwise.normalization.Quantity;
import io.github.afraalmaci.basketwise.normalization.QuantityParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills an empty database with the generated demo dataset on startup.
 *
 * <p>Offers go through the same {@link QuantityParser} that real data would, and the
 * result is checked against the generator's ground truth. If the parser ever
 * disagrees, startup fails loudly instead of serving wrong unit prices.
 */
@Component
@ConditionalOnProperty(name = "basketwise.data-mode", havingValue = "demo")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    static final long SEED = 42;

    private final JdbcTemplate jdbc;
    private final QuantityParser parser = new QuantityParser();

    public DemoDataLoader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long chains = jdbc.queryForObject("SELECT count(*) FROM chain", Long.class);
        if (chains != null && chains > 0) {
            log.info("Demo data already present, skipping load");
            return;
        }

        List<ChainProfile> profiles = ChainProfile.demoChains();
        List<CatalogEntry> catalog = DemoCsv.readCatalog(resource("demo/catalog.csv"));
        List<DemoStore> stores = DemoCsv.readStores(resource("demo/stores.csv"));
        List<DemoOffer> offers = new DemoDataGenerator(SEED).generate(profiles, catalog);

        Map<String, Long> chainIds = new HashMap<>();
        for (ChainProfile chain : profiles) {
            chainIds.put(chain.code(), jdbc.queryForObject(
                    "INSERT INTO chain (code, name, color) VALUES (?, ?, ?) RETURNING id",
                    Long.class, chain.code(), chain.name(), chain.color()));
        }

        jdbc.batchUpdate(
                "INSERT INTO store (chain_id, name, latitude, longitude) VALUES (?, ?, ?, ?)",
                stores.stream()
                        .map(s -> new Object[] {requireId(chainIds, s.chainCode()), s.name(), s.latitude(), s.longitude()})
                        .toList());

        Map<String, Long> productIds = new HashMap<>();
        for (CatalogEntry product : catalog) {
            productIds.put(product.key(), jdbc.queryForObject(
                    "INSERT INTO product (slug, name, category, unit) VALUES (?, ?, ?, ?) RETURNING id",
                    Long.class, product.key(), product.name(), product.category(), product.unit().code()));
        }

        jdbc.batchUpdate(
                "INSERT INTO offer (chain_id, product_id, display_name, quantity_text, quantity_amount, price_cents) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                offers.stream()
                        .map(o -> new Object[] {
                                requireId(chainIds, o.chainCode()),
                                requireId(productIds, o.productKey()),
                                o.displayName(),
                                o.quantityText(),
                                parseAndVerify(o).amount(),
                                o.priceCents()})
                        .toList());

        log.info("Loaded demo data: {} chains, {} stores, {} products, {} offers",
                profiles.size(), stores.size(), catalog.size(), offers.size());
    }

    private Quantity parseAndVerify(DemoOffer offer) {
        Quantity parsed = parser.parse(offer.quantityText());
        if (!parsed.equals(offer.trueQuantity())) {
            throw new IllegalStateException("Quantity parser mismatch for " + offer
                    + ": parsed " + parsed);
        }
        return parsed;
    }

    private static long requireId(Map<String, Long> ids, String key) {
        Long id = ids.get(key);
        if (id == null) {
            throw new IllegalStateException("Unknown reference in demo data: " + key);
        }
        return id;
    }

    private static Reader resource(String path) {
        try {
            return new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Missing demo resource " + path, e);
        }
    }
}
