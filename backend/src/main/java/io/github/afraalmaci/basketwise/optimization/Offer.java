package io.github.afraalmaci.basketwise.optimization;

/** A pack of a product sold by a chain. Prices are per chain, the same in all its stores. */
public record Offer(String chainCode, long productId, Pack pack) {

    public Offer {
        if (chainCode == null || chainCode.isBlank()) {
            throw new IllegalArgumentException("Chain code is required");
        }
        if (pack == null) {
            throw new IllegalArgumentException("Pack is required");
        }
    }
}
