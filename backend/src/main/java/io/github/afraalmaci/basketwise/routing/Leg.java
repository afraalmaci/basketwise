package io.github.afraalmaci.basketwise.routing;

/** Travel time and distance between two points, rounded to whole seconds and meters. */
public record Leg(long durationSeconds, long distanceMeters) {

    public Leg {
        if (durationSeconds < 0 || distanceMeters < 0) {
            throw new IllegalArgumentException("Duration and distance cannot be negative");
        }
    }

    public Leg plus(Leg other) {
        return new Leg(durationSeconds + other.durationSeconds, distanceMeters + other.distanceMeters);
    }
}
