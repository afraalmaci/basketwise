package io.github.afraalmaci.basketwise.routing;

/** Errors caused by the request, mapped to 4xx responses by the controller. */
public final class RoutingExceptions {

    private RoutingExceptions() {
    }

    public static class OutsideServiceAreaException extends RuntimeException {
        public OutsideServiceAreaException(GeoPoint point, ServiceArea area) {
            super("Location " + point.latitude() + ", " + point.longitude()
                    + " is outside the demo area (" + area.name() + ")");
        }
    }

    public static class UnknownStoreException extends RuntimeException {
        public UnknownStoreException(long storeId) {
            super("Unknown store id " + storeId);
        }
    }
}
