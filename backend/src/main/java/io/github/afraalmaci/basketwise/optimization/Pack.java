package io.github.afraalmaci.basketwise.optimization;

/**
 * One pack size a chain sells, e.g. a 1.5 L bottle for 1.29.
 *
 * @param amount     size in base units (ml, g or pieces), as normalized by the quantity parser
 * @param priceCents shelf price of one pack
 */
public record Pack(long amount, long priceCents) {

    public Pack {
        if (amount <= 0) {
            throw new IllegalArgumentException("Pack amount must be positive, got " + amount);
        }
        if (priceCents <= 0) {
            throw new IllegalArgumentException("Pack price must be positive, got " + priceCents);
        }
    }
}
