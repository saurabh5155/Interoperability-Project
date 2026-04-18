package com.healthcare.interop.validation.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.interop.common.model.ValidationResult;
import com.healthcare.interop.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Domain-specific healthcare business rules.
 * These rules enforce clinical data quality beyond structural validation.
 */
@Service
@Slf4j
public class BusinessRuleValidator {

    private static final Set<String> VALID_GENDERS = Set.of("male", "female", "other", "unknown");
    private static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    public ValidationResult validate(JsonNode payload, String resourceType) {
        List<ValidationResult.ValidationError> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        return switch (resourceType.toUpperCase()) {
            case "PATIENT" -> validatePatient(payload);
            case "ENCOUNTER" -> validateEncounter(payload);
            case "OBSERVATION" -> validateObservation(payload);
            default -> ValidationResult.success();
        };
    }

    private ValidationResult validatePatient(JsonNode patient) {
        List<ValidationResult.ValidationError> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Rule 1: Patient must have at least one name
        if (patient.path("name").isMissingNode() || patient.path("name").isEmpty()) {
            errors.add(error("name", "BR_PATIENT_001", "Patient must have at least one name"));
        }

        // Rule 2: BirthDate must be valid and not in future
        JsonUtils.extractString(patient, "$.birthDate").ifPresent(dob -> {
            try {
                LocalDate birthDate = LocalDate.parse(dob);
                if (birthDate.isAfter(LocalDate.now())) {
                    errors.add(error("birthDate", "BR_PATIENT_002", "Birth date cannot be in the future"));
                }
                if (birthDate.isBefore(MIN_BIRTH_DATE)) {
                    warnings.add("Birth date is suspiciously old: " + dob);
                }
            } catch (DateTimeParseException e) {
                errors.add(error("birthDate", "BR_PATIENT_003",
                    "Birth date format invalid (expected YYYY-MM-DD): " + dob));
            }
        });

        // Rule 3: Gender must be FHIR-valid
        JsonUtils.extractString(patient, "$.gender").ifPresent(gender -> {
            if (!VALID_GENDERS.contains(gender.toLowerCase())) {
                errors.add(error("gender", "BR_PATIENT_004",
                    "Gender must be one of: male, female, other, unknown. Got: " + gender));
            }
        });

        // Rule 4: Phone number should be present (warning if missing)
        if (patient.path("telecom").isMissingNode() || patient.path("telecom").isEmpty()) {
            warnings.add("Patient has no contact information (telecom)");
        }

        ValidationResult result = errors.isEmpty() ? ValidationResult.success()
            : ValidationResult.failure(errors);
        result.setWarnings(warnings);
        result.setValidatorName("BUSINESS-RULES-PATIENT");
        return result;
    }

    private ValidationResult validateEncounter(JsonNode encounter) {
        List<ValidationResult.ValidationError> errors = new ArrayList<>();

        if (encounter.path("subject").isMissingNode()) {
            errors.add(error("subject", "BR_ENCOUNTER_001", "Encounter must reference a patient subject"));
        }
        if (encounter.path("status").isMissingNode()) {
            errors.add(error("status", "BR_ENCOUNTER_002", "Encounter status is required"));
        }

        ValidationResult result = errors.isEmpty() ? ValidationResult.success()
            : ValidationResult.failure(errors);
        result.setValidatorName("BUSINESS-RULES-ENCOUNTER");
        return result;
    }

    private ValidationResult validateObservation(JsonNode observation) {
        List<ValidationResult.ValidationError> errors = new ArrayList<>();

        if (observation.path("code").isMissingNode()) {
            errors.add(error("code", "BR_OBS_001", "Observation must have a LOINC or SNOMED code"));
        }
        if (observation.path("subject").isMissingNode()) {
            errors.add(error("subject", "BR_OBS_002", "Observation must reference a patient"));
        }

        ValidationResult result = errors.isEmpty() ? ValidationResult.success()
            : ValidationResult.failure(errors);
        result.setValidatorName("BUSINESS-RULES-OBSERVATION");
        return result;
    }

    private ValidationResult.ValidationError error(String field, String code, String message) {
        return ValidationResult.ValidationError.builder()
            .field(field)
            .code(code)
            .message(message)
            .severity("ERROR")
            .build();
    }
}
