package com.healthcare.interop.common.exception;

import com.healthcare.interop.common.model.ValidationResult;
import lombok.Getter;

@Getter
public class ValidationException extends RuntimeException {
    private final ValidationResult validationResult;

    public ValidationException(String message, ValidationResult validationResult) {
        super(message);
        this.validationResult = validationResult;
    }
}
