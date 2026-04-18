package com.healthcare.interop.common.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class ValidationResult {
    private boolean valid;

    @Builder.Default
    private List<ValidationError> errors = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    private String validatorName;

    @Data
    @Builder
    public static class ValidationError {
        private String field;
        private String code;
        private String message;
        private String severity;
    }

    public static ValidationResult success() {
        return ValidationResult.builder().valid(true).build();
    }

    public static ValidationResult failure(List<ValidationError> errors) {
        return ValidationResult.builder().valid(false).errors(errors).build();
    }

    public void merge(ValidationResult other) {
        if (!other.isValid()) this.valid = false;
        this.errors.addAll(other.getErrors());
        this.warnings.addAll(other.getWarnings());
    }
}
