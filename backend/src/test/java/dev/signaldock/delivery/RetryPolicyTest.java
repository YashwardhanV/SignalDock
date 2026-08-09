package dev.signaldock.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {
    private final RetryPolicy policy = new RetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(10));

    @Test
    void calculatesExponentialBackoff() {
        assertThat(policy.delayAfterAttempt(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.delayAfterAttempt(2)).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.delayAfterAttempt(3)).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void capsBackoffAtConfiguredMaximum() {
        assertThat(policy.delayAfterAttempt(20)).isEqualTo(Duration.ofMinutes(10));
    }
}

