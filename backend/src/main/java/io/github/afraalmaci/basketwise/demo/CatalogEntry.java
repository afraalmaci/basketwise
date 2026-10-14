package io.github.afraalmaci.basketwise.demo;

import io.github.afraalmaci.basketwise.normalization.BaseUnit;
import java.util.List;

/**
 * One canonical product from {@code demo/catalog.csv}.
 *
 * @param referenceCents typical price per liter / kilogram, or per piece for {@link BaseUnit#PIECE}
 */
public record CatalogEntry(
        String key,
        String name,
        String category,
        BaseUnit unit,
        List<PackSize> sizes,
        long referenceCents) {

    /**
     * A pack size. {@code packs > 1} means a multipack, e.g. 6 x 330 ml.
     */
    public record PackSize(int packs, long perPack) {

        public long total() {
            return packs * perPack;
        }

        public boolean isMultipack() {
            return packs > 1;
        }

        static PackSize parse(String token) {
            String[] parts = token.trim().toLowerCase(java.util.Locale.ROOT).split("x");
            if (parts.length == 1) {
                return new PackSize(1, Long.parseLong(parts[0]));
            }
            return new PackSize(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
        }
    }
}
