package io.github.afraalmaci.basketwise.catalog;

import io.github.afraalmaci.basketwise.catalog.CatalogDtos.ChainDto;
import io.github.afraalmaci.basketwise.catalog.CatalogDtos.OfferDto;
import io.github.afraalmaci.basketwise.catalog.CatalogDtos.ProductDto;
import io.github.afraalmaci.basketwise.catalog.CatalogDtos.StoreDto;
import io.github.afraalmaci.basketwise.normalization.BaseUnit;
import io.github.afraalmaci.basketwise.normalization.Quantity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {

    /**
     * Minimum word_similarity for a fuzzy hit. Measured on the demo catalog: real typos
     * score 0.5 to 0.8 ("spagetti" 0.58, "pindakas" 0.78), while noise such as
     * "kaas" matching "Kabeljauwfilet" sits at 0.40.
     */
    static final double FUZZY_THRESHOLD = 0.45;

    private final NamedParameterJdbcTemplate jdbc;

    public CatalogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ChainDto> findChains() {
        return jdbc.query("""
                SELECT c.code, c.name, c.color, count(s.id) AS store_count
                FROM chain c
                LEFT JOIN store s ON s.chain_id = c.id
                GROUP BY c.id
                ORDER BY c.name
                """,
                (rs, i) -> new ChainDto(rs.getString("code"), rs.getString("name"),
                        rs.getString("color"), rs.getInt("store_count")));
    }

    public List<StoreDto> findStores() {
        return jdbc.query("""
                SELECT s.id, c.code AS chain_code, c.name AS chain_name, s.name, s.latitude, s.longitude
                FROM store s
                JOIN chain c ON c.id = s.chain_id
                ORDER BY s.name
                """,
                (rs, i) -> new StoreDto(rs.getLong("id"), rs.getString("chain_code"), rs.getString("chain_name"),
                        rs.getString("name"), rs.getDouble("latitude"), rs.getDouble("longitude")));
    }

    /**
     * Fuzzy product search with pg_trgm.
     *
     * <p>Ranking: names that start with the query come first, then names that contain it,
     * then fuzzy hits that survive typos such as "halvolle melk". {@code word_similarity}
     * compares the query with the best-matching part of the name, which suits short
     * queries better than plain {@code similarity}.
     */
    public List<ProductDto> searchProducts(String query, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("q", query)
                .addValue("pattern", escapeLike(query))
                .addValue("threshold", FUZZY_THRESHOLD)
                .addValue("limit", limit);

        record Row(long id, String slug, String name, String category, String unit) {
        }
        List<Row> rows = jdbc.query("""
                SELECT id, slug, name, category, unit
                FROM product
                WHERE name ILIKE '%' || :pattern || '%'
                   OR word_similarity(:q, name) >= :threshold
                ORDER BY (name ILIKE :pattern || '%') DESC,
                         (name ILIKE '%' || :pattern || '%') DESC,
                         word_similarity(:q, name) DESC,
                         name
                LIMIT :limit
                """,
                params,
                (rs, i) -> new Row(rs.getLong("id"), rs.getString("slug"), rs.getString("name"),
                        rs.getString("category"), rs.getString("unit")));

        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, List<OfferDto>> offersByProduct = new LinkedHashMap<>();
        Map<Long, BaseUnit> units = new LinkedHashMap<>();
        for (Row row : rows) {
            offersByProduct.put(row.id(), new ArrayList<>());
            units.put(row.id(), BaseUnit.fromCode(row.unit()));
        }

        jdbc.query("""
                SELECT o.product_id, c.code AS chain_code, o.display_name, o.quantity_text,
                       o.quantity_amount, o.price_cents
                FROM offer o
                JOIN chain c ON c.id = o.chain_id
                WHERE o.product_id IN (:ids)
                """,
                new MapSqlParameterSource("ids", offersByProduct.keySet()),
                rs -> {
                    long productId = rs.getLong("product_id");
                    Quantity quantity = new Quantity(rs.getLong("quantity_amount"), units.get(productId));
                    long price = rs.getLong("price_cents");
                    offersByProduct.get(productId).add(new OfferDto(
                            rs.getString("chain_code"),
                            rs.getString("display_name"),
                            rs.getString("quantity_text"),
                            quantity.amount(),
                            price,
                            quantity.unitPriceCents(price)));
                });

        return rows.stream()
                .map(row -> new ProductDto(row.id(), row.slug(), row.name(), row.category(), row.unit(),
                        offersByProduct.get(row.id()).stream()
                                .sorted((a, b) -> Long.compare(a.unitPriceCents(), b.unitPriceCents()))
                                .toList()))
                .toList();
    }

    /** Users typing "%" or "_" should search for those characters, not wildcards. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
