package dev.signaldock.endpoint;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class EndpointDtos {
    private EndpointDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 2048) String url,
            @Min(1) @Max(12) int maxAttempts
    ) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 2048) String url,
            @Min(1) @Max(12) int maxAttempts,
            boolean active
    ) {
    }

    public record Response(
            UUID id,
            String name,
            String url,
            boolean active,
            int maxAttempts,
            String secretHint,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static Response from(WebhookEndpoint endpoint) {
            String secret = endpoint.getSigningSecret();
            String hint = secret.length() <= 8 ? "********" : secret.substring(0, 4) + "..." + secret.substring(secret.length() - 4);
            return new Response(
                    endpoint.getId(),
                    endpoint.getName(),
                    endpoint.getUrl(),
                    endpoint.isActive(),
                    endpoint.getMaxAttempts(),
                    hint,
                    endpoint.getCreatedAt(),
                    endpoint.getUpdatedAt()
            );
        }
    }

    public record CreatedResponse(Response endpoint, String signingSecret) {
    }
}

