package io.github.afraalmaci.basketwise.routing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request and response shapes for the routing API. Coordinates are latitude first. */
public final class RoutingDtos {

    private RoutingDtos() {
    }

    public record LocationDto(double latitude, double longitude) {

        static LocationDto of(GeoPoint point) {
            return new LocationDto(point.latitude(), point.longitude());
        }

        GeoPoint toGeoPoint() {
            return new GeoPoint(latitude, longitude);
        }
    }

    public record ServiceAreaDto(String name, double minLatitude, double minLongitude,
                                 double maxLatitude, double maxLongitude, LocationDto defaultLocation) {
    }

    /** What the frontend needs before it can ask anything: where the demo works, which modes exist. */
    public record RoutingConfigDto(String provider, ServiceAreaDto serviceArea, List<String> modes,
                                   int maxStops) {
    }

    public record NearestStoreDto(long storeId, String chainCode, String storeName,
                                  double latitude, double longitude,
                                  long durationSeconds, long distanceMeters) {
    }

    /** @param estimated true when times are straight-line estimates, not real routes */
    public record NearestStoresDto(String mode, boolean estimated, List<NearestStoreDto> stores) {
    }

    public record TripRequest(@NotNull @Valid LocationDto origin,
                              @NotNull @Size(min = 1, max = TourPlanner.MAX_STOPS) List<@NotNull Long> storeIds,
                              String mode) {
    }

    public record TripStopDto(long storeId, String chainCode, String storeName, double latitude, double longitude) {
    }

    public record LegDto(long durationSeconds, long distanceMeters) {
    }

    /**
     * One way to do the trip.
     *
     * @param stops        stores in visiting order
     * @param legs         origin → first stop, ..., last stop → origin (stops + 1 entries)
     * @param extraSeconds how much slower than the fastest option (0 for the fastest)
     * @param geometry     the route line as [latitude, longitude] pairs, ready for Leaflet
     */
    public record TripOptionDto(List<TripStopDto> stops, List<LegDto> legs,
                                long totalDurationSeconds, long totalDistanceMeters, long extraSeconds,
                                boolean estimated, List<List<Double>> geometry) {
    }

    /** @param options fastest first, then up to two alternatives */
    public record TripPlanDto(String mode, boolean estimated, List<TripOptionDto> options) {
    }
}
