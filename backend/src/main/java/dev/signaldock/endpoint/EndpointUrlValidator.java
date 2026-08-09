package dev.signaldock.endpoint;

import dev.signaldock.config.AppProperties;
import dev.signaldock.exception.InvalidRequestException;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public class EndpointUrlValidator {
    private final AppProperties properties;

    public EndpointUrlValidator(AppProperties properties) {
        this.properties = properties;
    }

    public void validate(String rawUrl) {
        URI uri = parse(rawUrl);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        boolean schemeAllowed = "https".equals(scheme)
                || (properties.delivery().allowHttp() && "http".equals(scheme));
        if (!schemeAllowed) {
            throw new InvalidRequestException("Endpoint must use HTTPS.");
        }
        if (uri.getUserInfo() != null) {
            throw new InvalidRequestException("Endpoint URL must not contain credentials.");
        }
        if (uri.getFragment() != null) {
            throw new InvalidRequestException("Endpoint URL must not contain a fragment.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidRequestException("Endpoint URL must include a host.");
        }

        if (!properties.delivery().allowPrivateNetworks()) {
            String host = IDN.toASCII(uri.getHost()).toLowerCase();
            InetAddress[] addresses = resolve(host);
            if (Arrays.stream(addresses).anyMatch(this::isLocalOrPrivate)) {
                throw new InvalidRequestException("Endpoint must not resolve to a private or local network.");
            }
        }
    }

    private URI parse(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("Endpoint URL is invalid.");
        }
    }

    private InetAddress[] resolve(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (Exception exception) {
            throw new InvalidRequestException("Endpoint host could not be resolved.");
        }
    }

    private boolean isLocalOrPrivate(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }
}

