package dev.signaldock.exception;

/** The same Idempotency-Key was sent again with a different event type or payload. */
public class IdempotencyKeyReuseException extends RuntimeException {
    public IdempotencyKeyReuseException(String message) {
        super(message);
    }
}
