package io.github.afraalmaci.basketwise.normalization;

public class QuantityParseException extends RuntimeException {

    public QuantityParseException(String input, String reason) {
        super("Cannot parse quantity '" + input + "': " + reason);
    }
}
