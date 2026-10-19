package io.github.afraalmaci.basketwise.routing;

import io.github.afraalmaci.basketwise.routing.RoutingDtos.LegDto;
import io.github.afraalmaci.basketwise.routing.RoutingDtos.LocationDto;
import io.github.afraalmaci.basketwise.routing.RoutingDtos.NearestStoreDto;
import io.github.afraalmaci.basketwise.routing.RoutingDtos.NearestStoresDto;
import io.github.afraalmaci.basketwise.routing.RoutingDtos.RoutingConfigDto;
import io.github.afraalmaci.basketwise.routing.RoutingDtos.ServiceAreaDto;
import io.github.afraalmaci.basketwise.routing.RoutingDtos.TripOptionDto;
import io.github.afraalmaci.basketwise.routing.RoutingDtos.TripPlanDto;
import io.github.afraalmaci.basketwise.routing.RoutingDtos.TripRequest;
import io.github.afraalmaci.basketwise.routing.RoutingDtos.TripStopDto;
import io.github.afraalmaci.basketwise.routing.RoutingExceptions.OutsideServiceAreaException;
import io.github.afraalmaci.basketwise.routing.RoutingExceptions.UnknownStoreException;
import io.github.afraalmaci.basketwise.routing.RoutingService.NearestStores;
import io.github.afraalmaci.basketwise.routing.RoutingService.Trip;
import io.github.afraalmaci.basketwise.routing.RoutingService.TripPlan;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Location-based endpoints. The user's location is only used to answer the request:
 * it is not stored or logged.
 */
@RestController
@RequestMapping("/api/routing")
public class RoutingController {

    private final RoutingService routing;

    public RoutingController(RoutingService routing) {
        this.routing = routing;
    }

    @GetMapping("/config")
    public RoutingConfigDto config() {
        ServiceArea area = routing.serviceArea();
        return new RoutingConfigDto(
                routing.provider(),
                new ServiceAreaDto(area.name(), area.minLatitude(), area.minLongitude(),
                        area.maxLatitude(), area.maxLongitude(), LocationDto.of(area.defaultLocation())),
                Arrays.stream(TravelMode.values()).map(TravelMode::apiName).toList(),
                TourPlanner.MAX_STOPS);
    }

    @GetMapping("/nearest")
    public NearestStoresDto nearest(@RequestParam("lat") double latitude,
                                    @RequestParam("lon") double longitude,
                                    @RequestParam(name = "mode", defaultValue = "bike") String mode) {
        TravelMode travelMode = TravelMode.fromApiName(mode);
        NearestStores result = routing.nearestPerChain(new GeoPoint(latitude, longitude), travelMode);
        return new NearestStoresDto(travelMode.apiName(), result.estimated(), result.stores().stream()
                .map(nearest -> new NearestStoreDto(
                        nearest.store().id(),
                        nearest.store().chainCode(),
                        nearest.store().name(),
                        nearest.store().location().latitude(),
                        nearest.store().location().longitude(),
                        nearest.leg().durationSeconds(),
                        nearest.leg().distanceMeters()))
                .toList());
    }

    @PostMapping("/trip")
    public TripPlanDto trip(@Valid @RequestBody TripRequest request) {
        TravelMode travelMode = TravelMode.fromApiName(request.mode());
        TripPlan plan = routing.planTrip(request.origin().toGeoPoint(), request.storeIds(), travelMode);
        long fastestSeconds = plan.fastest().route().total().durationSeconds();
        return new TripPlanDto(
                travelMode.apiName(),
                plan.estimated(),
                plan.options().stream().map(trip -> toDto(trip, fastestSeconds)).toList());
    }

    private static TripOptionDto toDto(Trip trip, long fastestSeconds) {
        Leg total = trip.route().total();
        return new TripOptionDto(
                trip.stops().stream()
                        .map(store -> new TripStopDto(store.id(), store.chainCode(), store.name(),
                                store.location().latitude(), store.location().longitude()))
                        .toList(),
                trip.route().legs().stream()
                        .map(leg -> new LegDto(leg.durationSeconds(), leg.distanceMeters()))
                        .toList(),
                total.durationSeconds(),
                total.distanceMeters(),
                Math.max(0, total.durationSeconds() - fastestSeconds), // road routes can differ slightly from the matrix
                trip.estimated(),
                trip.route().geometry().stream()
                        .map(point -> List.of(point.latitude(), point.longitude()))
                        .toList());
    }

    @ExceptionHandler(OutsideServiceAreaException.class)
    ProblemDetail outsideServiceArea(OutsideServiceAreaException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Outside demo area");
        return problem;
    }

    @ExceptionHandler(UnknownStoreException.class)
    ProblemDetail unknownStore(UnknownStoreException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Unknown store");
        return problem;
    }

    /** Bad coordinates, unknown travel mode, duplicate stores, unreachable stops. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid routing request");
        return problem;
    }
}
