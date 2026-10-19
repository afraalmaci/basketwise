package io.github.afraalmaci.basketwise.routing;

/**
 * The bounding box the demo data covers, plus the location to use when the user's own
 * location is unknown or outside it.
 *
 * <p>Checked on the server too, not only in the browser: requests from far away would give
 * meaningless routes and waste the routing API quota.
 */
public record ServiceArea(String name, double minLatitude, double minLongitude,
                          double maxLatitude, double maxLongitude, GeoPoint defaultLocation) {

    public ServiceArea {
        if (minLatitude >= maxLatitude || minLongitude >= maxLongitude) {
            throw new IllegalArgumentException("Service area minimum must be below maximum");
        }
        if (!containsCoordinates(minLatitude, minLongitude, maxLatitude, maxLongitude, defaultLocation)) {
            throw new IllegalArgumentException("Default location must be inside the service area");
        }
    }

    public boolean contains(GeoPoint point) {
        return containsCoordinates(minLatitude, minLongitude, maxLatitude, maxLongitude, point);
    }

    private static boolean containsCoordinates(double minLat, double minLon, double maxLat, double maxLon,
                                               GeoPoint point) {
        return point.latitude() >= minLat && point.latitude() <= maxLat
                && point.longitude() >= minLon && point.longitude() <= maxLon;
    }
}
