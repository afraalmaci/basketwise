package io.github.afraalmaci.basketwise.demo;

import io.github.afraalmaci.basketwise.normalization.Quantity;

/**
 * One product as sold by one chain.
 *
 * @param displayName  the name as printed on the chain's label (messy on purpose)
 * @param quantityText the pack size as printed on the label (messy on purpose)
 * @param trueQuantity the ground truth, known only because the data is generated;
 *                     used to verify the quantity parser, never shown to users
 */
public record DemoOffer(
        String chainCode,
        String productKey,
        String displayName,
        String quantityText,
        Quantity trueQuantity,
        long priceCents) {
}
