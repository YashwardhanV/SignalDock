package dev.signaldock.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.signaldock.exception.InvalidRequestException;
import dev.signaldock.subscription.EventPatternMatcher;
import org.junit.jupiter.api.Test;

class EventPatternMatcherTest {
    private final EventPatternMatcher matcher = new EventPatternMatcher();

    @Test
    void matchesExactAndTrailingWildcardPatterns() {
        assertThat(matcher.matches("order.created", "order.created")).isTrue();
        assertThat(matcher.matches("order.*", "order.created")).isTrue();
        assertThat(matcher.matches("order.*", "invoice.created")).isFalse();
        assertThat(matcher.matches("*", "anything.happened")).isTrue();
    }

    @Test
    void doesNotLetWildcardMatchEmptySuffix() {
        assertThat(matcher.matches("order.*", "order.")).isFalse();
        assertThat(matcher.matches("order.*", "order")).isFalse();
    }

    @Test
    void rejectsMidPatternWildcards() {
        assertThatThrownBy(() -> matcher.validate("order.*.created"))
                .isInstanceOf(InvalidRequestException.class);
    }
}

