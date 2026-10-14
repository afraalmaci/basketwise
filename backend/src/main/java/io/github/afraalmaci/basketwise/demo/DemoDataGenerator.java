package io.github.afraalmaci.basketwise.demo;

import io.github.afraalmaci.basketwise.demo.CatalogEntry.PackSize;
import io.github.afraalmaci.basketwise.normalization.Quantity;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates realistic but fictional offers for every (chain, product) pair.
 *
 * <p>Deterministic: the same seed always produces the same dataset, so prices in
 * tests and screenshots never drift. Each (chain, product) pair gets its own
 * Random derived from the seed, so adding a product does not reshuffle all others.
 */
public final class DemoDataGenerator {

    /** Larger packs are cheaper per unit: each step up in size takes this factor off. */
    private static final double BULK_DISCOUNT_PER_STEP = 0.92;
    private static final double NOISE = 0.07;
    private static final long MIN_PRICE_CENTS = 19;

    private final long seed;

    public DemoDataGenerator(long seed) {
        this.seed = seed;
    }

    public List<DemoOffer> generate(List<ChainProfile> chains, List<CatalogEntry> catalog) {
        List<DemoOffer> offers = new ArrayList<>();
        for (ChainProfile chain : chains) {
            for (CatalogEntry product : catalog) {
                Random random = new Random(seed * 31 + (chain.code() + ":" + product.key()).hashCode());
                if (!carries(chain, product, random)) {
                    continue;
                }
                for (int index : chosenSizeIndexes(chain, product, random)) {
                    offers.add(offer(chain, product, index, random));
                }
            }
        }
        return offers;
    }

    private boolean carries(ChainProfile chain, CatalogEntry product, Random random) {
        return chain.fullCategories().contains(product.category())
                || random.nextDouble() < chain.availability();
    }

    /** A full-assortment chain sells every size; others sell a random non-empty subset. */
    private List<Integer> chosenSizeIndexes(ChainProfile chain, CatalogEntry product, Random random) {
        int count = product.sizes().size();
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (chain.availability() >= 1.0 || random.nextDouble() < 0.6) {
                indexes.add(i);
            }
        }
        if (indexes.isEmpty()) {
            indexes.add(random.nextInt(count));
        }
        return indexes;
    }

    private DemoOffer offer(ChainProfile chain, CatalogEntry product, int sizeIndex, Random random) {
        PackSize size = product.sizes().get(sizeIndex);
        Quantity quantity = new Quantity(size.total(), product.unit());

        double base = (double) product.referenceCents() * size.total() / product.unit().referenceSize();
        double bulk = Math.pow(BULK_DISCOUNT_PER_STEP, sizeIndex);
        double noise = 1 + (random.nextDouble() * 2 - 1) * NOISE;
        long priceCents = roundToShelfPrice(base * chain.multiplierFor(product.category()) * bulk * noise);

        return new DemoOffer(
                chain.code(),
                product.key(),
                chain.labelStyle().displayName(product.name()),
                chain.labelStyle().quantity(product.unit(), size),
                quantity,
                priceCents);
    }

    /** Shelf prices end in 9: 1.23 becomes 1.19, 1.26 becomes 1.29. */
    static long roundToShelfPrice(double cents) {
        long rounded = Math.round(cents / 10.0) * 10 - 1;
        return Math.max(MIN_PRICE_CENTS, rounded);
    }
}
