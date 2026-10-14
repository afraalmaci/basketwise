package io.github.afraalmaci.basketwise.normalization;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the free-text pack sizes that supermarkets print on products into
 * a comparable {@link Quantity}.
 *
 * <p>Supported shapes (case-insensitive, comma or dot as decimal separator):
 * <ul>
 *   <li>simple: {@code 500 g}, {@code 1,5 L}, {@code 0,145 kg}, {@code 33cl}, {@code 6 stuks}</li>
 *   <li>multipack: {@code 6 x 330 ml}, {@code 4x0,25l}, {@code 3 x 145 gram}</li>
 *   <li>count phrases: {@code per stuk}, {@code per 6}</li>
 * </ul>
 *
 * <p>Arithmetic uses {@link BigDecimal}, so {@code 0,145 kg} becomes exactly 145 g
 * instead of 144.99999 g. Anything that does not resolve to a whole number of
 * base units is rejected rather than silently rounded.
 */
public final class QuantityParser {

    private static final Pattern MULTIPACK = Pattern.compile("^(\\d+)\\s*x\\s*(.+)$");
    private static final Pattern PER_COUNT = Pattern.compile("^per\\s+(\\d+)$");
    private static final Pattern SIMPLE = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*([a-z]+)\\.?$");

    private record UnitFactor(BaseUnit unit, BigDecimal factor) {
    }

    private static final Map<String, UnitFactor> UNITS = Map.ofEntries(
            Map.entry("ml", new UnitFactor(BaseUnit.MILLILITER, BigDecimal.ONE)),
            Map.entry("cl", new UnitFactor(BaseUnit.MILLILITER, BigDecimal.TEN)),
            Map.entry("dl", new UnitFactor(BaseUnit.MILLILITER, BigDecimal.valueOf(100))),
            Map.entry("l", new UnitFactor(BaseUnit.MILLILITER, BigDecimal.valueOf(1000))),
            Map.entry("ltr", new UnitFactor(BaseUnit.MILLILITER, BigDecimal.valueOf(1000))),
            Map.entry("liter", new UnitFactor(BaseUnit.MILLILITER, BigDecimal.valueOf(1000))),
            Map.entry("g", new UnitFactor(BaseUnit.GRAM, BigDecimal.ONE)),
            Map.entry("gr", new UnitFactor(BaseUnit.GRAM, BigDecimal.ONE)),
            Map.entry("gram", new UnitFactor(BaseUnit.GRAM, BigDecimal.ONE)),
            Map.entry("kg", new UnitFactor(BaseUnit.GRAM, BigDecimal.valueOf(1000))),
            Map.entry("kilo", new UnitFactor(BaseUnit.GRAM, BigDecimal.valueOf(1000))),
            Map.entry("st", new UnitFactor(BaseUnit.PIECE, BigDecimal.ONE)),
            Map.entry("stuk", new UnitFactor(BaseUnit.PIECE, BigDecimal.ONE)),
            Map.entry("stuks", new UnitFactor(BaseUnit.PIECE, BigDecimal.ONE)));

    public Quantity parse(String input) {
        if (input == null || input.isBlank()) {
            throw new QuantityParseException(String.valueOf(input), "empty");
        }
        String text = input.trim().toLowerCase(Locale.ROOT).replace(',', '.').replaceAll("\\s+", " ");

        if (text.equals("per stuk")) {
            return new Quantity(1, BaseUnit.PIECE);
        }
        Matcher perCount = PER_COUNT.matcher(text);
        if (perCount.matches()) {
            return new Quantity(parseCount(input, perCount.group(1)), BaseUnit.PIECE);
        }

        Matcher multipack = MULTIPACK.matcher(text);
        if (multipack.matches()) {
            long packs = parseCount(input, multipack.group(1));
            Quantity single = parseSimple(input, multipack.group(2));
            return new Quantity(Math.multiplyExact(packs, single.amount()), single.unit());
        }

        return parseSimple(input, text);
    }

    private Quantity parseSimple(String original, String text) {
        Matcher matcher = SIMPLE.matcher(text.trim());
        if (!matcher.matches()) {
            throw new QuantityParseException(original, "unrecognized format");
        }
        UnitFactor unitFactor = UNITS.get(matcher.group(2));
        if (unitFactor == null) {
            throw new QuantityParseException(original, "unknown unit '" + matcher.group(2) + "'");
        }
        BigDecimal amount = new BigDecimal(matcher.group(1)).multiply(unitFactor.factor());
        try {
            return new Quantity(amount.longValueExact(), unitFactor.unit());
        } catch (ArithmeticException e) {
            throw new QuantityParseException(original, "not a whole number of " + unitFactor.unit().code());
        } catch (IllegalArgumentException e) {
            throw new QuantityParseException(original, e.getMessage());
        }
    }

    private long parseCount(String original, String digits) {
        long count = Long.parseLong(digits);
        if (count <= 0) {
            throw new QuantityParseException(original, "count must be positive");
        }
        return count;
    }
}
