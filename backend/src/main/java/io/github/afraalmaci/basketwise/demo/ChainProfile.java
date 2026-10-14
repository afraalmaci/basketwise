package io.github.afraalmaci.basketwise.demo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A fictional supermarket chain and the "personality" of its prices.
 *
 * @param priceMultiplier    overall price level relative to the catalog reference price
 * @param availability       share of the catalog the chain carries (1.0 = everything)
 * @param categoryMultipliers per-category adjustments on top of the overall level
 * @param fullCategories     categories the chain always carries completely
 */
public record ChainProfile(
        String code,
        String name,
        String color,
        LabelStyle labelStyle,
        double priceMultiplier,
        double availability,
        Map<String, Double> categoryMultipliers,
        Set<String> fullCategories) {

    public double multiplierFor(String category) {
        return priceMultiplier * categoryMultipliers.getOrDefault(category, 1.0);
    }

    /**
     * The four demo chains. Their trade-offs are what make the optimizer interesting:
     * the cheapest chain is far away and incomplete, the closest one is expensive.
     */
    public static List<ChainProfile> demoChains() {
        return List.of(
                new ChainProfile("BUDGETBAAS", "Budgetbaas", "#d97706", LabelStyle.BUDGETBAAS,
                        0.82, 0.72, Map.of(), Set.of()),
                new ChainProfile("HOEKWINKEL", "Hoekwinkel", "#2563eb", LabelStyle.HOEKWINKEL,
                        1.12, 1.0, Map.of(), Set.of()),
                new ChainProfile("VERSPLEIN", "Versplein", "#16a34a", LabelStyle.VERSPLEIN,
                        1.00, 0.85,
                        Map.of("vegetables", 0.85, "fruit", 0.85, "household", 1.10, "personal_care", 1.10),
                        Set.of("vegetables", "fruit")),
                new ChainProfile("MARKTWIJS", "Marktwijs", "#9333ea", LabelStyle.MARKTWIJS,
                        0.95, 0.90,
                        Map.of("drinks", 0.88, "snacks", 0.90),
                        Set.of("drinks")));
    }
}
