package dev.signaldock.exception;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        String code,
        String message,
        Map<String, String> fieldErrors,
        String requestId,
        Instant timestamp
) {
    public static ApiError of(String code, String message, String requestId) {
        return new ApiError(code, message, Map.of(), requestId, Instant.now());
    }
}

