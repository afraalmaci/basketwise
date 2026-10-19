package io.github.afraalmaci.basketwise.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.afraalmaci.basketwise.routing.TourPlanner.Tour;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TourPlannerTest {

    @Test
    void singleStopIsThereAndBack() {
        Tour tour = TourPlanner.fastest(minutes(new Integer[][] {
                {0, 5},
                {6, 0},
        }));

        assertThat(tour.stops()).containsExactly(1);
        assertThat(tour.total().durationSeconds()).isEqualTo(11 * 60);
    }

    @Test
    void picksTheFastestOrderOfThreeStops() {
        // Stops on a line: home(0) - 1 - 2 - 3. Going out to 3 and visiting the others on the
        // way is optimal; zig-zagging 0→2→1→3 is slower.
        Tour tour = TourPlanner.fastest(minutes(new Integer[][] {
                {0, 1, 2, 3},
                {1, 0, 1, 2},
                {2, 1, 0, 1},
                {3, 2, 1, 0},
        }));

        assertThat(tour.stops()).containsExactly(1, 2, 3);
        assertThat(tour.total().durationSeconds()).isEqualTo(6 * 60);
    }

    @Test
    void respectsAsymmetricTravelTimes() {
        // 1→2 is fast but 2→1 is slow (think one-way street or a hill).
        Tour tour = TourPlanner.fastest(minutes(new Integer[][] {
                {0, 5, 5},
                {5, 0, 1},
                {5, 20, 0},
        }));

        assertThat(tour.stops()).containsExactly(1, 2);
        assertThat(tour.total().durationSeconds()).isEqualTo(11 * 60);
    }

    @Test
    void skipsOrdersWithUnreachableLegs() {
        // 0→1 has no route, so the only option is 0→2→1→0.
        Tour tour = TourPlanner.fastest(minutes(new Integer[][] {
                {0, null, 4},
                {3, 0, 2},
                {4, 9, 0},
        }));

        assertThat(tour.stops()).containsExactly(2, 1);
        assertThat(tour.total().durationSeconds()).isEqualTo(16 * 60);
    }

    @Test
    void failsWhenNothingIsReachable() {
        assertThatThrownBy(() -> TourPlanner.fastest(minutes(new Integer[][] {
                {0, null},
                {null, 0},
        }))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTooManyStops() {
        Integer[][] five = new Integer[5][5];
        for (Integer[] row : five) {
            Arrays.fill(row, 1);
        }
        assertThatThrownBy(() -> TourPlanner.fastest(minutes(five)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 to 3");
    }

    @Test
    void tiesAreBrokenByDistanceThenByFirstOrder() {
        TravelMatrix matrix = new TravelMatrix(List.of(
                List.of(new Leg(0, 0), new Leg(60, 100), new Leg(60, 100)),
                List.of(new Leg(60, 100), new Leg(0, 0), new Leg(60, 100)),
                List.of(new Leg(60, 100), new Leg(60, 100), new Leg(0, 0))), false);

        // Both orders take 3 minutes and 300 m: the first permutation wins, every time.
        assertThat(TourPlanner.fastest(matrix).stops()).containsExactly(1, 2);
    }

    @Test
    void permutationsAreComplete() {
        assertThat(TourPlanner.permutations(List.of(1, 2, 3))).containsExactly(
                List.of(1, 2, 3), List.of(1, 3, 2), List.of(2, 1, 3),
                List.of(2, 3, 1), List.of(3, 1, 2), List.of(3, 2, 1));
    }

    @Test
    void neverWorseThanAnyOtherOrderOnRandomMatrices() {
        Random random = new Random(7);
        for (int run = 0; run < 200; run++) {
            int size = 2 + random.nextInt(3); // 1 to 3 stops
            Integer[][] values = new Integer[size][size];
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    values[i][j] = i == j ? 0 : 1 + random.nextInt(30);
                }
            }
            TravelMatrix matrix = minutes(values);
            Tour best = TourPlanner.fastest(matrix);

            List<Integer> stops = new ArrayList<>();
            for (int i = 1; i < size; i++) {
                stops.add(i);
            }
            for (List<Integer> order : TourPlanner.permutations(stops)) {
                assertThat(best.total().durationSeconds())
                        .isLessThanOrEqualTo(TourPlanner.roundTrip(matrix, order).durationSeconds());
            }
        }
    }

    @Test
    void rankedSwapsInAFasterSiblingBranch() {
        // Index 1 is the chosen store, index 2 a sibling branch of the same chain, much closer.
        List<Tour> tours = TourPlanner.ranked(minutes(new Integer[][] {
                {0, 10, 3},
                {10, 0, 8},
                {3, 8, 0},
        }), List.of(List.of(1, 2)), 3, 10);

        assertThat(tours).extracting(Tour::stops).containsExactly(List.of(2), List.of(1));
        assertThat(tours.get(0).total().durationSeconds()).isEqualTo(6 * 60);
    }

    @Test
    void rankedKeepsOnlyOneDirectionOfTheSameLoop() {
        // Symmetric: 1→2 and 2→1 take equally long and draw the same line, so they are one option.
        List<Tour> tours = TourPlanner.ranked(minutes(new Integer[][] {
                {0, 4, 5},
                {4, 0, 3},
                {5, 3, 0},
        }), List.of(List.of(1), List.of(2)), 3, 10);

        assertThat(tours).extracting(Tour::stops).containsExactly(List.of(1, 2));
    }

    @Test
    void rankedMixesBranchesAndOrders() {
        // Two chains with two branches each: 0 = home, A = {1, 2}, B = {3, 4}.
        List<Tour> tours = TourPlanner.ranked(minutes(new Integer[][] {
                {0, 2, 6, 3, 9},
                {2, 0, 5, 2, 8},
                {6, 5, 0, 4, 3},
                {3, 2, 4, 0, 7},
                {9, 8, 3, 7, 0},
        }), List.of(List.of(1, 2), List.of(3, 4)), 3, 10);

        // Every option visits one A and one B branch, and options are fastest first.
        assertThat(tours).hasSize(3);
        assertThat(tours.get(0).stops()).containsExactly(1, 3); // 2 + 2 + 3 = 7 min
        for (Tour tour : tours) {
            assertThat(tour.stops()).hasSize(2);
            assertThat(tour.stops().stream().filter(i -> i <= 2).count()).isEqualTo(1L);
        }
        for (int i = 1; i < tours.size(); i++) {
            assertThat(tours.get(i).total().durationSeconds())
                    .isGreaterThanOrEqualTo(tours.get(i - 1).total().durationSeconds());
        }
    }

    @Test
    void rankedDropsAlternativesThatAreTooSlow() {
        List<Tour> tours = TourPlanner.ranked(minutes(new Integer[][] {
                {0, 5, 20},
                {5, 0, 20},
                {20, 20, 0},
        }), List.of(List.of(1, 2)), 3, 1.5);

        // 10 min vs 40 min: the second branch is 4x slower, so only the fastest is shown.
        assertThat(tours).extracting(Tour::stops).containsExactly(List.of(1));
    }

    @Test
    void rankedNeverVisitsTheSameStoreTwice() {
        // Both groups may use store 2; a pick of (2, 2) must be skipped.
        List<Tour> tours = TourPlanner.ranked(minutes(new Integer[][] {
                {0, 9, 1},
                {9, 0, 9},
                {1, 9, 0},
        }), List.of(List.of(1, 2), List.of(2)), 3, 100);

        assertThat(tours).extracting(Tour::stops).containsExactly(List.of(1, 2));
    }

    @Test
    void loopKeyIsTheSameForBothDirections() {
        assertThat(TourPlanner.loopKey(List.of(3, 1, 2))).isEqualTo(TourPlanner.loopKey(List.of(2, 1, 3)));
        assertThat(TourPlanner.loopKey(List.of(1, 2, 3))).isNotEqualTo(TourPlanner.loopKey(List.of(1, 3, 2)));
    }

    /** Builds a matrix from minutes; distance is set to 100 m per minute. {@code null} = no route. */
    private static TravelMatrix minutes(Integer[][] values) {
        List<List<Leg>> rows = new ArrayList<>();
        for (Integer[] row : values) {
            List<Leg> legs = new ArrayList<>();
            for (Integer value : row) {
                legs.add(value == null ? null : new Leg(value * 60L, value * 100L));
            }
            rows.add(legs);
        }
        return new TravelMatrix(rows, false);
    }
}
