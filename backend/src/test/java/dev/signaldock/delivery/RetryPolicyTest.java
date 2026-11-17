package dev.signaldock.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {
    private final RetryPolicy noJitter = new RetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(10), 0, () -> 0.5);

    @Test
    void calculatesExponentialBackoff() {
        assertThat(noJitter.delayAfterAttempt(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(noJitter.delayAfterAttempt(2)).isEqualTo(Duration.ofMinutes(1));
        assertThat(noJitter.delayAfterAttempt(3)).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void capsBackoffAtConfiguredMaximum() {
        assertThat(noJitter.delayAfterAttempt(20)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void jitterMovesTheDelayWithinTheConfiguredBand() {
        RetryPolicy lowest = new RetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(10), 0.2, () -> 0.0);
        RetryPolicy middle = new RetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(10), 0.2, () -> 0.5);
        RetryPolicy highest = new RetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(10), 0.2, () -> 0.999_999);

        assertThat(lowest.delayAfterAttempt(2)).isEqualTo(Duration.ofSeconds(48));
        assertThat(middle.delayAfterAttempt(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(highest.delayAfterAttempt(2)).isBetween(Duration.ofSeconds(71), Duration.ofSeconds(72));
    }

    @RepeatedTest(50)
    void realRandomJitterStaysInsideTheBand() {
        RetryPolicy policy = new RetryPolicy(
                Duration.ofSeconds(30), Duration.ofMinutes(10), 0.2,
                () -> java.util.concurrent.ThreadLocalRandom.current().nextDouble());
        assertThat(policy.delayAfterAttempt(20)).isBetween(Duration.ofMinutes(8), Duration.ofMinutes(12));
    }
}
