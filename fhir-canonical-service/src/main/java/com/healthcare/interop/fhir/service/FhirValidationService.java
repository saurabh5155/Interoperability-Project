package com.healthcare.interop.fhir.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.interop.common.model.ValidationResult.ValidationError;
import com.healthcare.interop.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FhirValidationService {

    private final FhirContext fhirContext;
    private final FhirValidator fhirValidator;

    public com.healthcare.interop.common.model.ValidationResult validate(JsonNode fhirJson) {
        try {
            IBaseResource resource = fhirContext.newJsonParser()
                .parseResource(fhirJson.toString());
            ValidationResult result = fhirValidator.validateWithResult(resource);

            if (result.isSuccessful()) {
                return com.healthcare.interop.common.model.ValidationResult.success();
            }

            List<ValidationError> errors = result.getMessages().stream()
                .filter(m -> m.getSeverity().name().equals("ERROR") || m.getSeverity().name().equals("FATAL"))
                .map(m -> ValidationError.builder()
                    .field(m.getLocationString())
                    .code("FHIR_" + m.getMessageId())
                    .message(m.getMessage())
                    .severity(m.getSeverity().name())
                    .build())
                .collect(Collectors.toList());

            List<String> warnings = result.getMessages().stream()
                .filter(m -> m.getSeverity().name().equals("WARNING") || m.getSeverity().name().equals("INFORMATION"))
                .map(m -> m.getLocationString() + ": " + m.getMessage())
                .collect(Collectors.toList());

            com.healthcare.interop.common.model.ValidationResult vr =
                com.healthcare.interop.common.model.ValidationResult.failure(errors);
            vr.setWarnings(warnings);
            vr.setValidatorName("HAPI-FHIR-R4");
            return vr;

        } catch (Exception e) {
            log.error("FHIR validation error: {}", e.getMessage());
            return com.healthcare.interop.common.model.ValidationResult.failure(
                List.of(ValidationError.builder()
                    .code("FHIR_PARSE_ERROR")
                    .message("Failed to parse FHIR resource: " + e.getMessage())
                    .severity("ERROR")
                    .build()));
        }
    }
}
