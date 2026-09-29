package dev.signaldock.endpoint;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.signaldock.config.AppProperties;
import dev.signaldock.exception.InvalidRequestException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class EndpointUrlValidatorTest {
    @Test
    void rejectsHttpAndPrivateNetworksByDefault() {
        EndpointUrlValidator validator = new EndpointUrlValidator(properties(false, false));

        assertThatThrownBy(() -> validator.validate("http://example.com/callback"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> validator.validate("https://127.0.0.1/callback"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void acceptsPublicHttpsEndpoint() {
        EndpointUrlValidator validator = new EndpointUrlValidator(properties(false, false));

        assertThatCode(() -> validator.validate("https://example.com/callback")).doesNotThrowAnyException();
    }

    @Test
    void localDemoCanExplicitlyAllowHttpPrivateEndpoint() {
        EndpointUrlValidator validator = new EndpointUrlValidator(properties(true, true));

        assertThatCode(() -> validator.validate("http://127.0.0.1:8080/callback")).doesNotThrowAnyException();
    }

    private AppProperties properties(boolean allowHttp, boolean allowPrivate) {
        return new AppProperties(
                "admin",
                List.of("http://localhost:3000"),
                new AppProperties.Delivery(
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        10,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(1),
                        Duration.ofMinutes(1),
                        4000,
                        allowHttp,
                        allowPrivate
                ),
                new AppProperties.Demo(false, "http://localhost", "http://localhost")
        );
    }
}

