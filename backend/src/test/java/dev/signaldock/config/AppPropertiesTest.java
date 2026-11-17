package dev.signaldock.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AppPropertiesTest {
    @Test
    void rejectsALeaseThatIsNotLongerThanTheLongestPossibleSend() {
        assertThatThrownBy(() -> delivery(Duration.ofSeconds(6)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lease-duration");
    }

    @Test
    void acceptsALeaseLongerThanTheLongestPossibleSend() {
        assertThat(delivery(Duration.ofSeconds(30)).maxSendDuration()).isEqualTo(Duration.ofSeconds(6));
    }

    private static AppProperties.Delivery delivery(Duration lease) {
        return new AppProperties.Delivery(
                Duration.ofSeconds(2), Duration.ofSeconds(4), 20, lease,
                Duration.ofSeconds(30), Duration.ofHours(1), 0.2, 2, 5);
    }
}
