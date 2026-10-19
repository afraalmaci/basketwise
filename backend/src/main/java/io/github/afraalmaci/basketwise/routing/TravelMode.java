package io.github.afraalmaci.basketwise.routing;

import java.util.Locale;

/**
 * How the user travels between stores.
 *
 * @param orsProfile           OpenRouteService profile name
 * @param fallbackSpeedKmh     average speed used when no routing API is available
 */
public enum TravelMode {
    BIKE("cycling-regular", 15.0),
    WALK("foot-walking", 5.0);

    private final String orsProfile;
    private final double fallbackSpeedKmh;

    TravelMode(String orsProfile, double fallbackSpeedKmh) {
        this.orsProfile = orsProfile;
        this.fallbackSpeedKmh = fallbackSpeedKmh;
    }

    public String orsProfile() {
        return orsProfile;
    }

    public double fallbackSpeedMetersPerSecond() {
        return fallbackSpeedKmh * 1000 / 3600;
    }

    /** The lowercase name used in the API ("bike", "walk"). */
    public String apiName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses "bike" or "walk", ignoring case. A missing value means bike. */
    public static TravelMode fromApiName(String value) {
        if (value == null || value.isBlank()) {
            return BIKE;
        }
        for (TravelMode mode : values()) {
            if (mode.apiName().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown travel mode '" + value + "', expected 'bike' or 'walk'");
    }
}
