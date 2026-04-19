package com.healthcare.interop.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        List<FieldError> fieldErrors,
        Instant timestamp
) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null, Instant.now());
    }

    public static ErrorResponse withFields(String code, String message, List<FieldError> fieldErrors) {
        return new ErrorResponse(code, message, fieldErrors, Instant.now());
    }

    public record FieldError(String field, String message) {}
}
