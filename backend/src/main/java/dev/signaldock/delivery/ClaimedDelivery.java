package dev.signaldock.delivery;

import java.util.UUID;

/** A delivery this worker has claimed, plus the token that proves the claim is still ours. */
public record ClaimedDelivery(UUID deliveryId, UUID claimToken) {
}
