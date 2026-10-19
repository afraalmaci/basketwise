package io.github.afraalmaci.basketwise.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link RoutingClient} backed by the hosted OpenRouteService API (v2).
 *
 * <p>Any failure (network, quota exceeded, unexpected response) is logged and answered by
 * the fallback client instead, so the map keeps working with estimated times. The result's
 * {@code estimated} flag tells the UI which one it got.
 *
 * <p>The {@link RestClient} is passed in already configured with base URL, API key header
 * and timeouts (see {@link RoutingConfig}); tests bind it to a mock server instead.
 */
public class OpenRouteServiceClient implements RoutingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouteServiceClient.class);

    private final RestClient http;
    private final RoutingClient fallback;

    public OpenRouteServiceClient(RestClient http, RoutingClient fallback) {
        this.http = http;
        this.fallback = fallback;
    }

    @Override
    public TravelMatrix matrix(List<GeoPoint> sources, List<GeoPoint> destinations, TravelMode mode) {
        try {
            return requestMatrix(sources, destinations, mode);
        } catch (RestClientException | IllegalStateException e) {
            log.warn("OpenRouteService matrix failed, using straight-line estimate: {}", e.getMessage());
            return fallback.matrix(sources, destinations, mode);
        }
    }

    @Override
    public Route route(List<GeoPoint> waypoints, TravelMode mode) {
        StraightLineRoutingClient.requireTwoWaypoints(waypoints);
        try {
            return requestRoute(waypoints, mode);
        } catch (RestClientException | IllegalStateException e) {
            log.warn("OpenRouteService directions failed, using straight-line estimate: {}", e.getMessage());
            return fallback.route(waypoints, mode);
        }
    }

    @Override
    public String provider() {
        return "openrouteservice";
    }

    private TravelMatrix requestMatrix(List<GeoPoint> sources, List<GeoPoint> destinations, TravelMode mode) {
        // One locations list; sources and destinations refer to it by index (as strings).
        List<GeoPoint> locations = new ArrayList<>(sources);
        locations.addAll(destinations);
        List<String> sourceIndexes = indexes(0, sources.size());
        List<String> destinationIndexes = indexes(sources.size(), destinations.size());

        MatrixResponse response = http.post()
                .uri("/v2/matrix/{profile}", mode.orsProfile())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "locations", lonLat(locations),
                        "sources", sourceIndexes,
                        "destinations", destinationIndexes,
                        "metrics", List.of("duration", "distance")))
                .retrieve()
                .body(MatrixResponse.class);

        if (response == null || response.durations() == null || response.distances() == null
                || response.durations().size() != sources.size()
                || response.distances().size() != sources.size()) {
            throw new IllegalStateException("Unexpected matrix response shape");
        }

        List<List<Leg>> rows = new ArrayList<>(sources.size());
        for (int i = 0; i < sources.size(); i++) {
            List<Double> durations = response.durations().get(i);
            List<Double> distances = response.distances().get(i);
            if (durations.size() != destinations.size() || distances.size() != destinations.size()) {
                throw new IllegalStateException("Unexpected matrix row length");
            }
            List<Leg> row = new ArrayList<>(destinations.size());
            for (int j = 0; j < destinations.size(); j++) {
                Double duration = durations.get(j);
                Double distance = distances.get(j);
                // ORS returns null when it cannot route between two points.
                row.add(duration == null || distance == null
                        ? null
                        : new Leg(Math.round(duration), Math.round(distance)));
            }
            rows.add(row);
        }
        return new TravelMatrix(rows, false);
    }

    private Route requestRoute(List<GeoPoint> waypoints, TravelMode mode) {
        List<Integer> unlimitedSnapRadius = waypoints.stream().map(point -> -1).toList();
        DirectionsResponse response = http.post()
                .uri("/v2/directions/{profile}/geojson", mode.orsProfile())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "coordinates", lonLat(waypoints),
                        // A dragged pin can land in a park; -1 lets ORS snap to the nearest road.
                        "radiuses", unlimitedSnapRadius))
                .retrieve()
                .body(DirectionsResponse.class);

        if (response == null || response.features() == null || response.features().isEmpty()) {
            throw new IllegalStateException("Directions response has no route");
        }
        Feature feature = response.features().get(0);
        if (feature.geometry() == null || feature.geometry().coordinates() == null
                || feature.properties() == null || feature.properties().segments() == null
                || feature.properties().segments().size() != waypoints.size() - 1) {
            throw new IllegalStateException("Unexpected directions response shape");
        }

        List<GeoPoint> geometry = new ArrayList<>();
        for (List<Double> lonLat : feature.geometry().coordinates()) {
            geometry.add(new GeoPoint(lonLat.get(1), lonLat.get(0)));
        }
        // A segment between identical points can omit duration and distance.
        List<Leg> legs = feature.properties().segments().stream()
                .map(segment -> new Leg(
                        Math.round(segment.duration() == null ? 0 : segment.duration()),
                        Math.round(segment.distance() == null ? 0 : segment.distance())))
                .toList();
        return new Route(geometry, legs, false);
    }

    /** GeoJSON and ORS use [longitude, latitude]. */
    private static List<List<Double>> lonLat(List<GeoPoint> points) {
        return points.stream().map(point -> List.of(point.longitude(), point.latitude())).toList();
    }

    private static List<String> indexes(int start, int count) {
        List<String> result = new ArrayList<>(count);
        for (int i = start; i < start + count; i++) {
            result.add(String.valueOf(i));
        }
        return result;
    }

    // Only the fields we read; ORS responses contain much more.

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MatrixResponse(List<List<Double>> durations, List<List<Double>> distances) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DirectionsResponse(List<Feature> features) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Feature(Geometry geometry, Properties properties) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Geometry(List<List<Double>> coordinates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Properties(List<Segment> segments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Segment(Double distance, Double duration) {
    }
}
