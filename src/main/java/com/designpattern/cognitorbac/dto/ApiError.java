package com.designpattern.cognitorbac.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * RFC-7807-inspired error payload returned by the global exception handler.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        ApiErrorCode code,
        String message,
        String path,
        String requestId,
        List<FieldViolation> violations
) {
    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(int status, String error, ApiErrorCode code, String message,
                              String path, String requestId) {
        return new ApiError(Instant.now(), status, error, code, message, path, requestId, null);
    }

    public static ApiError of(int status, String error, ApiErrorCode code, String message,
                              String path, String requestId, List<FieldViolation> violations) {
        return new ApiError(Instant.now(), status, error, code, message, path, requestId, violations);
    }
}
