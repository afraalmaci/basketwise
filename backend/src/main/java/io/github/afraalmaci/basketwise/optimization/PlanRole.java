package io.github.afraalmaci.basketwise.optimization;

/** Why a plan is shown: the one to pick, or the extreme on price or on time. */
public enum PlanRole {
    /** Lowest score: price plus travel time at the user's value of time. */
    BEST,
    /** Lowest basket price, however long the trip. */
    CHEAPEST,
    /** Shortest trip, whatever the basket costs. */
    FASTEST
}
