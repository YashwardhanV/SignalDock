package dev.signaldock.endpoint;

import dev.signaldock.exception.InvalidRequestException;
import java.net.URI;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class EndpointUrlValidator {
    public void validate(String rawUrl) {
        URI uri = parse(rawUrl);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new InvalidRequestException("Endpoint URL must use http or https.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidRequestException("Endpoint URL must include a host.");
        }
        if (uri.getUserInfo() != null) {
            throw new InvalidRequestException("Endpoint URL must not contain credentials.");
        }
    }

    private URI parse(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("Endpoint URL is invalid.");
        }
    }
}
