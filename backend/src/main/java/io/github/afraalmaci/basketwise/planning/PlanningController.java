package io.github.afraalmaci.basketwise.planning;

import io.github.afraalmaci.basketwise.optimization.BasketOptimizer;
import io.github.afraalmaci.basketwise.optimization.ScoringPolicy;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.OptimizeRequest;
import io.github.afraalmaci.basketwise.planning.PlanningDtos.OptimizeResponse;
import io.github.afraalmaci.basketwise.planning.PlanningService.ListItem;
import io.github.afraalmaci.basketwise.planning.PlanningService.Outcome;
import io.github.afraalmaci.basketwise.planning.PlanningService.Request;
import io.github.afraalmaci.basketwise.planning.PlanningService.UnknownProductException;
import io.github.afraalmaci.basketwise.routing.GeoPoint;
import io.github.afraalmaci.basketwise.routing.RoutingExceptions.OutsideServiceAreaException;
import io.github.afraalmaci.basketwise.routing.TravelMode;
import jakarta.validation.Valid;
import java.util.List;
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

        List<ListItem> items = request.items().stream()
                .map(item -> new ListItem(item.productId(), item.quantity()))
                .toList();
        Outcome outcome = planning.plan(new Request(
                new GeoPoint(request.origin().latitude(), request.origin().longitude()),
                mode,
                items,
                maxStores,
                ScoringPolicy.eurosPerHour(valueOfTime)));
        return OptimizeResponseMapper.toResponse(outcome, items, mode, valueOfTime);
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
