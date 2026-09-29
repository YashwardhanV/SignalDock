package dev.signaldock.endpoint;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.signaldock.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

class EndpointUrlValidatorTest {
    private final EndpointUrlValidator validator = new EndpointUrlValidator();

    @Test
    void rejectsNonHttpSchemes() {
        assertThatThrownBy(() -> validator.validate("ftp://example.com"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsUrlsWithCredentials() {
        assertThatThrownBy(() -> validator.validate("https://user:pass@example.com"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void acceptsHttpAndHttpsUrls() {
        assertThatCode(() -> validator.validate("https://example.com/callback")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("http://localhost:8080/x")).doesNotThrowAnyException();
    }
}
