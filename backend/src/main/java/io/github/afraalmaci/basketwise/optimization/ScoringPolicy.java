package io.github.afraalmaci.basketwise.optimization;

/**
 * Turns money and time into one number, so a cheaper but longer plan can be compared with
 * a quicker but pricier one.
 *
 * <pre>
 * score = basket price + travel time × value of time
 * </pre>
 *
 * <p>The value of time is the user's answer to "how much would an extra hour of shopping
 * trips have to save me?". At 10 EUR/h, a detour of 9 minutes is worth it only if it saves
 * more than 1.50 EUR.
 *
 * <p>Comparisons use {@link #exactScore}, which is the score multiplied by 3600 so that it
 * stays a whole number: no rounding, so ranking plans is exact. Rounded cents are only for
 * display.
 */
public record ScoringPolicy(long valueOfTimeCentsPerHour) {

    private static final long SECONDS_PER_HOUR = 3600;

    public ScoringPolicy {
        if (valueOfTimeCentsPerHour < 0) {
            throw new IllegalArgumentException("Value of time cannot be negative");
        }
    }

    public static ScoringPolicy eurosPerHour(double euros) {
        if (!Double.isFinite(euros) || euros < 0) {
            throw new IllegalArgumentException("Value of time must be a non-negative number, got " + euros);
        }
        return new ScoringPolicy(Math.round(euros * 100));
    }

    /** Score in cent-seconds (cents × 3600). Only for comparing plans. */
    public long exactScore(long basketCents, long travelSeconds) {
        return Math.addExact(
                Math.multiplyExact(basketCents, SECONDS_PER_HOUR),
                Math.multiplyExact(travelSeconds, valueOfTimeCentsPerHour));
    }

    /** What the travel time is worth, in cents, rounded half up. */
    public long timeCostCents(long travelSeconds) {
        return (Math.multiplyExact(travelSeconds, valueOfTimeCentsPerHour) + SECONDS_PER_HOUR / 2) / SECONDS_PER_HOUR;
    }
}
