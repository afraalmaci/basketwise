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
     */
    public record PlanDto(int storeCount, long basketCents, long travelSeconds, long travelDistanceMeters,
                          long timeCostCents, long scoreCents, List<StopDto> stops, List<LegDto> legs,
                          boolean estimated, List<List<Double>> geometry) {
    }

    public record UnavailableDto(long productId, String name) {
    }

    /**
     * @param plans               the best plan for each number of stores, best first
     * @param unavailable         items on the list that no chain sells; left out of the plans
     * @param combinationsChecked chain combinations the optimizer compared
     */
    public record OptimizeResponse(String mode, double valueOfTimeEurPerHour, boolean estimated,
                                   int combinationsChecked, List<UnavailableDto> unavailable, List<PlanDto> plans) {
    }
}
