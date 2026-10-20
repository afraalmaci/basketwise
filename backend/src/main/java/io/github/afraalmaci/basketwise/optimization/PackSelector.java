package io.github.afraalmaci.basketwise.optimization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Cheapest combination of packs that covers a needed amount.
 *
 * <p>"I need 2 L of milk; the chain sells 1 L for 1.19 and 1.5 L for 1.29." Buying more than
 * needed is allowed (two 1.5 L packs can be cheaper than two 1 L packs). This is the
 * min-cost covering version of the unbounded knapsack problem, solved with dynamic
 * programming:
 *
 * <pre>
 * best[0] = nothing bought
 * best[q] = min over packs p of  best[max(0, q - p.amount)] + p
 * </pre>
 *
 * <p>Ties are broken by buying less (less waste), then by fewer packs. Comparing
 * (cost, amount, packs) lexicographically keeps the DP correct: adding the same pack to two
 * states adds the same vector to both, which never changes their order.
 *
 * <p>Every combination of packs adds up to a multiple of the greatest common divisor of the
 * pack sizes, so the DP runs in units of that divisor: for 1000 ml and 1500 ml packs that is
 * 500 ml, and "2 L" becomes 4 steps instead of 2000.
 */
public final class PackSelector {

    /** Guards against absurd requests; the API limits quantities long before this. */
    static final long MAX_STEPS = 1_000_000;

    private PackSelector() {
    }

    public record PackCount(Pack pack, int count) {
    }

    /**
     * @param packs        packs to buy, largest first
     * @param costCents    total price
     * @param boughtAmount total amount bought; at least the needed amount
     */
    public record Selection(List<PackCount> packs, long costCents, long boughtAmount) {

        public Selection {
            packs = List.copyOf(packs);
        }

        public int packCount() {
            return packs.stream().mapToInt(PackCount::count).sum();
        }
    }

    /**
     * @return the cheapest selection, or empty when there are no packs to choose from
     * @throws IllegalArgumentException if the needed amount is not positive or absurdly large
     */
    public static Optional<Selection> cheapest(long neededAmount, List<Pack> packs) {
        if (neededAmount <= 0) {
            throw new IllegalArgumentException("Needed amount must be positive, got " + neededAmount);
        }
        if (packs.isEmpty()) {
            return Optional.empty();
        }

        long divisor = 0;
        for (Pack pack : packs) {
            divisor = gcd(divisor, pack.amount());
        }
        final long step = divisor;
        long steps = ceilDiv(neededAmount, step);
        if (steps > MAX_STEPS) {
            throw new IllegalArgumentException("Needed amount " + neededAmount + " is too large");
        }
        int target = (int) steps;
        int[] size = packs.stream().mapToInt(pack -> (int) (pack.amount() / step)).toArray();

        // State per covered amount q (in steps): cost, amount bought (steps), packs, last pack.
        long[] cost = new long[target + 1];
        long[] bought = new long[target + 1];
        int[] count = new int[target + 1];
        int[] last = new int[target + 1];
        Arrays.fill(cost, Long.MAX_VALUE);
        cost[0] = 0;

        for (int q = 1; q <= target; q++) {
            for (int i = 0; i < size.length; i++) {
                int previous = Math.max(0, q - size[i]);
                long candidateCost = cost[previous] + packs.get(i).priceCents();
                long candidateBought = bought[previous] + size[i];
                int candidateCount = count[previous] + 1;
                if (better(candidateCost, candidateBought, candidateCount, cost[q], bought[q], count[q])) {
                    cost[q] = candidateCost;
                    bought[q] = candidateBought;
                    count[q] = candidateCount;
                    last[q] = i;
                }
            }
        }

        // Walk back from the target to see which packs were used.
        int[] used = new int[size.length];
        for (int q = target; q > 0; q = Math.max(0, q - size[last[q]])) {
            used[last[q]]++;
        }
        List<PackCount> result = new ArrayList<>();
        for (int i = 0; i < used.length; i++) {
            if (used[i] > 0) {
                result.add(new PackCount(packs.get(i), used[i]));
            }
        }
        result.sort((a, b) -> Long.compare(b.pack().amount(), a.pack().amount()));
        return Optional.of(new Selection(result, cost[target], bought[target] * step));
    }

    private static boolean better(long cost, long bought, int count, long bestCost, long bestBought, int bestCount) {
        if (cost != bestCost) {
            return cost < bestCost;
        }
        if (bought != bestBought) {
            return bought < bestBought;
        }
        return count < bestCount;
    }

    static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }
}
