package io.github.afraalmaci.basketwise.optimization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.afraalmaci.basketwise.optimization.PackSelector.PackCount;
import io.github.afraalmaci.basketwise.optimization.PackSelector.Selection;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PackSelectorTest {

    private static final Pack LITER = new Pack(1000, 119);
    private static final Pack LITER_AND_HALF = new Pack(1500, 129);

    @Test
    void exactFitWithTheOnlyPack() {
        Selection selection = select(2000, new Pack(1000, 119));

        assertThat(selection.packs()).containsExactly(new PackCount(new Pack(1000, 119), 2));
        assertThat(selection.costCents()).isEqualTo(238);
        assertThat(selection.boughtAmount()).isEqualTo(2000);
    }

    @Test
    void buysMoreThanNeededWhenThatIsCheaper() {
        // 2 L: two 1 L packs cost 2.38, two 1.5 L packs cost 2.58, 1.5 L + 1 L cost 2.48.
        // 1 L: the 1 L pack (1.19) beats a 1.5 L pack (1.29).
        // 1.2 L: one 1.5 L pack (1.29) beats two 1 L packs (2.38).
        assertThat(select(2000, LITER, LITER_AND_HALF).costCents()).isEqualTo(238);
        assertThat(select(1000, LITER, LITER_AND_HALF).costCents()).isEqualTo(119);

        Selection overbuy = select(1200, LITER, LITER_AND_HALF);
        assertThat(overbuy.packs()).containsExactly(new PackCount(LITER_AND_HALF, 1));
        assertThat(overbuy.boughtAmount()).isEqualTo(1500);
    }

    @Test
    void mixesPackSizes() {
        // 2.5 L: 1.5 L + 1 L = 2.48 is the cheapest way to cover it exactly.
        Selection selection = select(2500, LITER, LITER_AND_HALF);

        assertThat(selection.packs()).containsExactly(new PackCount(LITER_AND_HALF, 1), new PackCount(LITER, 1));
        assertThat(selection.costCents()).isEqualTo(248);
    }

    @Test
    void multipackIsJustABiggerPack() {
        // 10 eggs: a 10-pack (3.19) beats a 6-pack plus a 6-pack (2 x 2.09) and a 12-pack (3.59).
        Pack six = new Pack(6, 209);
        Pack ten = new Pack(10, 319);
        Pack twelve = new Pack(12, 359);

        assertThat(select(10, six, ten, twelve).packs()).containsExactly(new PackCount(ten, 1));
        // 16 eggs: 10 + 6 = 5.28 beats 12 + 6 (5.68) and 2 x 10 (6.38).
        assertThat(select(16, six, ten, twelve).costCents()).isEqualTo(528);
    }

    @Test
    void onEqualPriceBuysLessThenFewerPacks() {
        Pack small = new Pack(500, 100);
        Pack big = new Pack(1000, 200);

        // 1 L: two halves and one whole cost the same and buy the same; one pack wins.
        assertThat(select(1000, small, big).packs()).containsExactly(new PackCount(big, 1));
        // 500 ml: a 1 L pack would cost more, but at equal price less waste wins.
        assertThat(select(500, new Pack(500, 200), big).packs()).containsExactly(new PackCount(new Pack(500, 200), 1));
    }

    @Test
    void noPacksMeansNotSold() {
        assertThat(PackSelector.cheapest(1000, List.of())).isEmpty();
    }

    @Test
    void rejectsInvalidAmounts() {
        assertThatThrownBy(() -> PackSelector.cheapest(0, List.of(LITER)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PackSelector.cheapest(Long.MAX_VALUE / 2, List.of(new Pack(1, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
    }

    /** The DP must agree with trying every combination of pack counts, on many random inputs. */
    @Test
    void matchesBruteForceOnRandomInputs() {
        Random random = new Random(7);
        for (int round = 0; round < 2_000; round++) {
            List<Pack> packs = new ArrayList<>();
            int kinds = 1 + random.nextInt(3);
            for (int i = 0; i < kinds; i++) {
                packs.add(new Pack(50L * (1 + random.nextInt(12)), 20 + random.nextInt(400)));
            }
            long need = 50L * (1 + random.nextInt(40));

            Selection dp = select(need, packs.toArray(Pack[]::new));

            assertThat(dp.costCents()).isEqualTo(bruteForceCost(need, packs));
            assertThat(dp.boughtAmount()).isGreaterThanOrEqualTo(need);
            long total = dp.packs().stream().mapToLong(p -> p.pack().priceCents() * p.count()).sum();
            assertThat(total).isEqualTo(dp.costCents());
        }
    }

    private static long bruteForceCost(long need, List<Pack> packs) {
        return bruteForce(need, packs, 0);
    }

    /** Tries 0..k packs of each kind, where k packs of that kind alone would already cover the need. */
    private static long bruteForce(long remaining, List<Pack> packs, int index) {
        if (remaining <= 0) {
            return 0;
        }
        if (index == packs.size()) {
            return Long.MAX_VALUE;
        }
        Pack pack = packs.get(index);
        long best = Long.MAX_VALUE;
        long maxCount = (remaining + pack.amount() - 1) / pack.amount();
        for (long count = 0; count <= maxCount; count++) {
            long rest = bruteForce(remaining - count * pack.amount(), packs, index + 1);
            if (rest != Long.MAX_VALUE) {
                best = Math.min(best, rest + count * pack.priceCents());
            }
        }
        return best;
    }

    private static Selection select(long need, Pack... packs) {
        return PackSelector.cheapest(need, List.of(packs)).orElseThrow();
    }
}
