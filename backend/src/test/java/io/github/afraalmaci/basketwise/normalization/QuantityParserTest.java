package io.github.afraalmaci.basketwise.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuantityParserTest {

    private final QuantityParser parser = new QuantityParser();

    @ParameterizedTest(name = "\"{0}\" -> {1} {2}")
    @CsvSource(delimiter = '|', value = {
            // volume
            "1 L          | 1000 | MILLILITER",
            "1,5 L        | 1500 | MILLILITER",
            "1.5l         | 1500 | MILLILITER",
            "0,33 liter   | 330  | MILLILITER",
            "33 cl        | 330  | MILLILITER",
            "500 ml       | 500  | MILLILITER",
            "1 LTR.       | 1000 | MILLILITER",
            // weight
            "500 g        | 500  | GRAM",
            "500gr        | 500  | GRAM",
            "500 gram     | 500  | GRAM",
            "0,145 kg     | 145  | GRAM",
            "1,5 kilo     | 1500 | GRAM",
            // pieces
            "6 st         | 6    | PIECE",
            "6 stuks      | 6    | PIECE",
            "1 stuk       | 1    | PIECE",
            "per stuk     | 1    | PIECE",
            "per 6        | 6    | PIECE",
            // multipacks
            "6 x 330 ml   | 1980 | MILLILITER",
            "6x330ml      | 1980 | MILLILITER",
            "6 x 0,33 l   | 1980 | MILLILITER",
            "6 x 33 cl    | 1980 | MILLILITER",
            "3 X 145 gram | 435  | GRAM",
    })
    void parsesSupportedFormats(String input, long amount, BaseUnit unit) {
        assertThat(parser.parse(input)).isEqualTo(new Quantity(amount, unit));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "veel", "1,5", "500 pond", "0 g", "1,2345 kg", "x 330 ml"})
    void rejectsUnparseableInput(String input) {
        assertThatThrownBy(() -> parser.parse(input)).isInstanceOf(QuantityParseException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(QuantityParseException.class);
    }

    @Test
    void unitPriceMakesDifferentPackSizesComparable() {
        // 1 L for EUR 1.15 vs 6 x 33 cl for EUR 2.49
        long bottle = new Quantity(1000, BaseUnit.MILLILITER).unitPriceCents(115);
        long multipack = parser.parse("6 x 33 cl").unitPriceCents(249);

        assertThat(bottle).isEqualTo(115);
        assertThat(multipack).isEqualTo(126); // 249 / 1.98 L, rounded
        assertThat(new Quantity(6, BaseUnit.PIECE).unitPriceCents(199)).isEqualTo(33);
    }
}
