package dev.signaldock.delivery;

import dev.signaldock.config.AppProperties;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Exponential backoff with jitter: min(base * 2^(n-1), max), then moved randomly by up to +/- jitter.
 * Without jitter, every delivery that failed during the same outage retries at the same instant and
 * hits the receiver with one big burst the moment it recovers.
 */
@Component
public class RetryPolicy {
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final double jitter;
    private final DoubleSupplier random;

    @Autowired
    public RetryPolicy(AppProperties properties) {
        this(
                properties.delivery().retryBaseDelay(),
                properties.delivery().retryMaxDelay(),
                properties.delivery().retryJitter(),
                () -> ThreadLocalRandom.current().nextDouble()
        );
    }

    RetryPolicy(Duration baseDelay, Duration maxDelay, double jitter, DoubleSupplier random) {
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.jitter = jitter;
        this.random = random;
    }

    public Duration delayAfterAttempt(int attemptNumber) {
        Duration capped = exponentialDelay(attemptNumber);
        // random() is in [0, 1), so the factor is in [1 - jitter, 1 + jitter).
        double factor = 1 + jitter * (2 * random.getAsDouble() - 1);
        long millis = Math.round(capped.toMillis() * factor);
        return Duration.ofMillis(Math.max(0, millis));
    }

    private Duration exponentialDelay(int attemptNumber) {
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
