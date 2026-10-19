package io.github.afraalmaci.basketwise.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plans round trips from the user's location through a few stores.
 *
 * <p>Choosing the order is the travelling salesman problem, which is NP-hard in general, but
 * the app limits a trip to {@value #MAX_STOPS} stops: at most 3! = 6 orders. On top of that,
 * each stop can be any of a few branches of the same chain. Even with 3 branches per stop
 * that is 3^3 x 6 = 162 tours, so trying every one is exact and takes microseconds.
 *
 * <p>The matrix may be asymmetric (one-way streets, hills), so A→B→C and C→B→A are evaluated
 * separately, but they are the same loop on the map, so only the faster one is kept as an
 * alternative.
 */
public final class TourPlanner {

    public static final int MAX_STOPS = 3;

    private TourPlanner() {
    }

    /**
     * @param stops the order to visit the stops, as indexes into the matrix (1..n)
     * @param total round-trip time and distance
     */
    public record Tour(List<Integer> stops, Leg total) {
    }

    /** Fastest first; ties by distance, then by the order itself, so results are deterministic. */
    private static final Comparator<Tour> FASTEST_FIRST = Comparator
            .comparingLong((Tour tour) -> tour.total().durationSeconds())
            .thenComparingLong(tour -> tour.total().distanceMeters())
            .thenComparing(Tour::stops, TourPlanner::compareLists);

    /**
     * Fastest order through every stop of the matrix.
     *
     * @param matrix square matrix where index 0 is the start/end point and 1..n are stops
     * @throws IllegalArgumentException if there are no stops, too many, or no order is
     *                                  reachable
     */
    public static Tour fastest(TravelMatrix matrix) {
        requireSquare(matrix);
        List<List<Integer>> groups = new ArrayList<>();
        for (int i = 1; i < matrix.sourceCount(); i++) {
            groups.add(List.of(i));
        }
        return ranked(matrix, groups, 1, Double.POSITIVE_INFINITY).get(0);
    }

    /**
     * The best distinct tours that visit one stop from each group, in any order.
     *
     * <p>A group is the set of interchangeable stops for one thing the user wants to visit,
     * for example the three nearest branches of one chain. Two tours are the same alternative
     * when they visit the same stops as one loop in either direction.
     *
     * @param groups      per stop, the matrix indexes (1..n) it may be served by
     * @param limit       maximum number of tours returned
     * @param maxSlowdown drop tours slower than this factor times the fastest (1.5 = +50%)
     * @return fastest first; never empty
     * @throws IllegalArgumentException if the groups are invalid or nothing is reachable
     */
    public static List<Tour> ranked(TravelMatrix matrix, List<List<Integer>> groups, int limit, double maxSlowdown) {
        requireSquare(matrix);
        if (groups.isEmpty() || groups.size() > MAX_STOPS) {
            throw new IllegalArgumentException("A trip needs 1 to " + MAX_STOPS + " stops, got " + groups.size());
        }
        for (List<Integer> group : groups) {
            if (group.isEmpty()) {
                throw new IllegalArgumentException("Every stop needs at least one candidate");
            }
            for (int index : group) {
                if (index < 1 || index >= matrix.sourceCount()) {
                    throw new IllegalArgumentException("Stop index " + index + " is not in the matrix");
                }
            }
        }

        List<Tour> candidates = new ArrayList<>();
        for (List<Integer> choice : choices(groups)) {
            for (List<Integer> order : permutations(choice)) {
                Leg total = roundTrip(matrix, order);
                if (total != null) {
                    candidates.add(new Tour(order, total));
                }
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No reachable order for these stops");
        }
        candidates.sort(FASTEST_FIRST);

        long cutoff = (long) Math.floor(candidates.get(0).total().durationSeconds() * maxSlowdown);
        Set<List<Integer>> seenLoops = new HashSet<>();
        List<Tour> result = new ArrayList<>();
        for (Tour tour : candidates) {
            if (result.size() >= limit) {
                break;
            }
            if (!result.isEmpty() && tour.total().durationSeconds() > cutoff) {
                break; // sorted, so everything after is slower too
            }
            if (seenLoops.add(loopKey(tour.stops()))) {
                result.add(tour);
            }
        }
        return List.copyOf(result);
    }

    /** Start → stops in order → start. {@code null} if any leg is unreachable. */
    static Leg roundTrip(TravelMatrix matrix, List<Integer> order) {
        Leg total = new Leg(0, 0);
        int previous = 0;
        for (int stop : order) {
            Leg leg = matrix.leg(previous, stop);
            if (leg == null) {
                return null;
            }
            total = total.plus(leg);
            previous = stop;
        }
        Leg back = matrix.leg(previous, 0);
        return back == null ? null : total.plus(back);
    }

    /** The same loop driven backwards looks identical on the map: A→B→C and C→B→A share a key. */
    static List<Integer> loopKey(List<Integer> order) {
        List<Integer> reversed = new ArrayList<>(order).reversed();
        return compareLists(order, reversed) <= 0 ? order : List.copyOf(reversed);
    }

    /** Every way to pick one stop per group, skipping picks that use the same stop twice. */
    static List<List<Integer>> choices(List<List<Integer>> groups) {
        List<List<Integer>> result = new ArrayList<>();
        choose(groups, 0, new ArrayList<>(), result);
        return result;
    }

    private static void choose(List<List<Integer>> groups, int depth, List<Integer> picked, List<List<Integer>> result) {
        if (depth == groups.size()) {
            result.add(List.copyOf(picked));
            return;
        }
        for (int index : groups.get(depth)) {
            if (picked.contains(index)) {
                continue;
            }
            picked.add(index);
            choose(groups, depth + 1, picked, result);
            picked.remove(picked.size() - 1);
        }
    }

    /** All orderings, in lexicographic order of the input. */
    static List<List<Integer>> permutations(List<Integer> items) {
        List<List<Integer>> result = new ArrayList<>();
        permute(new ArrayList<>(), new ArrayList<>(items), result);
        return result;
    }

    private static void permute(List<Integer> prefix, List<Integer> remaining, List<List<Integer>> result) {
        if (remaining.isEmpty()) {
            result.add(List.copyOf(prefix));
            return;
        }
        for (int i = 0; i < remaining.size(); i++) {
            Integer next = remaining.remove(i);
            prefix.add(next);
            permute(prefix, remaining, result);
            prefix.remove(prefix.size() - 1);
            remaining.add(i, next);
        }
    }

    private static int compareLists(List<Integer> a, List<Integer> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            int cmp = Integer.compare(a.get(i), b.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(a.size(), b.size());
    }

    private static void requireSquare(TravelMatrix matrix) {
        if (matrix.sourceCount() != matrix.destinationCount()) {
            throw new IllegalArgumentException("Tour planning needs a square matrix");
        }
    }
}
