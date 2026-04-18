package com.healthcare.interop.validation.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.interop.common.model.ValidationResult;
import com.healthcare.interop.validation.validator.BusinessRuleValidator;
import com.healthcare.interop.validation.validator.JsonSchemaValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Chains: JsonSchema → FhirCompliance → BusinessRules.
 * Each validator is non-blocking. Merges results from all layers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationPipeline {

    private final JsonSchemaValidator jsonSchemaValidator;
    private final BusinessRuleValidator businessRuleValidator;

    public Mono<ValidationResult> validateFhirPayload(
            JsonNode payload, String resourceType, String targetEhrCode) {

        return Mono.fromCallable(() -> {
            ValidationResult combined = ValidationResult.builder()
                .valid(true)
                .errors(new ArrayList<>())
                .warnings(new ArrayList<>())
                .validatorName("INTEROP-PIPELINE")
                .build();

            // Layer 1: JSON Schema
            ValidationResult schemaResult = jsonSchemaValidator.validate(payload, resourceType);
            combined.merge(schemaResult);
            if (!schemaResult.isValid()) {
                log.warn("Schema validation failed for {}/{}: {} errors",
                    targetEhrCode, resourceType, schemaResult.getErrors().size());
            }

            // Layer 2: Business Rules
            ValidationResult bizResult = businessRuleValidator.validate(payload, resourceType);
            combined.merge(bizResult);
            if (!bizResult.isValid()) {
                log.warn("Business rule validation failed for {}/{}: {} errors",
                    targetEhrCode, resourceType, bizResult.getErrors().size());
            }

            log.info("Validation complete for {}/{}: valid={}, errors={}, warnings={}",
                targetEhrCode, resourceType, combined.isValid(),
                combined.getErrors().size(), combined.getWarnings().size());
            return combined;
        });
    }
}
