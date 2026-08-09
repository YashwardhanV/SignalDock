package dev.signaldock.delivery;

import dev.signaldock.config.AppProperties;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {
    private final Duration baseDelay;
    private final Duration maxDelay;

    @Autowired
    public RetryPolicy(AppProperties properties) {
        this.baseDelay = properties.delivery().retryBaseDelay();
        this.maxDelay = properties.delivery().retryMaxDelay();
    }

    RetryPolicy(Duration baseDelay, Duration maxDelay) {
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
    }

    public Duration delayAfterAttempt(int attemptNumber) {
        int exponent = Math.max(0, Math.min(30, attemptNumber - 1));
        long multiplier = 1L << exponent;
        try {
            Duration calculated = baseDelay.multipliedBy(multiplier);
            return calculated.compareTo(maxDelay) > 0 ? maxDelay : calculated;
        } catch (ArithmeticException exception) {
            return maxDelay;
        }
    }
}
