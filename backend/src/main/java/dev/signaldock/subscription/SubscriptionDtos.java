package dev.signaldock.subscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class SubscriptionDtos {
    private SubscriptionDtos() {
    }

    public record CreateRequest(@NotBlank @Size(max = 160) String eventPattern) {
    }

    public record Response(
            UUID id,
            UUID endpointId,
            String endpointName,
            String eventPattern,
            boolean active,
            Instant createdAt
    ) {
        public static Response from(EndpointSubscription subscription) {
            return new Response(
                    subscription.getId(),
                    subscription.getEndpoint().getId(),
                    subscription.getEndpoint().getName(),
                    subscription.getEventPattern(),
                    subscription.isActive(),
                    subscription.getCreatedAt()
            );
        }
    }
}

