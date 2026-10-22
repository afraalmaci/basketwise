package io.github.afraalmaci.basketwise.planning;

import io.github.afraalmaci.basketwise.optimization.BasketOptimizer;
import io.github.afraalmaci.basketwise.routing.RoutingDtos.LegDto;
import io.github.afraalmaci.basketwise.routing.RoutingDtos.LocationDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request and response shapes for {@code POST /api/optimize}. Amounts are in base units (ml, g, pc). */
public final class PlanningDtos {

    private PlanningDtos() {
    }

    public static final double DEFAULT_VALUE_OF_TIME_EUR_PER_HOUR = 10;

    /** @param quantity how much is needed, in the product's base unit: 2000 for 2 L of milk */
    public record ItemRequest(@NotNull Long productId, @Positive long quantity) {
    }

    /**
     * @param maxStores             1 to 3; defaults to 3
     * @param valueOfTimeEurPerHour what an hour of travel is worth to the user; defaults to 10
     */
    public record OptimizeRequest(@NotNull @Valid LocationDto origin,
                                  String mode,
                                  @NotNull @Size(min = 1, max = PlanningService.MAX_ITEMS) List<@NotNull @Valid ItemRequest> items,
                                  @Min(1) @Max(BasketOptimizer.MAX_STORES) Integer maxStores,
                                  @DecimalMin("0") @DecimalMax("100") Double valueOfTimeEurPerHour) {
    }

    /** One pack size bought, with the chain's own name and label for it. */
    public record PackDto(String displayName, String quantityText, long amount, long priceCents, int count) {
    }

    /**
     * @param neededAmount what the list asked for
     * @param boughtAmount what the packs add up to; more than needed when that was cheaper
     */
    public record PurchaseDto(long productId, String name, String unit, long neededAmount, long boughtAmount,
                              long costCents, List<PackDto> packs) {
    }

    public record StopDto(long storeId, String chainCode, String storeName, double latitude, double longitude,
                          long subtotalCents, List<PurchaseDto> items) {
    }

    /**
     * One way to do the shopping.
     *
     * @param travelSeconds the round trip as used for scoring
     * @param timeCostCents travel time at the requested value of time
     * @param scoreCents    basket + time cost; plans are sorted by it
     * @param stops         stores in visiting order with what to buy at each
     * @param legs          origin → first stop, ..., last stop → origin
     * @param geometry      the route line as [latitude, longitude] pairs
     * @param roles         why the plan is shown: "best", "cheapest", "fastest" (one or more)
     */
    public record PlanDto(int storeCount, long basketCents, long travelSeconds, long travelDistanceMeters,
                          long timeCostCents, long scoreCents, List<StopDto> stops, List<LegDto> legs,
                          boolean estimated, List<List<Double>> geometry, List<String> roles) {
    }

    public record UnavailableDto(long productId, String name) {
    }

    /**
     * One chain's column in the comparison.
     *
     * @param nearestStoreName its branch with the shortest travel time; null when no travel
     *                         times were needed (nothing on the list is sold anywhere)
     * @param nearestSeconds   one-way travel time to that branch, or null
     * @param totalCents       the items it sells, bought there
     * @param itemsCovered     how many items of the list it sells
     */
    public record ChainColumnDto(String chainCode, Long nearestStoreId, String nearestStoreName, Long nearestSeconds,
                                 long totalCents, int itemsCovered) {
    }

    /** @param cheapest true for the lowest price of this item across chains (ties: all of them) */
    public record ChainPriceDto(String chainCode, long costCents, long boughtAmount, boolean cheapest,
                                List<PackDto> packs) {
    }

    /** @param prices one per chain that sells the item; a chain that does not is missing */
    public record ComparisonRowDto(long productId, String name, String unit, long neededAmount,
                                   List<ChainPriceDto> prices) {
    }

    /** Every item of the list priced at every chain, in list order. */
    public record ComparisonDto(List<ChainColumnDto> chains, List<ComparisonRowDto> items) {
    }

    /**
     * @param plans               the best plan first, then the cheapest and the fastest when
     *                            they are other plans
     * @param unavailable         items on the list that no chain sells; left out of the plans
     * @param combinationsChecked chain combinations the optimizer compared
     * @param comparison          the list priced at every chain
     */
    public record OptimizeResponse(String mode, double valueOfTimeEurPerHour, boolean estimated,
                                   int combinationsChecked, List<UnavailableDto> unavailable, List<PlanDto> plans,
                                   ComparisonDto comparison) {
    }
}
