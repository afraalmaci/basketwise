package io.github.afraalmaci.basketwise.normalization;

/**
 * A normalized quantity: a positive whole amount of a base unit.
 */
public record Quantity(long amount, BaseUnit unit) {

    public Quantity {
        if (amount <= 0) {
            throw new IllegalArgumentException("Quantity must be positive, got " + amount);
        }
        if (unit == null) {
            throw new IllegalArgumentException("Unit is required");
        }
    }

    /**
     * Price per liter, kilogram or piece, in cents, rounded half up.
     * Used to compare a 1 L bottle with a 6 x 33 cl multipack.
     */
    public long unitPriceCents(long packPriceCents) {
        long numerator = packPriceCents * unit.referenceSize();
        return (numerator + amount / 2) / amount;
    }
}
