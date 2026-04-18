package com.healthcare.interop.common.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class TransformationException extends RuntimeException {
    private final String errorCode;
    private final UUID correlationId;

    public TransformationException(String message, String errorCode, UUID correlationId) {
        super(message);
        this.errorCode = errorCode;
        this.correlationId = correlationId;
    }

    public TransformationException(String message, String errorCode, UUID correlationId, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.correlationId = correlationId;
    }
}
