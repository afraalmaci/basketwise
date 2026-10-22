package io.github.afraalmaci.basketwise.planning;

import io.github.afraalmaci.basketwise.catalog.CatalogDtos.OfferDto;
import io.github.afraalmaci.basketwise.catalog.CatalogDtos.ProductDto;
import io.github.afraalmaci.basketwise.optimization.BasketOptimizer.Purchase;
import io.github.afraalmaci.basketwise.optimization.PackSelector.PackCount;
import io.github.afraalmaci.basketwise.optimization.PackSelector.Selection;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.ChainColumnDto;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.ChainPriceDto;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.ComparisonDto;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.ComparisonRowDto;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.OptimizeResponse;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.PackDto;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.PlanDto;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.PurchaseDto;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.StopDto;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.UnavailableDto;
import io.github.afraalmaci.basketwise.planning.PlanningService.ChainSummary;
import io.github.afraalmaci.basketwise.planning.PlanningService.ListItem;
import io.github.afraalmaci.basketwise.planning.PlanningService.Outcome;
import io.github.afraalmaci.basketwise.planning.PlanningService.RoutedPlan;
import io.github.afraalmaci.basketwise.planning.PlanningService.Stop;
import io.github.afraalmaci.basketwise.routing.RoutingDtos.LegDto;
import io.github.afraalmaci.basketwise.routing.TravelMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a planning {@link Outcome} into the JSON shape of {@code POST /api/optimize}.
 *
 * <p>Plain Java, kept out of the controller so the mapping (the cheapest flags, the chain's
 * own pack labels) is unit tested without starting Spring.
 */
final class OptimizeResponseMapper {

    private OptimizeResponseMapper() {
    }

    static OptimizeResponse toResponse(Outcome outcome, List<ListItem> items, TravelMode mode, double valueOfTime) {
        Map<Long, ProductDto> products = outcome.products();
        return new OptimizeResponse(
                mode.apiName(),
                valueOfTime,
                outcome.estimated(),
                outcome.combinationsChecked(),
                outcome.unavailable().stream().map(product -> new UnavailableDto(product.id(), product.name())).toList(),
                outcome.plans().stream().map(plan -> plan(plan, products)).toList(),
                comparison(outcome.comparison(), items, products));
    }

    private static ComparisonDto comparison(PlanningService.Comparison comparison, List<ListItem> items,
                                            Map<Long, ProductDto> products) {
        List<ChainColumnDto> chains = comparison.chains().stream().map(OptimizeResponseMapper::column).toList();

        List<ComparisonRowDto> rows = new ArrayList<>();
        for (ListItem item : items) {
            ProductDto product = products.get(item.productId());
            long lowest = Long.MAX_VALUE;
            for (ChainSummary chain : comparison.chains()) {
                Selection selection = selection(comparison, chain.chainCode(), item.productId());
                if (selection != null) {
                    lowest = Math.min(lowest, selection.costCents());
                }
            }
            List<ChainPriceDto> prices = new ArrayList<>();
            for (ChainSummary chain : comparison.chains()) {
                Selection selection = selection(comparison, chain.chainCode(), item.productId());
                if (selection != null) {
                    prices.add(new ChainPriceDto(chain.chainCode(), selection.costCents(), selection.boughtAmount(),
                            selection.costCents() == lowest, packs(selection, chain.chainCode(), product)));
                }
            }
            rows.add(new ComparisonRowDto(product.id(), product.name(), product.unit(), item.quantity(), prices));
        }
        return new ComparisonDto(chains, rows);
    }

    private static Selection selection(PlanningService.Comparison comparison, String chainCode, long productId) {
        return comparison.cheapest().getOrDefault(chainCode, Map.of()).get(productId);
    }

    private static ChainColumnDto column(ChainSummary chain) {
        return new ChainColumnDto(
                chain.chainCode(),
                chain.nearestStore() == null ? null : chain.nearestStore().id(),
                chain.nearestStore() == null ? null : chain.nearestStore().name(),
                chain.nearestSeconds(),
                chain.totalCents(),
                chain.itemsCovered());
    }

    private static PlanDto plan(RoutedPlan routed, Map<Long, ProductDto> products) {
        return new PlanDto(
                routed.plan().storeCount(),
                routed.plan().basketCents(),
                routed.plan().trip().durationSeconds(),
                routed.plan().trip().distanceMeters(),
                routed.plan().timeCostCents(),
                routed.plan().scoreCents(),
                routed.stops().stream().map(stop -> stop(stop, products)).toList(),
                routed.route().legs().stream().map(leg -> new LegDto(leg.durationSeconds(), leg.distanceMeters())).toList(),
                routed.estimated(),
                routed.route().geometry().stream().map(point -> List.of(point.latitude(), point.longitude())).toList(),
                routed.plan().roles().stream().map(role -> role.name().toLowerCase(Locale.ROOT)).toList());
    }

    private static StopDto stop(Stop stop, Map<Long, ProductDto> products) {
        return new StopDto(
                stop.store().id(),
                stop.store().chainCode(),
                stop.store().name(),
                stop.store().location().latitude(),
                stop.store().location().longitude(),
                stop.subtotalCents(),
                stop.purchases().stream().map(purchase -> purchase(purchase, products.get(purchase.productId()))).toList());
    }

    private static PurchaseDto purchase(Purchase purchase, ProductDto product) {
        return new PurchaseDto(
                product.id(),
                product.name(),
                product.unit(),
                purchase.neededAmount(),
                purchase.selection().boughtAmount(),
                purchase.selection().costCents(),
                packs(purchase.selection(), purchase.chainCode(), product));
    }

    private static List<PackDto> packs(Selection selection, String chainCode, ProductDto product) {
        return selection.packs().stream().map(count -> pack(count, chainCode, product)).toList();
    }

    /** The chain's own label for the pack: (chain, product, size) identifies one offer. */
    private static PackDto pack(PackCount count, String chainCode, ProductDto product) {
        OfferDto offer = product.offers().stream()
                .filter(o -> o.chainCode().equals(chainCode) && o.quantityAmount() == count.pack().amount())
                .findFirst()
                .orElseThrow();
        return new PackDto(offer.displayName(), offer.quantityText(), offer.quantityAmount(), offer.priceCents(),
                count.count());
    }
}
