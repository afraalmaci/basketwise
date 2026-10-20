package io.github.afraalmaci.basketwise.optimization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ScoringPolicyTest {

    @Test
    void travelTimeIsPricedPerHour() {
        ScoringPolicy tenEuros = ScoringPolicy.eurosPerHour(10);

        assertThat(tenEuros.valueOfTimeCentsPerHour()).isEqualTo(1000);
        assertThat(tenEuros.timeCostCents(3600)).isEqualTo(1000);
        assertThat(tenEuros.timeCostCents(540)).isEqualTo(150); // 9 minutes
        assertThat(tenEuros.timeCostCents(0)).isEqualTo(0);
    }

    @Test
    void exactScoreDoesNotRound() {
        ScoringPolicy policy = new ScoringPolicy(100); // 1 EUR/h

        // 5 s and 10 s both round to 0 cents, but the exact score still tells them apart.
        assertThat(policy.timeCostCents(5)).isEqualTo(0);
        assertThat(policy.timeCostCents(10)).isEqualTo(0);
        assertThat(policy.exactScore(500, 5)).isLessThan(policy.exactScore(500, 10));
        assertThat(policy.exactScore(500, 540)).isEqualTo(500 * 3600 + 540 * 100);
    }

    @Test
    void zeroValueOfTimeMeansPriceOnly() {
        ScoringPolicy free = ScoringPolicy.eurosPerHour(0);

        assertThat(free.exactScore(400, 99_999)).isLessThan(free.exactScore(401, 0));
    }

    @Test
    void rejectsNegativeOrInvalidValues() {
        assertThatThrownBy(() -> ScoringPolicy.eurosPerHour(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScoringPolicy.eurosPerHour(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoringPolicy(-5)).isInstanceOf(IllegalArgumentException.class);
    }
}
