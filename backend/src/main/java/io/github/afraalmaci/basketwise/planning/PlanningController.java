package io.github.afraalmaci.basketwise.planning;

import io.github.afraalmaci.basketwise.catalog.CatalogDtos.OfferDto;
import io.github.afraalmaci.basketwise.catalog.CatalogDtos.ProductDto;
import io.github.afraalmaci.basketwise.optimization.BasketOptimizer;
import io.github.afraalmaci.basketwise.optimization.BasketOptimizer.Purchase;
import io.github.afraalmaci.basketwise.optimization.PackSelector.PackCount;
import io.github.afraalmaci.basketwise.optimization.ScoringPolicy;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.OptimizeRequest;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.OptimizeResponse;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.PackDto;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.PlanDto;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.PurchaseDto;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.StopDto;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.UnavailableDto;
import io.github.afraalmaci.basketwise.planning.PlanningService.ListItem;
import io.github.afraalmaci.basketwise.planning.PlanningService.Outcome;
import io.github.afraalmaci.basketwise.planning.PlanningService.Request;
import io.github.afraalmaci.basketwise.planning.PlanningService.RoutedPlan;
import io.github.afraalmaci.basketwise.planning.PlanningService.Stop;
import io.github.afraalmaci.basketwise.planning.PlanningService.UnknownProductException;
import io.github.afraalmaci.basketwise.routing.GeoPoint;
import io.github.afraalmaci.basketwise.routing.RoutingDtos.LegDto;
import io.github.afraalmaci.basketwise.routing.RoutingExceptions.OutsideServiceAreaException;
import io.github.afraalmaci.basketwise.routing.TravelMode;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The route-aware optimizer. Like the routing endpoints, the location is only used to answer
 * the request: it is not stored or logged.
 */
@RestController
@RequestMapping("/api")
public class PlanningController {

    private final PlanningService planning;

    public PlanningController(PlanningService planning) {
        this.planning = planning;
    }

    @PostMapping("/optimize")
    public OptimizeResponse optimize(@Valid @RequestBody OptimizeRequest request) {
        TravelMode mode = TravelMode.fromApiName(request.mode());
        double valueOfTime = request.valueOfTimeEurPerHour() == null
                ? PlanningDtos.DEFAULT_VALUE_OF_TIME_EUR_PER_HOUR
                : request.valueOfTimeEurPerHour();
        int maxStores = request.maxStores() == null ? BasketOptimizer.MAX_STORES : request.maxStores();

        Outcome outcome = planning.plan(new Request(
                new GeoPoint(request.origin().latitude(), request.origin().longitude()),
                mode,
                request.items().stream().map(item -> new ListItem(item.productId(), item.quantity())).toList(),
                maxStores,
                ScoringPolicy.eurosPerHour(valueOfTime)));

        return new OptimizeResponse(
                mode.apiName(),
                valueOfTime,
                outcome.estimated(),
                outcome.combinationsChecked(),
                outcome.unavailable().stream().map(product -> new UnavailableDto(product.id(), product.name())).toList(),
                outcome.plans().stream().map(plan -> toDto(plan, outcome.products())).toList());
    }

    private static PlanDto toDto(RoutedPlan routed, Map<Long, ProductDto> products) {
        return new PlanDto(
                routed.plan().storeCount(),
                routed.plan().basketCents(),
                routed.plan().trip().durationSeconds(),
                routed.plan().trip().distanceMeters(),
                routed.plan().timeCostCents(),
                routed.plan().scoreCents(),
                routed.stops().stream().map(stop -> toDto(stop, products)).toList(),
                routed.route().legs().stream().map(leg -> new LegDto(leg.durationSeconds(), leg.distanceMeters())).toList(),
                routed.estimated(),
                routed.route().geometry().stream().map(point -> List.of(point.latitude(), point.longitude())).toList());
    }

    private static StopDto toDto(Stop stop, Map<Long, ProductDto> products) {
        return new StopDto(
                stop.store().id(),
                stop.store().chainCode(),
                stop.store().name(),
                stop.store().location().latitude(),
                stop.store().location().longitude(),
                stop.subtotalCents(),
                stop.purchases().stream().map(purchase -> toDto(purchase, products.get(purchase.productId()))).toList());
    }

    private static PurchaseDto toDto(Purchase purchase, ProductDto product) {
        return new PurchaseDto(
                product.id(),
                product.name(),
                product.unit(),
                purchase.neededAmount(),
                purchase.selection().boughtAmount(),
                purchase.selection().costCents(),
                purchase.selection().packs().stream().map(count -> toDto(count, purchase.chainCode(), product)).toList());
    }

    /** The chain's own label for the pack: (chain, product, size) identifies one offer. */
    private static PackDto toDto(PackCount count, String chainCode, ProductDto product) {
        OfferDto offer = product.offers().stream()
                .filter(o -> o.chainCode().equals(chainCode) && o.quantityAmount() == count.pack().amount())
                .findFirst()
                .orElseThrow();
        return new PackDto(offer.displayName(), offer.quantityText(), offer.quantityAmount(), offer.priceCents(),
                count.count());
    }

    @ExceptionHandler(OutsideServiceAreaException.class)
    ProblemDetail outsideServiceArea(OutsideServiceAreaException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Outside demo area");
        return problem;
    }

    @ExceptionHandler(UnknownProductException.class)
    ProblemDetail unknownProduct(UnknownProductException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Unknown product");
        return problem;
    }

    /** Bad coordinates, unknown travel mode, duplicate items, quantities out of range. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid optimization request");
        return problem;
    }
}
