package io.github.afraalmaci.basketwise.demo;

import io.github.afraalmaci.basketwise.demo.CatalogEntry.PackSize;
import io.github.afraalmaci.basketwise.normalization.BaseUnit;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * How a chain writes product names and pack sizes on its labels.
 * Each chain has its own habits, just like real supermarkets: one writes "1,5 L",
 * another "1500 ml", a third "6 x 33 cl". The quantity parser has to make sense of all of them.
 */
public enum LabelStyle {

    /** House-brand prefix, compact units: "BB Halfvolle melk", "1,5 L", "6x330ml", "6 st". */
    BUDGETBAAS {
        @Override
        String displayName(String name) {
            return "BB " + name;
        }

        @Override
        String volume(PackSize size) {
            if (size.isMultipack()) {
                return size.packs() + "x" + size.perPack() + "ml";
            }
            long ml = size.total();
            return ml >= 1000 ? decimal(ml, 1000) + " L" : ml + " ml";
        }

        @Override
        String weight(PackSize size) {
            if (size.isMultipack()) {
                return size.packs() + "x" + size.perPack() + "g";
            }
            long g = size.total();
            return g >= 1000 ? decimal(g, 1000) + " kg" : g + " g";
        }

        @Override
        String pieces(long count) {
            return count + " st";
        }
    },

    /** Title case, always the small unit for volume, always kg for weight. */
    HOEKWINKEL {
        @Override
        String displayName(String name) {
            return Arrays.stream(name.split(" "))
                    .map(word -> word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1))
                    .collect(Collectors.joining(" "));
        }

        @Override
        String volume(PackSize size) {
            return size.isMultipack()
                    ? size.packs() + " x " + size.perPack() + " ml"
                    : size.total() + " ml";
        }

        @Override
        String weight(PackSize size) {
            return size.isMultipack()
                    ? size.packs() + " x " + size.perPack() + " g"
                    : decimal(size.total(), 1000) + " kg";
        }

        @Override
        String pieces(long count) {
            return count == 1 ? "1 stuk" : count + " stuks";
        }
    },

    /** Lower case, liters with no space, "gr" for grams, "per 6" for counts. */
    VERSPLEIN {
        @Override
        String displayName(String name) {
            return name.toLowerCase(Locale.ROOT);
        }

        @Override
        String volume(PackSize size) {
            return size.isMultipack()
                    ? size.packs() + " x " + decimal(size.perPack(), 1000) + " l"
                    : decimal(size.total(), 1000) + "l";
        }

        @Override
        String weight(PackSize size) {
            return size.isMultipack()
                    ? size.packs() + "x" + size.perPack() + "gr"
                    : size.total() + "gr";
        }

        @Override
        String pieces(long count) {
            return count == 1 ? "per stuk" : "per " + count;
        }
    },

    /** Upper case names, centiliters and spelled-out units: "6 x 33 cl", "500 gram", "1,5 kilo". */
    MARKTWIJS {
        @Override
        String displayName(String name) {
            return name.toUpperCase(Locale.ROOT);
        }

        @Override
        String volume(PackSize size) {
            if (size.isMultipack()) {
                return size.packs() + " x " + smallVolume(size.perPack());
            }
            long ml = size.total();
            return ml >= 1000 ? decimal(ml, 1000) + " liter" : smallVolume(ml);
        }

        private String smallVolume(long ml) {
            return ml % 10 == 0 ? (ml / 10) + " cl" : ml + " ml";
        }

        @Override
        String weight(PackSize size) {
            if (size.isMultipack()) {
                return size.packs() + " x " + size.perPack() + " gram";
            }
            long g = size.total();
            return g >= 1000 ? decimal(g, 1000) + " kilo" : g + " gram";
        }

        @Override
        String pieces(long count) {
            return count == 1 ? "1 stuk" : count + " stuks";
        }
    };

    abstract String displayName(String name);

    abstract String volume(PackSize size);

    abstract String weight(PackSize size);

    abstract String pieces(long count);

    public String quantity(BaseUnit unit, PackSize size) {
        return switch (unit) {
            case MILLILITER -> volume(size);
            case GRAM -> weight(size);
            case PIECE -> pieces(size.total());
        };
    }

    /** 1500 / 1000 -> "1,5" (Dutch decimal comma, no trailing zeros). */
    static String decimal(long value, long divisor) {
        return BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(divisor))
                .stripTrailingZeros()
                .toPlainString()
                .replace('.', ',');
    }
}
